package com.si.screen;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class OverlayService extends Service {
    private WindowManager wm;
    private View ball;
    private TranslateView tv;
    private MediaProjection mp;
    private String host;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private WindowManager.LayoutParams ballLp;
    private WindowManager.LayoutParams tvLp;
    private int downX, downY;
    private long downT;
    private float startX, startY;

    @Override
    public void onCreate() {
        super.onCreate();
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        SharedPreferences sp = getSharedPreferences("si", 0);
        host = sp.getString("host", "http://127.0.0.1:8017");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "start".equals(intent.getAction())) {
            int code = intent.getIntExtra("code", 0);
            Intent data = (Intent) intent.getParcelableExtra("data");
            startFg();
            MediaProjectionManager mpm =
                    (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            try {
                mp = mpm.getMediaProjection(code, data);
            } catch (Throwable t) {
                toast("录屏授权失败：" + t.getMessage());
                stopSelf();
                return START_NOT_STICKY;
            }
            if (mp != null) {
                mp.registerCallback(new MediaProjection.Callback() {
                    @Override
                    public void onStop() {
                        main.post(() -> stopSelf());
                    }
                }, main);
            }
            addBall();
        }
        return START_STICKY;
    }

    private void startFg() {
        String ch = "si";
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            NotificationChannel c = new NotificationChannel(ch, "屏幕翻译", NotificationManager.IMPORTANCE_LOW);
            nm.createNotificationChannel(c);
        }
        PendingIntent pi = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class),
                Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, ch) : new Notification.Builder(this);
        b.setContentTitle("屏幕翻译正在运行")
                .setContentText("点悬浮球翻译当前画面")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setContentIntent(pi)
                .setOngoing(true);
        Notification n = b.build();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, n, ServiceInfo());
        } else {
            startForeground(1, n);
        }
    }

    private int ServiceInfo() {
        return 0x00000020; // FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
    }

    private void addBall() {
        if (ball != null) return;
        DisplayMetrics dm = getResources().getDisplayMetrics();
        final int size = (int) (52 * dm.density);

        ball = new View(this) {
            final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            final Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);

            @Override
            protected void onDraw(Canvas c) {
                p.setColor(Color.parseColor("#EE1F6FEB"));
                c.drawCircle(getWidth() / 2f, getHeight() / 2f, getWidth() / 2f, p);
                t.setColor(Color.WHITE);
                t.setTextAlign(Paint.Align.CENTER);
                t.setTextSize(getWidth() * 0.42f);
                t.setFakeBoldText(true);
                c.drawText("译", getWidth() / 2f, getHeight() / 2f + getWidth() * 0.15f, t);
            }
        };

        ballLp = new WindowManager.LayoutParams(size, size,
                Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        ballLp.gravity = Gravity.RIGHT | Gravity.CENTER_VERTICAL;
        ballLp.x = (int) (10 * dm.density);

        ball.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = (int) e.getRawX();
                    downY = (int) e.getRawY();
                    startX = ballLp.x;
                    startY = ballLp.y;
                    downT = System.currentTimeMillis();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    ballLp.x = (int) (startX - (e.getRawX() - downX));
                    ballLp.y = (int) (startY + (e.getRawY() - downY));
                    wm.updateViewLayout(ball, ballLp);
                    return true;
                case MotionEvent.ACTION_UP:
                    boolean moved = Math.abs(e.getRawX() - downX) > 12 || Math.abs(e.getRawY() - downY) > 12;
                    if (!moved) translate();
                    return true;
            }
            return false;
        });
        wm.addView(ball, ballLp);
    }

    private void translate() {
        if (tv != null && tv.count() > 0) {
            removeOverlay();
            return;
        }
        toast("识别中…");
        io.execute(() -> {
            try {
                DisplayMetrics dm = new DisplayMetrics();
                wm.getDefaultDisplay().getRealMetrics(dm);
                Bitmap bmp = Capture.shot(mp, dm.widthPixels, dm.heightPixels, dm.densityDpi);
                if (bmp == null) {
                    main.post(() -> toast("抓屏失败，重开一次授权试试"));
                    return;
                }
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                bmp.compress(Bitmap.CompressFormat.JPEG, 82, bo);
                bmp.recycle();
                String b64 = Base64.encodeToString(bo.toByteArray(), Base64.NO_WRAP);
                String body = "{\"items\":[{\"id\":\"a\",\"data\":\"data:image/jpeg;base64," + b64 + "\"}]}";
                String resp = Net.postJson(host + "/api/translate", body, 90);
                JSONObject o = new JSONObject(resp).getJSONObject("results").getJSONObject("a");
                if (o.has("error")) {
                    main.post(() -> toast("服务报错：" + o.optString("error")));
                    return;
                }
                if (o.optBoolean("need_key")) {
                    main.post(() -> toast("先去网页填 API Key"));
                    return;
                }
                main.post(() -> showResult(o, dm));
            } catch (Throwable t) {
                main.post(() -> toast("失败：" + t.getMessage()));
            }
        });
    }

    private void showResult(JSONObject o, DisplayMetrics dm) {
        if (tv == null) {
            tv = new TranslateView(this);
            tvLp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                            : WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT);
            tvLp.gravity = Gravity.TOP | Gravity.LEFT;
            wm.addView(tv, tvLp);
        }
        tv.setResult(o);
        if (tv.count() == 0) {
            toast("这屏没识别到文字");
        } else {
            toast("译文 " + tv.count() + " 处 · 再点球关闭");
        }
    }

    private void removeOverlay() {
        if (tv == null) return;
        try {
            wm.removeView(tv);
        } catch (Throwable ignored) {
        }
        tv = null;
    }

    private void toast(String s) {
        Toast.makeText(getApplicationContext(), s, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDestroy() {
        removeOverlay();
        if (ball != null) {
            try {
                wm.removeView(ball);
            } catch (Throwable ignored) {
            }
            ball = null;
        }
        if (mp != null) {
            try {
                mp.stop();
            } catch (Throwable ignored) {
            }
            mp = null;
        }
        io.shutdownNow();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
