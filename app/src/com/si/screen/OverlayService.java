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
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import com.si.ocr.OcrEngine;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 悬浮球 + 全屏译文层；识别在本机，翻译走网络。 */
public class OverlayService extends Service {
    private WindowManager wm;
    private View ball;
    private TranslateView tv;
    private MediaProjection mp;
    private String key, model, base;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private WindowManager.LayoutParams ballLp;
    private int downX, downY;
    private float startX, startY;

    @Override
    public void onCreate() {
        super.onCreate();
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        SharedPreferences sp = getSharedPreferences("si", 0);
        key = sp.getString("key", "");
        model = sp.getString("model", "Qwen/Qwen3.5-9B");
        base = sp.getString("base", "https://api.siliconflow.cn/v1");
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
                toast("录屏授权失败");
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
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                    new NotificationChannel(ch, "屏幕翻译", NotificationManager.IMPORTANCE_LOW));
        }
        PendingIntent pi = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class),
                Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, ch) : new Notification.Builder(this);
        b.setContentTitle("屏幕翻译运行中").setContentText("点悬浮球翻译当前画面")
                .setSmallIcon(android.R.drawable.ic_menu_view).setContentIntent(pi).setOngoing(true);
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, b.build(), 32);
        else startForeground(1, b.build());
    }

    private void addBall() {
        if (ball != null) return;
        DisplayMetrics dm = getResources().getDisplayMetrics();
        final int size = (int) (56 * dm.density);
        ball = new View(this) {
            final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            final Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);

            @Override
            protected void onDraw(Canvas c) {
                p.setColor(Color.parseColor("#F01F6FEB"));
                c.drawCircle(getWidth() / 2f, getHeight() / 2f, getWidth() / 2f, p);
                p.setColor(0x33000000);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(getWidth() * 0.03f);
                c.drawCircle(getWidth() / 2f, getHeight() / 2f, getWidth() / 2f - 1, p);
                p.setStyle(Paint.Style.FILL);
                t.setColor(Color.WHITE);
                t.setTextAlign(Paint.Align.CENTER);
                t.setTextSize(getWidth() * 0.44f);
                t.setFakeBoldText(true);
                c.drawText("译", getWidth() / 2f, getHeight() / 2f + getWidth() * 0.155f, t);
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
                    return true;
                case MotionEvent.ACTION_MOVE:
                    ballLp.x = (int) (startX - (e.getRawX() - downX));
                    ballLp.y = (int) (startY + (e.getRawY() - downY));
                    wm.updateViewLayout(ball, ballLp);
                    return true;
                case MotionEvent.ACTION_UP:
                    if (Math.abs(e.getRawX() - downX) <= 12 && Math.abs(e.getRawY() - downY) <= 12) {
                        translate();
                    }
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
        if (key.isEmpty()) {
            toast("先在 App 里填 API Key");
            return;
        }
        toast("识别中…");
        io.execute(() -> {
            try {
                DisplayMetrics dm = new DisplayMetrics();
                wm.getDefaultDisplay().getRealMetrics(dm);
                Bitmap bmp = Capture.shot(mp, dm.widthPixels, dm.heightPixels, dm.densityDpi);
                if (bmp == null) {
                    main.post(() -> toast("抓屏失败"));
                    return;
                }
                OcrRunner ocr = new OcrRunner(new ModelStore(this));
                List<OcrEngine.Line> lines = ocr.run(bmp);
                if (lines.isEmpty()) {
                    main.post(() -> toast("这屏没识别到文字"));
                    bmp.recycle();
                    return;
                }
                List<String> src = new ArrayList<>();
                for (OcrEngine.Line l : lines) src.add(l.text);
                List<String> tr = Translator.translate(src, key, base, model);
                final List<OcrEngine.Line> fl = lines;
                final int w = bmp.getWidth(), h = bmp.getHeight();
                bmp.recycle();
                main.post(() -> show(fl, tr, w, h));
            } catch (Throwable t) {
                String m = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
                main.post(() -> toast("失败：" + m));
            }
        });
    }

    private void show(List<OcrEngine.Line> lines, List<String> tr, int w, int h) {
        if (tv == null) {
            tv = new TranslateView(this);
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                            : WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP | Gravity.LEFT;
            wm.addView(tv, lp);
        }
        tv.show(lines, tr, w, h);
        toast(tv.count() == 0 ? "没有可显示的译文" : "译文 " + tv.count() + " 处 · 再点球关闭");
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
