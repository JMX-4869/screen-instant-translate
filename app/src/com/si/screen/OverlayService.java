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
import android.graphics.drawable.GradientDrawable;
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
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
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
    private ScreenMirror mirror;
    private String key, model, base;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private WindowManager.LayoutParams ballLp;
    private View hud;
    private TextView hudText;
    private WindowManager.LayoutParams hudLp;
    private boolean busy;
    private int downX, downY;
    private float startX, startY;

    @Override
    public void onCreate() {
        super.onCreate();
        Logs.install(this);
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
            try {
                DisplayMetrics dm = new DisplayMetrics();
                wm.getDefaultDisplay().getRealMetrics(dm);
                mirror = new ScreenMirror();
                mirror.start(mp, dm.widthPixels, dm.heightPixels, dm.densityDpi, main);
            } catch (Throwable t) {
                toast("屏幕镜像启动失败");
            }
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
                    main.postDelayed(openApp, 700);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (Math.abs(e.getRawX() - downX) > 12 || Math.abs(e.getRawY() - downY) > 12) {
                        main.removeCallbacks(openApp);
                    }
                    ballLp.x = (int) (startX - (e.getRawX() - downX));
                    ballLp.y = (int) (startY + (e.getRawY() - downY));
                    wm.updateViewLayout(ball, ballLp);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    main.removeCallbacks(openApp);
                    if (e.getActionMasked() == MotionEvent.ACTION_UP
                            && Math.abs(e.getRawX() - downX) <= 12
                            && Math.abs(e.getRawY() - downY) <= 12) {
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
        if (busy) return;
        if (key.isEmpty()) {
            fail("还没填 API Key，回 App 设置里粘贴");
            return;
        }
        if (!new ModelStore(this).installed(Models.DET)) {
            fail("还没下「文字检测」模型");
            return;
        }
        busy = true;
        showHud("正在识别文字…", false);
        io.execute(() -> {
            long t0 = System.currentTimeMillis();
            try {
                Bitmap bmp = mirror == null ? null : mirror.grab(650);
                if (bmp == null) {
                    main.post(() -> fail("抓屏失败，重开一次录屏授权"));
                    return;
                }
                OcrRunner ocr = new OcrRunner(new ModelStore(this));
                List<OcrEngine.Line> lines = ocr.run(bmp);
                long t1 = System.currentTimeMillis();
                Logs.w(this, "ocr " + (t1 - t0) + "ms lines=" + lines.size());
                if (lines.isEmpty()) {
                    bmp.recycle();
                    main.post(() -> fail("这屏没识别到文字"));
                    return;
                }
                main.post(() -> hudSay("正在翻译 " + lines.size() + " 行…"));
                List<String> src = new ArrayList<>();
                for (OcrEngine.Line l : lines) src.add(l.text);
                List<String> tr = Translator.translate(src, key, base, model);
                long t2 = System.currentTimeMillis();
                int nonEmpty = 0;
                for (String s : tr) if (s != null && !s.isEmpty()) nonEmpty++;
                Logs.w(this, "mt " + (t2 - t1) + "ms got=" + nonEmpty + "/" + tr.size()
                        + " model=" + model);
                if (nonEmpty == 0) {
                    bmp.recycle();
                    main.post(() -> fail("翻译返回空（Key/模型名/额度？）"));
                    return;
                }
                final List<OcrEngine.Line> fl = lines;
                final int w = bmp.getWidth(), h = bmp.getHeight();
                bmp.recycle();
                main.post(() -> {
                    try {
                        show(fl, tr, w, h);
                    } catch (Throwable t) {
                        fail("显示失败：" + msg(t));
                    }
                });
            } catch (Throwable t) {
                String m = msg(t);
                Logs.w(this, "ERR " + m);
                main.post(() -> fail(m));
            }
        });
    }

    private static String msg(Throwable t) {
        String m = t.getMessage();
        if (m == null) m = t.getClass().getSimpleName();
        if (m.length() > 90) m = m.substring(0, 90) + "…";
        return m;
    }

    /** 出错统一用胶囊提示（很多机型会吞掉后台 Toast）。 */
    private void fail(String s) {
        busy = false;
        Logs.w(this, "FAIL " + s);
        showHud(s, true);
        main.removeCallbacks(hideLater);
        main.postDelayed(hideLater, 4000);
    }

    private final Runnable hideLater = () -> hideHud();

    /** 顶部小胶囊：正在识别 / 正在翻译。点球先出这个，保证有明显反馈。 */
    private final Runnable openApp = () -> {
        try {
            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Throwable ignored) {
        }
    };

    private void showHud(String s, boolean err) {
        float d = getResources().getDisplayMetrics().density;
        if (hud == null) {
            LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.HORIZONTAL);
            box.setGravity(Gravity.CENTER_VERTICAL);
            GradientDrawable g = new GradientDrawable();
            g.setColor(0xF0141A24);
            g.setCornerRadius(20 * d);
            box.setBackground(g);
            int p = (int) (14 * d);
            box.setPadding(p, (int) (7 * d), (int) (16 * d), (int) (7 * d));
            ProgressBar pb = new ProgressBar(this);
            pb.setIndeterminate(true);
            try {
                pb.getIndeterminateDrawable().setTint(Color.WHITE);
            } catch (Throwable ignored) {
            }
            box.addView(pb, new LinearLayout.LayoutParams((int) (16 * d), (int) (16 * d)));
            hudText = new TextView(this);
            hudText.setTextColor(Color.WHITE);
            hudText.setTextSize(13);
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-2, -2);
            tp.leftMargin = (int) (10 * d);
            box.addView(hudText, tp);
            hud = box;
            hudLp = new WindowManager.LayoutParams(-2, -2,
                    Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                            : WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
            hudLp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            hudLp.y = (int) (72 * d);
        }
        try {
            GradientDrawable bg = (GradientDrawable) hud.getBackground();
            bg.setColor(err ? 0xF02A1218 : 0xF0141A24);
        } catch (Throwable ignored) {
        }
        hudText.setText(s);
        if (hud.getParent() == null) {
            try {
                wm.addView(hud, hudLp);
            } catch (Throwable ignored) {
            }
        }
    }

    private void hudSay(String s) {
        if (hudText != null) hudText.setText(s);
    }

    private void hideHud() {
        busy = false;
        if (hud != null && hud.getParent() != null) {
            try {
                wm.removeView(hud);
            } catch (Throwable ignored) {
            }
        }
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
        if (tv.count() == 0) {
            fail("译文都为空，检查模型语言");
        } else {
            hideHud();
            showHud("译文 " + tv.count() + " 处 · 再点球关闭", false);
            main.removeCallbacks(hideLater);
            main.postDelayed(hideLater, 1600);
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
        if (mirror != null) {
            mirror.stop();
            mirror = null;
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
