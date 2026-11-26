package com.si.screen;

import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.os.Handler;

import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 常驻镜像：整个服务生命周期只建一个 VirtualDisplay，
 * 每次点球从 ImageReader 取最新一帧（Android 14 不允许反复建虚拟屏）。
 */
public class ScreenMirror {
    private ImageReader reader;
    private VirtualDisplay vd;
    private volatile CountDownLatch latch;
    private int w, h;

    public void start(MediaProjection mp, int w, int h, int dpi, Handler handler) {
        this.w = w;
        this.h = h;
        reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 3);
        reader.setOnImageAvailableListener(r -> {
            CountDownLatch l = latch;
            if (l != null) l.countDown();
        }, handler);
        vd = mp.createVirtualDisplay("si-mirror", w, h, dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.getSurface(), null, handler);
    }

    public Bitmap grab(long timeoutMs) {
        if (reader == null) return null;
        for (int attempt = 0; attempt < 2; attempt++) {
            CountDownLatch l = new CountDownLatch(1);
            latch = l;
            try {
                l.await(timeoutMs, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ignored) {
            }
            latch = null;
            Image img = reader.acquireLatestImage();
            if (img == null) continue;
            try {
                Image.Plane[] planes = img.getPlanes();
                ByteBuffer buf = planes[0].getBuffer();
                int pixelStride = planes[0].getPixelStride();
                int rowStride = planes[0].getRowStride();
                int rowPadding = rowStride - pixelStride * w;
                int bw = w + rowPadding / pixelStride;
                Bitmap raw = Bitmap.createBitmap(bw, h, Bitmap.Config.ARGB_8888);
                raw.copyPixelsFromBuffer(buf);
                if (bw == w) return raw;
                Bitmap out = Bitmap.createBitmap(raw, 0, 0, w, h);
                raw.recycle();
                return out;
            } catch (Throwable t) {
                return null;
            } finally {
                img.close();
            }
        }
        return null;
    }

    public void stop() {
        try {
            if (vd != null) vd.release();
        } catch (Throwable ignored) {
        }
        try {
            if (reader != null) reader.close();
        } catch (Throwable ignored) {
        }
        vd = null;
        reader = null;
    }
}
