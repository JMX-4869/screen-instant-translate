package com.si.ocr;

/** 平台无关的位图：ARGB int 数组，行优先。 */
public class Pix {
    public final int w, h;
    public final int[] argb;

    public Pix(int w, int h, int[] argb) {
        this.w = w;
        this.h = h;
        this.argb = argb;
    }

    public static Pix fromArgb(int w, int h, int[] argb) {
        return new Pix(w, h, argb);
    }

    /** 双线性缩放到指定尺寸。 */
    public Pix resize(int nw, int nh) {
        if (nw == w && nh == h) return this;
        int[] out = new int[nw * nh];
        float sx = (float) w / nw, sy = (float) h / nh;
        for (int y = 0; y < nh; y++) {
            float fy = (y + 0.5f) * sy - 0.5f;
            int y0 = (int) Math.floor(fy);
            float wy = fy - y0;
            int y1 = Math.min(h - 1, Math.max(0, y0 + 1));
            y0 = Math.min(h - 1, Math.max(0, y0));
            for (int x = 0; x < nw; x++) {
                float fx = (x + 0.5f) * sx - 0.5f;
                int x0 = (int) Math.floor(fx);
                float wx = fx - x0;
                int x1 = Math.min(w - 1, Math.max(0, x0 + 1));
                x0 = Math.min(w - 1, Math.max(0, x0));
                out[y * nw + x] = bilinear(
                        argb[y0 * w + x0], argb[y0 * w + x1],
                        argb[y1 * w + x0], argb[y1 * w + x1], wx, wy);
            }
        }
        return new Pix(nw, nh, out);
    }

    private static int bilinear(int a, int b, int c, int d, float wx, float wy) {
        int a0 = (a >>> 24) & 255, r0 = (a >> 16) & 255, g0 = (a >> 8) & 255, b0 = a & 255;
        int a1 = (b >>> 24) & 255, r1 = (b >> 16) & 255, g1 = (b >> 8) & 255, b1 = b & 255;
        int a2 = (c >>> 24) & 255, r2 = (c >> 16) & 255, g2 = (c >> 8) & 255, b2 = c & 255;
        int a3 = (d >>> 24) & 255, r3 = (d >> 16) & 255, g3 = (d >> 8) & 255, b3 = d & 255;
        float w00 = (1 - wx) * (1 - wy), w10 = wx * (1 - wy), w01 = (1 - wx) * wy, w11 = wx * wy;
        int a4 = (int) (a0 * w00 + a1 * w10 + a2 * w01 + a3 * w11);
        int r4 = (int) (r0 * w00 + r1 * w10 + r2 * w01 + r3 * w11);
        int g4 = (int) (g0 * w00 + g1 * w10 + g2 * w01 + g3 * w11);
        int b4 = (int) (b0 * w00 + b1 * w10 + b2 * w01 + b3 * w11);
        return (a4 << 24) | (r4 << 16) | (g4 << 8) | b4;
    }

    /** 轴对齐裁剪（含边界保护）。 */
    public Pix crop(int x0, int y0, int x1, int y1) {
        x0 = clamp(x0, 0, w - 1);
        y0 = clamp(y0, 0, h - 1);
        x1 = clamp(x1, x0 + 1, w);
        y1 = clamp(y1, y0 + 1, h);
        int cw = x1 - x0, ch = y1 - y0;
        int[] out = new int[cw * ch];
        for (int y = 0; y < ch; y++) {
            System.arraycopy(argb, (y0 + y) * w + x0, out, y * cw, cw);
        }
        return new Pix(cw, ch, out);
    }

    /** 顺时针转 90°（竖排文字转横排）。 */
    public Pix rot90() {
        int[] out = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                out[x * h + (h - 1 - y)] = argb[y * w + x];
            }
        }
        return new Pix(h, w, out);
    }

    static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
