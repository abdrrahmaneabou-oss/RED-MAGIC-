package com.redmagic.tgktest;

import android.app.Activity;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import rikka.shizuku.Shizuku;

public final class MainActivity extends Activity {
    private static final int REQ_SHIZUKU = 42;
    private static final int KEY_F7 = 65;
    private static final int KEY_F8 = 66;

    private ITgkService remote;
    private TextView status;
    private Button initButton;
    private Button leftButton;
    private Button rightButton;
    private Button f7Button;
    private Button f8Button;

    private Shizuku.UserServiceArgs userServiceArgs;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            remote = ITgkService.Stub.asInterface(service);
            runRemoteInit();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            remote = null;
            setStatus("Shizuku UserService disconnected");
            refreshButtons();
        }
    };

    private final Shizuku.OnBinderReceivedListener binderListener = this::connectOrRequest;
    private final Shizuku.OnRequestPermissionResultListener permissionListener = (requestCode, grantResult) -> {
        if (requestCode != REQ_SHIZUKU) return;
        if (grantResult == PackageManager.PERMISSION_GRANTED) bindUserService();
        else setStatus("Shizuku permission denied");
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        userServiceArgs = new Shizuku.UserServiceArgs(
                new ComponentName(getPackageName(), TgkUserService.class.getName()))
                .processNameSuffix("tgk")
                .daemon(true)
                .tag("redmagic-tgk-uinput-test")
                .version(1);

        setContentView(buildUi());
        Shizuku.addBinderReceivedListenerSticky(binderListener);
        Shizuku.addRequestPermissionResultListener(permissionListener);
        connectOrRequest();
    }

    @Override
    protected void onDestroy() {
        Shizuku.removeBinderReceivedListener(binderListener);
        Shizuku.removeRequestPermissionResultListener(permissionListener);
        super.onDestroy();
    }

    private View buildUi() {
        int pad = dp(20);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        root.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView title = new TextView(this);
        title.setText("RedMagic TGK / uinput test");
        title.setTextSize(24f);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView info = new TextView(this);
        info.setText("Creates two virtual nubia_tgk devices inside a Shizuku shell UserService.\n\nObserved physical mapping:\nR candidate: event3 / KEY_F7\nL candidate: event6 / KEY_F8");
        info.setTextSize(15f);
        LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(-1, -2);
        infoLp.topMargin = dp(12);
        root.addView(info, infoLp);

        initButton = button("Initialize virtual TGK", v -> bindUserService());
        leftButton = button("TEST L  (KEY_F8)", v -> tap(KEY_F8));
        rightButton = button("TEST R  (KEY_F7)", v -> tap(KEY_F7));
        f7Button = button("Raw KEY_F7 test", v -> tap(KEY_F7));
        f8Button = button("Raw KEY_F8 test", v -> tap(KEY_F8));

        root.addView(initButton, buttonLp());
        root.addView(leftButton, buttonLp());
        root.addView(rightButton, buttonLp());
        root.addView(f7Button, buttonLp());
        root.addView(f8Button, buttonLp());

        status = new TextView(this);
        status.setTextSize(14f);
        status.setTextIsSelectable(true);
        status.setText("Waiting for Shizuku...");
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(-1, -2);
        statusLp.topMargin = dp(16);
        root.addView(status, statusLp);

        refreshButtons();

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        return scroll;
    }

    private Button button(String text, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setOnClickListener(listener);
        return b;
    }

    private LinearLayout.LayoutParams buttonLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(12);
        return lp;
    }

    private void connectOrRequest() {
        if (!Shizuku.pingBinder()) {
            setStatus("Start Shizuku using Wireless debugging / ADB.");
            refreshButtons();
            return;
        }
        int uid;
        try {
            uid = Shizuku.getUid();
        } catch (Throwable t) {
            setStatus("Cannot read Shizuku UID: " + t);
            return;
        }
        if (uid != 2000) {
            setStatus("This test intentionally requires Shizuku shell UID 2000. Current UID=" + uid);
            return;
        }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            setStatus("Requesting Shizuku permission...");
            Shizuku.requestPermission(REQ_SHIZUKU);
            return;
        }
        bindUserService();
    }

    private void bindUserService() {
        if (!Shizuku.pingBinder()) {
            setStatus("Shizuku is not running");
            return;
        }
        try {
            setStatus("Binding shell UserService...");
            Shizuku.bindUserService(userServiceArgs, connection);
        } catch (Throwable t) {
            setStatus("bind failed: " + t);
        }
    }

    private void runRemoteInit() {
        new Thread(() -> {
            try {
                int uid = remote.getBackendUid();
                int rc = remote.initBackend();
                String s = remote.getStatus();
                runOnUiThread(() -> {
                    setStatus("backend uid=" + uid + "\ninit rc=" + rc + "\n" + s);
                    refreshButtons();
                });
            } catch (Throwable t) {
                runOnUiThread(() -> setStatus("init exception: " + t));
            }
        }, "tgk-init").start();
    }

    private void tap(int keyCode) {
        ITgkService service = remote;
        if (service == null) {
            setStatus("Backend is not connected");
            return;
        }
        new Thread(() -> {
            try {
                int rc = service.tapKey(keyCode);
                String s = service.getStatus();
                runOnUiThread(() -> setStatus("tap key=" + keyCode + " rc=" + rc + "\n" + s));
            } catch (Throwable t) {
                runOnUiThread(() -> setStatus("tap exception: " + t));
            }
        }, "tgk-tap").start();
    }

    private void refreshButtons() {
        boolean ready = remote != null;
        if (leftButton != null) leftButton.setEnabled(ready);
        if (rightButton != null) rightButton.setEnabled(ready);
        if (f7Button != null) f7Button.setEnabled(ready);
        if (f8Button != null) f8Button.setEnabled(ready);
    }

    private void setStatus(String text) {
        if (status != null) status.setText(text);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
