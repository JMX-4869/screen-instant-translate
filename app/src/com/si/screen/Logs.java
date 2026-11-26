package com.si.screen;

import android.content.Context;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** 简单运行日志，落在 App 私有外部目录，设置页可查看。 */
public class Logs {

    public static void install(final android.content.Context c) {
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            try {
                w(c, "CRASH " + android.util.Log.getStackTraceString(e));
            } catch (Throwable ignored) {
            }
            if (prev != null) prev.uncaughtException(t, e);
        });
    }
    private static final Object LOCK = new Object();

    public static File file(Context c) {
        File d = c.getExternalFilesDir(null);
        if (d == null) d = c.getFilesDir();
        return new File(d, "si.log");
    }

    public static void w(Context c, String s) {
        synchronized (LOCK) {
            try {
                File f = file(c);
                if (f.exists() && f.length() > 200_000) f.delete();
                FileWriter fw = new FileWriter(f, true);
                fw.write(new SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(new Date())
                        + "  " + s + "\n");
                fw.close();
            } catch (Throwable ignored) {
            }
        }
    }

    public static String tail(Context c, int max) {
        try {
            File f = file(c);
            if (!f.exists()) return "(还没有日志)";
            StringBuilder sb = new StringBuilder();
            java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader(f));
            String line;
            java.util.ArrayDeque<String> q = new java.util.ArrayDeque<>();
            int size = 0;
            while ((line = r.readLine()) != null) {
                q.addLast(line);
                size += line.length() + 1;
                while (size > max && !q.isEmpty()) size -= q.removeFirst().length() + 1;
            }
            r.close();
            for (String s : q) sb.append(s).append('\n');
            return sb.length() == 0 ? "(空)" : sb.toString();
        } catch (Throwable t) {
            return "读日志失败：" + t;
        }
    }

    public static void clear(Context c) {
        try {
            file(c).delete();
        } catch (Throwable ignored) {
        }
    }
}
