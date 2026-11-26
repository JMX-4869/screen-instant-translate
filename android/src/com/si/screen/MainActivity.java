package com.si.screen;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int REQ_PROJ = 1001;
    private EditText etHost;
    private TextView tvStatus;
    private SharedPreferences sp;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences("si", 0);
        float d = getResources().getDisplayMetrics().density;
        int p = (int) (18 * d);

        LinearLayout ll = new LinearLayout(this);
        ll.setOrientation(LinearLayout.VERTICAL);
        ll.setPadding(p, p, p, p);
        ll.setBackgroundColor(Color.WHITE);

        TextView title = new TextView(this);
        title.setText("屏幕翻译");
        title.setTextSize(24);
        title.setTextColor(Color.BLACK);
        ll.addView(title);

        TextView hint = new TextView(this);
        hint.setText("\n电脑上先跑 si 服务，手机和电脑连同一个 Wi-Fi。\n填好地址 → 开始 → 屏幕右边出现悬浮球，点一下翻译当前屏。");
        hint.setTextSize(14);
        hint.setTextColor(Color.DKGRAY);
        ll.addView(hint);

        etHost = new EditText(this);
        etHost.setText(sp.getString("host", "http://192.168.1.100:8017"));
        etHost.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        etHost.setTextSize(16);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = (int) (20 * d);
        ll.addView(etHost, lp);

        Button bStart = new Button(this);
        bStart.setText("开始");
        bStart.setTextSize(18);
        bStart.setOnClickListener(v -> start());
        ll.addView(bStart, lp);

        Button bStop = new Button(this);
        bStop.setText("停止");
        bStop.setOnClickListener(v -> {
            stopService(new Intent(this, OverlayService.class));
            tvStatus.setText("已停止");
        });
        ll.addView(bStop);

        tvStatus = new TextView(this);
        tvStatus.setText("\n" + statusText());
        tvStatus.setTextSize(13);
        tvStatus.setTextColor(Color.GRAY);
        ll.addView(tvStatus);

        setContentView(ll);
    }

    private String statusText() {
        boolean ov = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this);
        return "悬浮窗权限：" + (ov ? "已授权" : "未授权");
    }

    private void start() {
        String host = etHost.getText().toString().trim();
        if (!host.startsWith("http")) {
            toast("地址要以 http:// 开头");
            return;
        }
        sp.edit().putString("host", host).apply();

        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            toast("先给悬浮窗权限");
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(i);
            return;
        }
        MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        startActivityForResult(mpm.createScreenCaptureIntent(), REQ_PROJ);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_PROJ) return;
        if (res != RESULT_OK || data == null) {
            toast("没拿到录屏授权");
            return;
        }
        Intent svc = new Intent(this, OverlayService.class);
        svc.setAction("start");
        svc.putExtra("code", res);
        svc.putExtra("data", data);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(svc);
        else startService(svc);
        tvStatus.setText("\n已启动，返回桌面或打开漫画试一下");
        moveTaskToBack(true);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
