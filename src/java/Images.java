package com.personal.clipviewer;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 썸네일/포스터 이미지 로더 (메모리 캐시만) */
public class Images {

    private static final Handler handler = new Handler(Looper.getMainLooper());
    private static final ExecutorService pool = Executors.newFixedThreadPool(3);
    // 메모리의 1/8까지 캐시 (크기는 바이트 기준)
    private static final LruCache<String, Bitmap> cache =
            new LruCache<String, Bitmap>((int) (Runtime.getRuntime().maxMemory() / 8)) {
                @Override
                protected int sizeOf(String key, Bitmap b) {
                    return b.getByteCount();
                }
            };

    public static void load(ImageView iv, String url) {
        load(iv, url, 1);
    }

    public static void load(ImageView iv, String url, int sample) {
        iv.setTag(url);
        if (url == null) {
            iv.setImageDrawable(null);
            return;
        }
        Bitmap b = cache.get(url);
        if (b != null) {
            iv.setImageBitmap(b);
            return;
        }
        iv.setImageDrawable(null);
        pool.execute(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inSampleSize = sample;
                Bitmap bmp;
                try (InputStream in = c.getInputStream()) {
                    bmp = BitmapFactory.decodeStream(in, null, o);
                }
                if (bmp == null) return;
                cache.put(url, bmp);
                handler.post(() -> {
                    if (url.equals(iv.getTag())) iv.setImageBitmap(bmp);
                });
            } catch (Exception ignored) {
            }
        });
    }
}
