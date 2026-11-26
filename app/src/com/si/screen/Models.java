package com.si.screen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** 模型清单：默认装 en/ja/ch/cht 四个识别模型 + 一个共用检测模型。 */
public class Models {
    public static final String BASE =
            "https://github.com/JMX-4869/screen-instant-translate/releases/download/models-v1/";

    public static class F {
        public final String file;
        public final long bytes;

        F(String file, long bytes) {
            this.file = file;
            this.bytes = bytes;
        }

        public String url() {
            return BASE + file;
        }
    }

    public static class M {
        public final String id;      // 也是 rec 语种键
        public final String name;
        public final String note;
        public final List<F> files = new ArrayList<>();
        public final boolean rec;

        M(String id, String name, String note, boolean rec, F... fs) {
            this.id = id;
            this.name = name;
            this.note = note;
            this.rec = rec;
            files.addAll(Arrays.asList(fs));
        }

        public long size() {
            long s = 0;
            for (F f : files) s += f.bytes;
            return s;
        }
    }

    /** 检测模型，必装。 */
    public static final M DET = new M("det", "文字检测", "所有语种共用，必装", false,
            new F("det.onnx", 4745517));

    public static final List<M> ALL = new ArrayList<>();

    static {
        ALL.add(new M("ja", "日文", "漫画 / 网页", true,
                new F("ja.onnx", 9753335), new F("ja.txt", 17332)));
        ALL.add(new M("ch", "简体中文", "默认语言", true,
                new F("ch.onnx", 10857958), new F("ch.txt", 26249)));
        ALL.add(new M("en", "英文", "拉丁字母", true,
                new F("en.onnx", 7653044), new F("en.txt", 190)));
        ALL.add(new M("cht", "繁体中文", "港台用字", true,
                new F("cht.onnx", 11152536), new F("cht.txt", 33443)));
    }

    public static M byId(String id) {
        for (M m : ALL) if (m.id.equals(id)) return m;
        if (DET.id.equals(id)) return DET;
        return null;
    }

    public static String mb(long bytes) {
        return String.format("%.1f MB", bytes / 1048576.0);
    }
}
