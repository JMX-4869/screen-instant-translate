package com.si.screen;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.HashMap;
import java.util.Map;

public class MainActivity extends Activity {
    private static final int BG = 0xFF0B0D11, CARD = 0xFF151A23, LINE = 0xFF232B38,
            TEXT = 0xFFE8EAEF, SUB = 0xFF8B95A5, BLUE = 0xFF2B6EF6, VIOLET = 0xFF7C3AED,
            GREEN = 0xFF34D399, RED = 0xFFF87171;

    private SharedPreferences sp;
    private ModelStore store;
    private LinearLayout list;
    private TextView pillModel, pillApi, foot;
    private final Map<String, Row> rows = new HashMap<>();
    private float d;

    private class Row {
        TextView state;
        ProgressBar bar;
        Button btn;
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences("si", 0);
        store = new ModelStore(this);
        d = getResources().getDisplayMetrics().density;
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad(20), pad(18), pad(20), pad(28));
        sv.addView(root);

        root.addView(header());
        root.addView(pills());
        root.addView(section("识别模型", "下载后离线可用，图片不出手机"));
        list = card();
        root.addView(list);
        rowDet();
        for (Models.M m : Models.ALL) rowModel(m);
        root.addView(section("翻译接口", "只把文字发给接口，走 OpenAI 兼容协议"));
        root.addView(apiCard());
        root.addView(startButton());
        root.addView(stopLink());
        foot = text("首次使用：先下模型 → 填 Key → 点开始 → 授权悬浮窗和录屏", 12, SUB);
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(-1, -2);
        flp.topMargin = pad(16);
        foot.setGravity(Gravity.CENTER);
        root.addView(foot, flp);

        setContentView(sv);
        refresh();
    }

    // ---------- 组件 ----------

    private View header() {
        LinearLayout h = new LinearLayout(this);
        h.setOrientation(LinearLayout.VERTICAL);
        h.setPadding(pad(20), pad(20), pad(20), pad(20));
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{BLUE, VIOLET});
        g.setCornerRadius(dp(20));
        h.setBackground(g);

        TextView t = text("屏幕翻译", 26, Color.WHITE);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        h.addView(t);
        TextView s = text("本地识别 · 译文原位覆盖 · 不打断阅读", 13, 0xFFD6E2FF);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.topMargin = pad(6);
        h.addView(s, lp);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, -2);
        hp.bottomMargin = pad(14);
        h.setLayoutParams(hp);
        return h;
    }

    private View pills() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        pillModel = pill("模型 —");
        pillApi = pill("接口 —");
        r.addView(pillModel);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.leftMargin = pad(8);
        r.addView(pillApi, lp);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
        rp.bottomMargin = pad(4);
        r.setLayoutParams(rp);
        return r;
    }

    private TextView pill(String s) {
        TextView t = text(s, 12, SUB);
        GradientDrawable g = new GradientDrawable();
        g.setColor(CARD);
        g.setCornerRadius(dp(20));
        g.setStroke(pad(1), LINE);
        t.setBackground(g);
        t.setPadding(pad(12), pad(6), pad(12), pad(6));
        return t;
    }

    private LinearLayout section(String title, String sub) {
        LinearLayout w = new LinearLayout(this);
        w.setOrientation(LinearLayout.VERTICAL);
        TextView t = text(title, 15, TEXT);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        w.addView(t);
        w.addView(text(sub, 12, SUB));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = pad(22);
        lp.bottomMargin = pad(10);
        w.setLayoutParams(lp);
        return w;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(pad(14), pad(6), pad(14), pad(6));
        GradientDrawable g = new GradientDrawable();
        g.setColor(CARD);
        g.setCornerRadius(dp(16));
        g.setStroke(pad(1), LINE);
        c.setBackground(g);
        return c;
    }

    private void addRow(LinearLayout parent, Models.M m, String title, String sub) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, pad(10), 0, pad(10));

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.addView(text(title, 15, TEXT));
        TextView st = text(sub, 12, SUB);
        left.addView(st);
        row.addView(left, new LinearLayout.LayoutParams(0, -2, 1));

        Row r = new Row();
        r.state = text("", 12, SUB);
        row.addView(r.state);

        r.btn = new Button(this);
        r.btn.setAllCaps(false);
        r.btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        r.btn.setTextColor(Color.WHITE);
        GradientDrawable g = new GradientDrawable();
        g.setColor(BLUE);
        g.setCornerRadius(dp(10));
        r.btn.setBackground(g);
        r.btn.setPadding(pad(12), 0, pad(12), 0);
        r.btn.setMinWidth(0);
        r.btn.setMinimumWidth(0);
        r.btn.setMinHeight(0);
        r.btn.setMinimumHeight(pad(34));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-2, pad(34));
        blp.leftMargin = pad(8);
        row.addView(r.btn, blp);

        r.bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        r.bar.setMax(100);
        r.bar.setVisibility(View.GONE);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(pad(72), pad(4));
        plp.leftMargin = pad(8);
        row.addView(r.bar, plp);

        parent.addView(row);
        rows.put(m.id, r);
        r.btn.setOnClickListener(v -> download(m));
    }

    private void rowDet() {
        addRow(list, Models.DET, "文字检测", Models.mb(Models.DET.size()) + " · 所有语种共用");
    }

    private void rowModel(Models.M m) {
        addRow(list, m, m.name, Models.mb(m.size()) + " · " + m.note);
    }

    private View apiCard() {
        LinearLayout c = card();
        c.setPadding(pad(14), pad(14), pad(14), pad(14));

        EditText key = input("API Key（硅基流动）", true);
        key.setText(sp.getString("key", ""));
        c.addView(key);

        EditText model = input("模型名，如 Qwen/Qwen3.5-9B", false);
        model.setText(sp.getString("model", "Qwen/Qwen3.5-9B"));
        c.addView(model);

        Button save = new Button(this);
        save.setAllCaps(false);
        save.setText("保存");
        save.setTextColor(Color.WHITE);
        save.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        GradientDrawable g = new GradientDrawable();
        g.setColor(0xFF1F2733);
        g.setCornerRadius(dp(10));
        save.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(pad(96), pad(38));
        lp.topMargin = pad(4);
        c.addView(save, lp);
        save.setOnClickListener(v -> {
            sp.edit().putString("key", key.getText().toString().trim())
                    .putString("model", model.getText().toString().trim()).apply();
            toast("已保存");
            refresh();
        });
        return c;
    }

    private View startButton() {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText("开始悬浮翻译");
        b.setTextColor(Color.WHITE);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{BLUE, VIOLET});
        g.setCornerRadius(dp(16));
        b.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(54));
        lp.topMargin = pad(26);
        b.setLayoutParams(lp);
        b.setOnClickListener(v -> start());
        return b;
    }

    private View stopLink() {
        TextView t = text("停止悬浮球", 13, SUB);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, pad(14), 0, 0);
        t.setOnClickListener(v -> {
            stopService(new Intent(this, OverlayService.class));
            toast("已停止");
        });
        return t;
    }

    // ---------- 逻辑 ----------

    private void download(Models.M m) {
        Row r = rows.get(m.id);
        if (r == null) return;
        r.btn.setEnabled(false);
        r.btn.setText("…");
        r.bar.setVisibility(View.VISIBLE);
        r.bar.setProgress(0);
        new Thread(() -> {
            store.download(m, new ModelStore.Progress() {
                @Override
                public void on(String id, String file, long got, long total) {
                    int pc = total <= 0 ? 0 : (int) (got * 100 / total);
                    runOnUiThread(() -> {
                        r.bar.setProgress(pc);
                        r.state.setText(pc + "%");
                    });
                }

                @Override
                public void done(String id, boolean ok, String err) {
                    runOnUiThread(() -> {
                        r.bar.setVisibility(View.GONE);
                        r.btn.setEnabled(true);
                        if (!ok) toast("下载失败：" + err);
                        refresh();
                    });
                }
            });
        }).start();
    }

    private void refresh() {
        int n = store.installedCount();
        int total = 1 + Models.ALL.size();
        pillModel.setText("模型 " + n + "/" + total);
        boolean hasKey = !sp.getString("key", "").isEmpty();
        pillApi.setText("接口 " + (hasKey ? "已配置" : "未配置"));
        pillApi.setTextColor(hasKey ? GREEN : RED);

        setRow(Models.DET);
        for (Models.M m : Models.ALL) setRow(m);
        foot.setText("模型占 " + Models.mb(store.usedBytes()) + " · 识别在本机完成，只把文字发给接口");
    }

    private void setRow(Models.M m) {
        Row r = rows.get(m.id);
        if (r == null) return;
        if (store.installed(m)) {
            r.btn.setVisibility(View.GONE);
            r.state.setText("✓ 已装");
            r.state.setTextColor(GREEN);
        } else {
            r.btn.setVisibility(View.VISIBLE);
            r.btn.setText("下载 " + Models.mb(m.size()));
            r.state.setText("");
            r.state.setTextColor(SUB);
        }
    }

    private void start() {
        String key = sp.getString("key", "");
        if (key.isEmpty()) {
            toast("先填 API Key");
            return;
        }
        if (!store.installed(Models.DET)) {
            toast("先下「文字检测」模型");
            return;
        }
        boolean anyRec = false;
        for (Models.M m : Models.ALL) if (store.installed(m)) anyRec = true;
        if (!anyRec) {
            toast("至少装一个识别模型");
            return;
        }
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            toast("请给悬浮窗权限");
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
            return;
        }
        MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        startActivityForResult(mpm.createScreenCaptureIntent(), 1001);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != 1001) return;
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
        toast("已启动，返回桌面点悬浮球");
        moveTaskToBack(true);
    }

    // ---------- 小工具 ----------

    private int dp(float v) {
        return (int) (v * d + 0.5f);
    }

    private int pad(int v) {
        return dp(v);
    }

    private TextView text(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        return t;
    }

    private EditText input(String hint, boolean pwd) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(0xFF5C6675);
        e.setTextColor(TEXT);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        e.setSingleLine(true);
        if (pwd) e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        GradientDrawable g = new GradientDrawable();
        g.setColor(BG);
        g.setCornerRadius(dp(10));
        g.setStroke(pad(1), LINE);
        e.setBackground(g);
        e.setPadding(pad(12), pad(10), pad(12), pad(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = pad(10);
        e.setLayoutParams(lp);
        return e;
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
