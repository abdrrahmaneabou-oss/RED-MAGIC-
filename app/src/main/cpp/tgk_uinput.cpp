#include <jni.h>
#include <android/log.h>
#include <linux/input.h>
#include <linux/uinput.h>
#include <fcntl.h>
#include <unistd.h>
#include <sys/ioctl.h>
#include <cerrno>
#include <cstring>
#include <mutex>
#include <string>

#define LOG_TAG "RedMagicTgkTest"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {
std::mutex gMutex;
int gFdF7 = -1;
int gFdF8 = -1;
std::string gStatus = "native not initialized";

void closeDevice(int &fd) {
    if (fd >= 0) {
        ioctl(fd, UI_DEV_DESTROY);
        close(fd);
        fd = -1;
    }
}

void setStatus(const std::string &s) {
    gStatus = s;
    LOGI("%s", s.c_str());
}

int emitEvent(int fd, unsigned short type, unsigned short code, int value) {
    input_event ev{};
    ev.type = type;
    ev.code = code;
    ev.value = value;
    ssize_t n = write(fd, &ev, sizeof(ev));
    if (n != static_cast<ssize_t>(sizeof(ev))) {
        return -errno;
    }
    return 0;
}

int createTgkDevice(const char *name, int keyCode, int &outFd) {
    int fd = open("/dev/uinput", O_WRONLY | O_NONBLOCK | O_CLOEXEC);
    if (fd < 0) return -errno;

    auto fail = [&](int err) {
        close(fd);
        return err;
    };

    if (ioctl(fd, UI_SET_EVBIT, EV_KEY) < 0) return fail(-errno);
    if (ioctl(fd, UI_SET_KEYBIT, keyCode) < 0) return fail(-errno);
    if (ioctl(fd, UI_SET_EVBIT, EV_ABS) < 0) return fail(-errno);
    if (ioctl(fd, UI_SET_ABSBIT, ABS_DISTANCE) < 0) return fail(-errno);

    uinput_user_dev dev{};
    std::strncpy(dev.name, name, UINPUT_MAX_NAME_SIZE - 1);
    dev.id.bustype = BUS_VIRTUAL;
    dev.id.vendor = 0x19d2;   // ZTE/Nubia vendor-style marker for test only.
    dev.id.product = (keyCode == KEY_F7) ? 0x0f07 : 0x0f08;
    dev.id.version = 1;
    dev.absmin[ABS_DISTANCE] = -1;
    dev.absmax[ABS_DISTANCE] = 100;
    dev.absfuzz[ABS_DISTANCE] = 0;
    dev.absflat[ABS_DISTANCE] = 0;

    ssize_t n = write(fd, &dev, sizeof(dev));
    if (n != static_cast<ssize_t>(sizeof(dev))) return fail(-errno);
    if (ioctl(fd, UI_DEV_CREATE) < 0) return fail(-errno);

    outFd = fd;
    return 0;
}

int initLocked() {
    if (gFdF7 >= 0 && gFdF8 >= 0) {
        setStatus("uinput ready: virtual TGK F7 + F8 already created");
        return 0;
    }

    closeDevice(gFdF7);
    closeDevice(gFdF8);

    int rc = createTgkDevice("nubia_tgk_aw_sar0_ch0", KEY_F7, gFdF7);
    if (rc != 0) {
        setStatus("create F7 TGK failed rc=" + std::to_string(rc) + " errno=" + std::to_string(-rc) + " " + std::strerror(-rc));
        return rc;
    }

    rc = createTgkDevice("nubia_tgk_aw_sar1_ch0", KEY_F8, gFdF8);
    if (rc != 0) {
        closeDevice(gFdF7);
        setStatus("create F8 TGK failed rc=" + std::to_string(rc) + " errno=" + std::to_string(-rc) + " " + std::strerror(-rc));
        return rc;
    }

    // Let EventHub/InputReader discover both virtual devices before the first test event.
    usleep(500000);
    setStatus("uinput ready: sar0=KEY_F7, sar1=KEY_F8");
    return 0;
}

int tapLocked(int keyCode) {
    int rc = initLocked();
    if (rc != 0) return rc;

    int fd = -1;
    if (keyCode == KEY_F7) fd = gFdF7;
    else if (keyCode == KEY_F8) fd = gFdF8;
    else {
        setStatus("unsupported key code " + std::to_string(keyCode));
        return -EINVAL;
    }

    // Reproduce the exact sequence captured from the physical shoulder sensors:
    // ABS_DISTANCE=1, KEY_F7/F8 DOWN, SYN_REPORT; then distance=0, key UP, SYN_REPORT.
    if ((rc = emitEvent(fd, EV_ABS, ABS_DISTANCE, 1)) != 0) goto failed;
    if ((rc = emitEvent(fd, EV_KEY, keyCode, 1)) != 0) goto failed;
    if ((rc = emitEvent(fd, EV_SYN, SYN_REPORT, 0)) != 0) goto failed;

    usleep(70000); // deliberately visible test press; optimize only after proving the path.

    if ((rc = emitEvent(fd, EV_ABS, ABS_DISTANCE, 0)) != 0) goto failed;
    if ((rc = emitEvent(fd, EV_KEY, keyCode, 0)) != 0) goto failed;
    if ((rc = emitEvent(fd, EV_SYN, SYN_REPORT, 0)) != 0) goto failed;

    setStatus(std::string("sent physical-style TGK tap via ") +
              (keyCode == KEY_F7 ? "sar0 / KEY_F7" : "sar1 / KEY_F8"));
    return 0;

failed:
    setStatus("event write failed rc=" + std::to_string(rc) + " errno=" + std::to_string(-rc) + " " + std::strerror(-rc));
    return rc;
}
} // namespace

extern "C" JNIEXPORT jint JNICALL
Java_com_redmagic_tgktest_TgkUserService_nativeInit(JNIEnv *, jclass) {
    std::lock_guard<std::mutex> lock(gMutex);
    return initLocked();
}

extern "C" JNIEXPORT jint JNICALL
Java_com_redmagic_tgktest_TgkUserService_nativeTap(JNIEnv *, jclass, jint keyCode) {
    std::lock_guard<std::mutex> lock(gMutex);
    return tapLocked(keyCode);
}

extern "C" JNIEXPORT void JNICALL
Java_com_redmagic_tgktest_TgkUserService_nativeDestroy(JNIEnv *, jclass) {
    std::lock_guard<std::mutex> lock(gMutex);
    closeDevice(gFdF7);
    closeDevice(gFdF8);
    setStatus("uinput devices destroyed");
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_redmagic_tgktest_TgkUserService_nativeStatus(JNIEnv *env, jclass) {
    std::lock_guard<std::mutex> lock(gMutex);
    return env->NewStringUTF(gStatus.c_str());
}
