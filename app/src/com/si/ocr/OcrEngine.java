package com.si.ocr;

import ai.onnxruntime.OrtEnvironment;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 检测 + 多语种识别，逐行取置信度最高的语种。 */
public class OcrEngine {
    public static class Line {
        public float x0, y0, x1, y1;
        public String text;
        public float score;
        public String lang;
        public boolean vertical;
    }

    private final OrtEnvironment env = OrtEnvironment.getEnvironment();
    private final Detector det;
    private final Map<String, Recognizer> recs = new LinkedHashMap<>();

    public OcrEngine(String modelsDir, int threads) throws Exception {
        det = new Detector(env, Files.readAllBytes(Paths.get(modelsDir, "det.onnx")), threads, 960, 0.3f);
    }

    public OcrEngine add(String lang, String modelFile, String dictFile) throws Exception {
        recs.put(lang, Recognizer.load(env, lang,
                Paths.get(modelFile).toString(), Paths.get(dictFile).toString(), 4));
        return this;
    }

    public List<Line> run(Pix img, List<String> langs) throws Exception {
        List<float[]> boxes = det.detect(img);
        List<Line> out = new ArrayList<>();
        if (boxes.isEmpty()) return out;

        List<Pix> crops = new ArrayList<>();
        List<Boolean> vertical = new ArrayList<>();
        for (float[] b : boxes) {
            int x0 = (int) b[0] - 2, y0 = (int) b[1] - 2;
            int x1 = (int) b[2] + 2, y1 = (int) b[3] + 2;
            Pix c = img.crop(x0, y0, x1, y1);
            boolean v = (b[3] - b[1]) > (b[2] - b[0]) * 2.0f;
            if (v) c = c.rot90();
            crops.add(c);
            vertical.add(v);
        }

        String[] bestText = new String[crops.size()];
        float[] bestScore = new float[crops.size()];
        String[] bestLang = new String[crops.size()];
        for (String lg : langs) {
            Recognizer r = recs.get(lg);
            if (r == null) continue;
            for (int i = 0; i < crops.size(); i++) {
                Object[] res = r.run(crops.get(i));
                String t = (String) res[0];
                float s = (Float) res[1];
                if (t != null && !t.isEmpty() && s > bestScore[i]) {
                    bestText[i] = t;
                    bestScore[i] = s;
                    bestLang[i] = lg;
                }
            }
        }

        for (int i = 0; i < boxes.size(); i++) {
            if (bestText[i] == null) continue;
            float[] b = boxes.get(i);
            Line l = new Line();
            l.x0 = b[0];
            l.y0 = b[1];
            l.x1 = b[2];
            l.y1 = b[3];
            l.text = bestText[i];
            l.score = bestScore[i];
            l.lang = bestLang[i];
            l.vertical = vertical.get(i);
            out.add(l);
        }
        return out;
    }
}
