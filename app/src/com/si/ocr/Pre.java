package com.si.ocr;

/** 预处理：BGR/RGB 归一化成 NCHW float。 */
public class Pre {
    public static float[] nchw(Pix p, float[] mean, float[] std) {
        int w = p.w, h = p.h;
        float[] out = new float[3 * h * w];
        int plane = h * w;
        for (int y = 0; y < h; y++) {
            int row = y * w;
            for (int x = 0; x < w; x++) {
                int c = p.argb[row + x];
                float r = ((c >> 16) & 255) / 255f;
                float g = ((c >> 8) & 255) / 255f;
                float b = (c & 255) / 255f;
                int i = row + x;
                out[i] = (r - mean[0]) / std[0];
                out[plane + i] = (g - mean[1]) / std[1];
                out[2 * plane + i] = (b - mean[2]) / std[2];
            }
        }
        return out;
    }

    /** 识别模型用：(x/255-0.5)/0.5，按 ImageNet 顺序无所谓，单通道就行。 */
    public static float[] rec(Pix p) {
        int w = p.w, h = p.h;
        float[] out = new float[3 * h * w];
        int plane = h * w;
        for (int y = 0; y < h; y++) {
            int row = y * w;
            for (int x = 0; x < w; x++) {
                int c = p.argb[row + x];
                float r = ((c >> 16) & 255) / 255f;
                float g = ((c >> 8) & 255) / 255f;
                float b = (c & 255) / 255f;
                int i = row + x;
                out[i] = (r - 0.5f) / 0.5f;
                out[plane + i] = (g - 0.5f) / 0.5f;
                out[2 * plane + i] = (b - 0.5f) / 0.5f;
            }
        }
        return out;
    }
}
