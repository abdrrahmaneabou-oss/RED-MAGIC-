package com.redmagic.tgktest;

import android.os.Process;

public final class TgkUserService extends ITgkService.Stub {
    public static final int SHELL_UID = 2000;

    static {
        System.loadLibrary("tgk_uinput");
    }

    private volatile String status = "not initialized";

    public TgkUserService() {
        status = "UserService uid=" + Process.myUid();
    }

    @Override
    public int getBackendUid() {
        return Process.myUid();
    }

    @Override
    public int initBackend() {
        if (Process.myUid() != SHELL_UID) {
            status = "Rejected: expected shell uid 2000, got " + Process.myUid();
            return -100;
        }
        int rc = nativeInit();
        status = nativeStatus();
        return rc;
    }

    @Override
    public int tapKey(int linuxKeyCode) {
        if (Process.myUid() != SHELL_UID) {
            status = "Rejected: expected shell uid 2000, got " + Process.myUid();
            return -100;
        }
        int rc = nativeTap(linuxKeyCode);
        status = nativeStatus();
        return rc;
    }

    @Override
    public String getStatus() {
        return status + " | native=" + nativeStatus();
    }

    @Override
    public void destroy() {
        nativeDestroy();
        System.exit(0);
    }

    private static native int nativeInit();
    private static native int nativeTap(int linuxKeyCode);
    private static native void nativeDestroy();
    private static native String nativeStatus();
}
