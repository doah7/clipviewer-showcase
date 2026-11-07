package com.personal.clipviewer;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 유튜브 링크는 앱 안에서 재생하지 않고 유튜브 앱으로 넘김 */
public class Youtube {

    private static final Pattern ID = Pattern.compile(
            "(?:youtu\\.be/|youtube\\.com/(?:watch\\?(?:.*&)?v=|shorts/|embed/|live/|movies/))([A-Za-z0-9_-]{11})");

    public static boolean is(String url) {
        return url != null && url.matches("(?i)https?://([a-z0-9-]+\\.)*(youtube\\.com|youtu\\.be)/.*");
    }

    public static String videoId(String url) {
        Matcher m = ID.matcher(url);
        return m.find() ? m.group(1) : null;
    }

    public static String thumb(String url) {
        String id = videoId(url);
        return id == null ? null : "https://i.ytimg.com/vi/" + id + "/hqdefault.jpg";
    }

    public static void open(Activity a, String url) {
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        i.setPackage("com.google.android.youtube");
        try {
            a.startActivity(i);
        } catch (ActivityNotFoundException e) {
            // 유튜브 앱이 없으면 브라우저 등 기본 앱으로
            a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        }
    }

    /** 영상 제목 (oEmbed, 키 필요 없음). 백그라운드 스레드에서 호출, 실패하면 null */
    public static String fetchTitle(String url) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(
                    "https://www.youtube.com/oembed?format=json&url=" + URLEncoder.encode(url, "UTF-8")).openConnection();
            c.setConnectTimeout(10000);
            c.setReadTimeout(10000);
            if (c.getResponseCode() != 200) return null;
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                byte[] b = new byte[4096];
                int n;
                while ((n = in.read(b)) > 0) buf.write(b, 0, n);
                String t = new JSONObject(buf.toString("UTF-8")).optString("title");
                return t.isEmpty() ? null : t;
            }
        } catch (Exception e) {
            return null;
        }
    }
}
