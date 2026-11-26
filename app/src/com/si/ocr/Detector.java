package com.si.ocr;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Collections;
import java.util.Map;

/** DB 文本检测：输出轴对齐框（图像坐标）。 */
public class Detector {
    private final OrtEnvironment env;
    private final OrtSession sess;
    private final String inName;
    private final float[] MEAN = {0.485f, 0.456f, 0.406f};
    private final float[] STD = {0.229f, 0.224f, 0.225f};
    public final int maxSide;
    public final float thresh;

    public Detector(OrtEnvironment env, byte[] model, int threads, int maxSide, float thresh) throws Exception {
        OrtSession.SessionOptions o = new OrtSession.SessionOptions();
        o.setIntraOpNumThreads(threads);
        this.env = env;
        this.sess = env.createSession(model, o);
        this.inName = sess.getInputNames().iterator().next();
        this.maxSide = maxSide;
        this.thresh = thresh;
    }

    public List<float[]> detect(Pix src) throws Exception {
        int w = src.w, h = src.h;
        float scale = Math.min(1f, maxSide / (float) Math.max(w, h));
        int nw = Math.max(32, Math.round(w * scale / 32f) * 32);
        int nh = Math.max(32, Math.round(h * scale / 32f) * 32);
        Pix rs = src.resize(nw, nh);
        float[] data = Pre.nchw(rs, MEAN, STD);
        float[] prob;
        int pw, ph;
        try (OnnxTensor t = OnnxTensor.createTensor(env,
                FloatBuffer.wrap(data), new long[]{1, 3, nh, nw});
             OrtSession.Result r = sess.run(java.util.Collections.singletonMap(inName, t))) {
            float[][][][] out = (float[][][][]) r.get(0).getValue();
            ph = out[0][0].length;
            pw = out[0][0][0].length;
            prob = new float[ph * pw];
            for (int y = 0; y < ph; y++) {
                System.arraycopy(out[0][0][y], 0, prob, y * pw, pw);
            }
        }

        List<float[]> boxes = new ArrayList<>();
        boolean[] vis = new boolean[pw * ph];
        int[] stack = new int[pw * ph];
        float sx = w / (float) nw, sy = h / (float) nh;
        for (int y = 0; y < ph; y++) {
            for (int x = 0; x < pw; x++) {
                int idx = y * pw + x;
                if (vis[idx] || prob[idx] <= thresh) continue;
                int sp = 0, count = 0;
                stack[sp++] = idx;
                vis[idx] = true;
                int x0 = x, x1 = x, y0 = y, y1 = y;      // 连通域整体
                int cx0 = x, cx1 = x, cy0 = y, cy1 = y;  // 高置信核心
                double sum = 0;
                while (sp > 0) {
                    int cur = stack[--sp];
                    int ccx = cur % pw, ccy = cur / pw;
                    count++;
                    sum += prob[cur];
                    if (ccx < x0) x0 = ccx;
                    if (ccx > x1) x1 = ccx;
                    if (ccy < y0) y0 = ccy;
                    if (ccy > y1) y1 = ccy;
                    if (prob[cur] > 0.55f) {
                        if (ccx < cx0) cx0 = ccx;
                        if (ccx > cx1) cx1 = ccx;
                        if (ccy < cy0) cy0 = ccy;
                        if (ccy > cy1) cy1 = ccy;
                    }
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            if (dx == 0 && dy == 0) continue;
                            int nx = ccx + dx, ny = ccy + dy;
                            if (nx < 0 || ny < 0 || nx >= pw || ny >= ph) continue;
                            int ni = ny * pw + nx;
                            if (!vis[ni] && prob[ni] > thresh) {
                                vis[ni] = true;
                                stack[sp++] = ni;
                            }
                        }
                    }
                }
                if (cx1 < cx0) {
                    cx0 = x0;
                    cx1 = x1;
                    cy0 = y0;
                    cy1 = y1;
                }
                int bw = cx1 - cx0 + 1, bh = cy1 - cy0 + 1;
                if (count < 8 || bw < 3 || bh < 3) continue;
                if (sum / count < 0.5) continue;
                // DB 输出是文字骨架，用 unclip 公式向外扩回字形（宽高按面积/周长推）
                float peri = 2f * (bw + bh);
                float d = peri <= 0 ? 2f : 1.5f * (bw * bh) / peri;
                d = Math.min(d, Math.max(bw, bh) * 0.5f);
                float bx0 = (cx0 - d) * sx, by0 = (cy0 - d) * sy;
                float bx1 = (cx1 + 1 + d) * sx, by1 = (cy1 + 1 + d) * sy;
                boxes.add(new float[]{Math.max(0, bx0), Math.max(0, by0),
                        Math.min(w, bx1), Math.min(h, by1)});
            }
        }
        boxes.sort((a, b) -> {
            float ay = (a[1] + a[3]) / 2, by = (b[1] + b[3]) / 2;
            if (Math.abs(ay - by) > 8) return Float.compare(ay, by);
            return Float.compare(a[0], b[0]);
        });
        return boxes;
    }

    static String fmt(float[] b) {
        return Arrays.toString(b);
    }
}
