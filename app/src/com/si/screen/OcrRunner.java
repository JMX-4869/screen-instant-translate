package com.si.screen;

import android.graphics.Bitmap;

import com.si.ocr.OcrEngine;
import com.si.ocr.Pix;

import java.util.ArrayList;
import java.util.List;

/** 把 Bitmap 交给本地模型识别；模型按已安装的语言加载，只加载一次的缓存。 */
public class OcrRunner {
    private final ModelStore store;
    private OcrEngine engine;
    private String signature = "";

    public OcrRunner(ModelStore s) {
        this.store = s;
    }

    public static Pix toPix(Bitmap b) {
        int w = b.getWidth(), h = b.getHeight();
        int[] px = new int[w * h];
        b.getPixels(px, 0, w, 0, 0, w, h);
        return new Pix(w, h, px);
    }

    /** 已安装且有可用识别的语种，按偏好排序。 */
    public List<String> langs() {
        List<String> out = new ArrayList<>();
        for (Models.M m : Models.ALL) {
            if (m.rec && store.installed(m)) out.add(m.id);
        }
        return out;
    }

    private void ensure(List<String> langs) throws Exception {
        String sig = langs.toString();
        if (engine != null && sig.equals(signature)) return;
        String dir = store.dir().getAbsolutePath();
        OcrEngine e = new OcrEngine(dir, 4);
        for (String lg : langs) {
            Models.M m = Models.byId(lg);
            if (m == null) continue;
            e.add(lg, store.file(lg + ".onnx").getAbsolutePath(),
                    store.file(lg + ".txt").getAbsolutePath());
        }
        engine = e;
        signature = sig;
    }

    public List<OcrEngine.Line> run(Bitmap bmp) throws Exception {
        List<String> langs = langs();
        if (langs.isEmpty()) throw new Exception("没有可用识别模型");
        ensure(langs);
        Bitmap src = bmp;
        int maxSide = 1400;
        if (Math.max(bmp.getWidth(), bmp.getHeight()) > maxSide) {
            float s = maxSide / (float) Math.max(bmp.getWidth(), bmp.getHeight());
            src = Bitmap.createScaledBitmap(bmp, Math.round(bmp.getWidth() * s),
                    Math.round(bmp.getHeight() * s), true);
            src.setDensity(bmp.getDensity());
        }
        List<OcrEngine.Line> lines = engine.run(toPix(src), langs);
        if (src != bmp) {
            // 坐标换算回原图
            float k = bmp.getWidth() / (float) src.getWidth();
            for (OcrEngine.Line l : lines) {
                l.x0 *= k;
                l.x1 *= k;
                l.y0 *= k;
                l.y1 *= k;
            }
        }
        return lines;
    }

    public void reset() {
        engine = null;
        signature = "";
    }
}
