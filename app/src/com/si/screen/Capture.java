package com.si.screen;

import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.os.SystemClock;

import java.nio.ByteBuffer;

public class Capture {
    /** 抓一帧全屏画面；失败返回 null。 */
    public static Bitmap shot(MediaProjection mp, int w, int h, int dpi) {
        if (mp == null || w <= 0 || h <= 0) return null;
        ImageReader reader = null;
        VirtualDisplay vd = null;
        try {
            reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2);
            vd = mp.createVirtualDisplay("si-cap", w, h, dpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    reader.getSurface(), null, null);
            Image img = null;
            long t0 = SystemClock.uptimeMillis();
            while (SystemClock.uptimeMillis() - t0 < 2500) {
                img = reader.acquireLatestImage();
                if (img != null) break;
                SystemClock.sleep(30);
            }
            if (img == null) return null;

            Image.Plane[] planes = img.getPlanes();
            ByteBuffer buf = planes[0].getBuffer();
            int pixelStride = planes[0].getPixelStride();
            int rowStride = planes[0].getRowStride();
            int rowPadding = rowStride - pixelStride * w;
            int bw = w + rowPadding / pixelStride;

            Bitmap raw = Bitmap.createBitmap(bw, h, Bitmap.Config.ARGB_8888);
            raw.copyPixelsFromBuffer(buf);
            if (bw == w) {
                return raw;
            }
            Bitmap out = Bitmap.createBitmap(raw, 0, 0, w, h);
            raw.recycle();
            return out;
        } catch (Throwable t) {
            return null;
        } finally {
            try {
                if (vd != null) vd.release();
            } catch (Throwable ignored) {
            }
            try {
                if (reader != null) reader.close();
            } catch (Throwable ignored) {
            }
        }
    }
}
