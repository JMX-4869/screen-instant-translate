package com.si.ocr;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;

import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.Map;

/** CTC 识别：单行裁剪图 → 文字 + 置信度。 */
public class Recognizer {
    private final OrtEnvironment env;
    private final OrtSession sess;
    private final String inName;
    private final List<String> dict = new ArrayList<>();
    public final String lang;

    public Recognizer(OrtEnvironment env, String lang, byte[] model, byte[] dictBytes, int threads) throws Exception {
        this.lang = lang;
        OrtSession.SessionOptions o = new OrtSession.SessionOptions();
        o.setIntraOpNumThreads(threads);
        this.env = env;
        this.sess = env.createSession(model, o);
        this.inName = sess.getInputNames().iterator().next();
        String txt = new String(dictBytes, StandardCharsets.UTF_8);
        for (String line : txt.split("\n", -1)) {
            String s = line;
            if (s.endsWith("\r")) s = s.substring(0, s.length() - 1);
            if (s.isEmpty() && !txt.contains("\n\n")) continue;
            dict.add(s);
        }
        // 有些词表最后一行是空行，去掉末尾多余的
        while (!dict.isEmpty() && dict.get(dict.size() - 1).isEmpty()) dict.remove(dict.size() - 1);
    }

    public static Recognizer load(OrtEnvironment env, String lang, String modelPath, String dictPath, int threads) throws Exception {
        return new Recognizer(env, lang,
                Files.readAllBytes(Paths.get(modelPath)),
                Files.readAllBytes(Paths.get(dictPath)), threads);
    }

    /** 返回 {文本, 平均置信度}。 */
    public Object[] run(Pix crop) throws Exception {
        int h = 48;
        float ratio = crop.w / (float) Math.max(1, crop.h);
        int rw = (int) Math.round(h * ratio);
        rw = Math.max(16, Math.min(640, rw));
        rw = Math.round(rw / 8f) * 8;
        Pix rs = crop.resize(rw, h);
        float[] data = Pre.rec(rs);
        float[][][] out;
        try (OnnxTensor t = OnnxTensor.createTensor(env,
                FloatBuffer.wrap(data), new long[]{1, 3, h, rw});
             OrtSession.Result r = sess.run(java.util.Collections.singletonMap(inName, t))) {
            out = (float[][][]) r.get(0).getValue();
        }
        int T = out[0].length, C = out[0][0].length;
        StringBuilder sb = new StringBuilder();
        double scoreSum = 0;
        int scoreN = 0;
        int prev = -1;
        for (int t = 0; t < T; t++) {
            int best = 0;
            float bv = out[0][t][0];
            for (int c = 1; c < C; c++) {
                float v = out[0][t][c];
                if (v > bv) {
                    bv = v;
                    best = c;
                }
            }
            if (best != 0 && best != prev) {
                String ch = idxChar(best);
                if (ch != null) {
                    sb.append(ch);
                    scoreSum += bv;
                    scoreN++;
                }
            }
            prev = best;
        }
        return new Object[]{sb.toString(), scoreN == 0 ? 0f : (float) (scoreSum / scoreN)};
    }

    private String idxChar(int idx) {
        // 0 = blank, 1..N = dict, N+1 = 空格
        if (idx == 0) return null;
        if (idx <= dict.size()) return dict.get(idx - 1);
        return " ";
    }

    private static float unusedSoften(float[] logits, int idx) {
        float max = logits[0];
        for (float v : logits) if (v > max) max = v;
        double sum = 0;
        for (float v : logits) sum += Math.exp(v - max);
        return (float) (Math.exp(logits[idx] - max) / sum);
    }
}
