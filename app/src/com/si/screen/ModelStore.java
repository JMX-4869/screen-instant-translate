package com.si.screen;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** 模型下载与本地状态。文件落在 filesDir/models/ 下。 */
public class ModelStore {
    public interface Progress {
        void on(String id, String file, long got, long total);

        void done(String id, boolean ok, String err);
    }

    private final File dir;

    public ModelStore(Context c) {
        dir = new File(c.getFilesDir(), "models");
        if (!dir.exists()) dir.mkdirs();
    }

    public File dir() {
        return dir;
    }

    public File file(String name) {
        return new File(dir, name);
    }

    public boolean installed(Models.M m) {
        for (Models.F f : m.files) {
            File t = new File(dir, f.file);
            if (!t.exists() || t.length() != f.bytes) return false;
        }
        return true;
    }

    public int installedCount() {
        int n = installed(Models.DET) ? 1 : 0;
        for (Models.M m : Models.ALL) if (installed(m)) n++;
        return n;
    }

    public long usedBytes() {
        long s = 0;
        File[] fs = dir.listFiles();
        if (fs != null) for (File f : fs) s += f.length();
        return s;
    }

    public void delete(Models.M m) {
        for (Models.F f : m.files) {
            File t = new File(dir, f.file);
            if (t.exists()) t.delete();
        }
    }

    /** 同步下载，调用方放到工作线程。 */
    public boolean download(Models.M m, Progress cb) {
        for (Models.F f : m.files) {
            File out = new File(dir, f.file);
            if (out.exists() && out.length() == f.bytes) {
                cb.on(m.id, f.file, f.bytes, f.bytes);
                continue;
            }
            File tmp = new File(dir, f.file + ".part");
            HttpURLConnection c = null;
            InputStream in = null;
            FileOutputStream os = null;
            try {
                c = (HttpURLConnection) new URL(f.url()).openConnection();
                c.setConnectTimeout(15000);
                c.setReadTimeout(30000);
                c.setInstanceFollowRedirects(true);
                c.setRequestProperty("User-Agent", "si-app");
                int code = c.getResponseCode();
                if (code != 200) throw new IOException("HTTP " + code);
                long total = f.bytes;
                in = c.getInputStream();
                os = new FileOutputStream(tmp);
                byte[] buf = new byte[64 * 1024];
                long got = 0;
                int n;
                long last = 0;
                while ((n = in.read(buf)) > 0) {
                    os.write(buf, 0, n);
                    got += n;
                    long now = System.currentTimeMillis();
                    if (now - last > 120) {
                        last = now;
                        cb.on(m.id, f.file, got, total);
                    }
                }
                os.flush();
                os.close();
                os = null;
                in.close();
                in = null;
                if (tmp.length() != f.bytes) throw new IOException("大小不符");
                if (out.exists()) out.delete();
                if (!tmp.renameTo(out)) throw new IOException("改名失败");
                cb.on(m.id, f.file, f.bytes, f.bytes);
            } catch (Exception e) {
                try {
                    if (os != null) os.close();
                } catch (Exception ignored) {
                }
                try {
                    if (in != null) in.close();
                } catch (Exception ignored) {
                }
                if (c != null) c.disconnect();
                if (tmp.exists()) tmp.delete();
                cb.done(m.id, false, e.getMessage());
                return false;
            }
        }
        cb.done(m.id, true, null);
        return true;
    }
}
