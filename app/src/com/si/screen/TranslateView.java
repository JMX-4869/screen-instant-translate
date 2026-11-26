package com.si.screen;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import com.si.ocr.OcrEngine;

import java.util.ArrayList;
import java.util.List;

/** 全屏译文层：白底圆角盖住原文，字放不下自动缩。 */
public class TranslateView extends View {
    private static class Box {
        float x, y, w, h;
        String text;
        boolean vertical;
    }

    private final List<Box> boxes = new ArrayList<>();
    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float pad;

    public TranslateView(Context c) {
        super(c);
        float d = c.getResources().getDisplayMetrics().density;
        pad = 2f * d;
        bg.setColor(Color.WHITE);
        fg.setColor(Color.rgb(17, 17, 17));
        fg.setTextAlign(Paint.Align.CENTER);
        shadow.setColor(0x33000000);
    }

    public void show(List<OcrEngine.Line> lines, List<String> trans, int imgW, int imgH) {
        boxes.clear();
        for (int i = 0; i < lines.size(); i++) {
            String tr = i < trans.size() ? trans.get(i) : "";
            if (tr.isEmpty()) continue;
            OcrEngine.Line l = lines.get(i);
            Box b = new Box();
            b.x = l.x0 / imgW;
            b.y = l.y0 / imgH;
            b.w = (l.x1 - l.x0) / imgW;
            b.h = (l.y1 - l.y0) / imgH;
            b.text = tr;
            b.vertical = l.vertical;
            boxes.add(b);
        }
        invalidate();
    }

    public int count() {
        return boxes.size();
    }

    public void clear() {
        boxes.clear();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        int W = getWidth(), H = getHeight();
        int[] loc = new int[2];
        getLocationOnScreen(loc);
        for (Box b : boxes) {
            RectF r = new RectF(b.x * W - pad - loc[0], b.y * H - pad - loc[1],
                    (b.x + b.w) * W + pad - loc[0], (b.y + b.h) * H + pad - loc[1]);
            RectF sh = new RectF(r);
            sh.offset(0, pad);
            c.drawRoundRect(sh, pad * 3, pad * 3, shadow);
            c.drawRoundRect(r, pad * 3, pad * 3, bg);
            drawFit(c, b.text, r);
        }
    }

    private void drawFit(Canvas c, String text, RectF r) {
        float maxW = r.width() - pad * 2;
        float maxH = r.height() - pad * 2;
        if (maxW <= 4 || maxH <= 4) return;
        float fs = Math.max(9f, maxH * 0.85f);
        List<String> out = new ArrayList<>();
        for (int guard = 0; guard < 14 && fs >= 7f; guard++) {
            fg.setTextSize(fs);
            out = wrap(text, maxW);
            if (out.size() * fs * 1.08f <= maxH) break;
            fs *= 0.9f;
        }
        fg.setTextSize(fs);
        out = wrap(text, maxW);
        float lh = fs * 1.08f;
        float y = r.top + (r.height() - out.size() * lh) / 2f + fs;
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
