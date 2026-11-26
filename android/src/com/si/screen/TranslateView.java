package com.si.screen;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

class Line {
    float x, y, w, h;
    String tr = "";
    boolean vertical;
}

public class TranslateView extends View {
    private final List<Line> lines = new ArrayList<>();
    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float pad;

    public TranslateView(Context c) {
        super(c);
        bg.setColor(Color.WHITE);
        fg.setColor(Color.BLACK);
        fg.setTextAlign(Paint.Align.CENTER);
        pad = 2f * c.getResources().getDisplayMetrics().density;
    }

    public void setResult(JSONObject res) {
        lines.clear();
        JSONArray arr = res.optJSONArray("lines");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                String tr = o.optString("tr", "");
                if (tr.isEmpty()) continue;
                Line l = new Line();
                l.x = (float) o.optDouble("x");
                l.y = (float) o.optDouble("y");
                l.w = (float) o.optDouble("w");
                l.h = (float) o.optDouble("h");
                l.tr = tr;
                l.vertical = l.h > l.w * 2.2;
                lines.add(l);
            }
        }
        invalidate();
    }

    public void clear() {
        lines.clear();
        invalidate();
    }

    public int count() {
        return lines.size();
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        int W = getWidth(), H = getHeight();
        int[] loc = new int[2];
        getLocationOnScreen(loc); // 悬浮层可能不在 (0,0)，按实际偏移校正
        for (Line l : lines) {
            RectF r = new RectF(l.x * W - pad - loc[0], l.y * H - pad - loc[1],
                    (l.x + l.w) * W + pad - loc[0], (l.y + l.h) * H + pad - loc[1]);
            c.drawRoundRect(r, pad * 2, pad * 2, bg);
            drawFit(c, l.tr, r);
        }
    }

    private void drawFit(Canvas c, String text, RectF r) {
        float maxW = r.width() - pad * 2;
        float maxH = r.height() - pad * 2;
        if (maxW <= 4 || maxH <= 4) return;
        float fs = Math.max(9f, maxH * 0.82f);
        List<String> out = new ArrayList<>();
        for (int guard = 0; guard < 14 && fs >= 7f; guard++) {
            fg.setTextSize(fs);
            out = wrap(text, maxW);
            float need = out.size() * fs * 1.08f;
            if (need <= maxH) break;
            fs *= 0.9f;
        }
        fg.setTextSize(fs);
        out = wrap(text, maxW);
        float lh = fs * 1.08f;
        float total = out.size() * lh;
        float y = r.top + (r.height() - total) / 2f + fs;
        for (String s : out) {
            c.drawText(s, r.left + r.width() / 2f, y, fg);
            y += lh;
        }
    }

    private List<String> wrap(String text, float maxW) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '\n') {
                out.add(cur.toString());
                cur.setLength(0);
                continue;
            }
            cur.append(ch);
            if (fg.measureText(cur.toString()) > maxW && cur.length() > 1) {
                cur.setLength(cur.length() - 1);
                out.add(cur.toString());
                cur.setLength(0);
                cur.append(ch);
            }
        }
        if (cur.length() > 0) out.add(cur.toString());
        if (out.isEmpty()) out.add("");
        return out;
    }
}
