package com.personal.clipviewer;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** 저장된 클립 목록 (SharedPreferences에 JSON으로 보관) */
public class Store {

    public static final int NONE = 0, WATCHING = 1, DONE = 2;

    public static class Clip {
        public String id;
        public String title;
        public String director = "";
        public String year = "";
        public String origTitle = ""; // 원제 (검색용)
        public String url;
        public String thumb;
        public String info;       // TMDB에서 가져온 영화 정보(JSON), 없으면 null
        public double pos;        // 마지막 시청 위치(초)
        public double dur;        // 영상 길이(초)
        public boolean finished;  // 끝까지 봤는지
        public int status = -1;   // 사용자가 직접 정한 상태, -1이면 시청 기록으로 자동 판단
        public double rating = -1; // 0.0~5.0, -1이면 없음
        public long watched;      // 마지막으로 연 시각(ms), 0이면 없음

        /** 목록에 보일 이미지: 영화 정보가 있으면 포스터, 없으면 클립 썸네일 */
        public String displayThumb() {
            if (info != null) {
                try {
                    String poster = new JSONObject(info).optString("poster", null);
                    if (poster != null) return poster.replace("/w342/", "/w500/");
                } catch (Exception ignored) {
                }
            }
            return thumb;
        }

        public int effectiveStatus() {
            if (status >= 0) return status;
            if (finished) return DONE;
            if (pos > 3) return WATCHING;
            return NONE;
        }
    }

    public interface Edit {
        void apply(Clip clip);
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("clips", Context.MODE_PRIVATE);
    }

    public static List<Clip> load(Context c) {
        List<Clip> list = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs(c).getString("list", "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Clip clip = new Clip();
                clip.id = o.getString("id");
                clip.title = o.optString("title");
                clip.director = o.optString("director");
                clip.year = o.optString("year");
                clip.origTitle = o.optString("origTitle");
                clip.url = o.optString("url");
                clip.thumb = o.has("thumb") ? o.getString("thumb") : null;
                clip.info = o.has("info") ? o.getString("info") : null;
                clip.pos = o.optDouble("pos", 0);
                clip.dur = o.optDouble("dur", 0);
                clip.finished = o.optBoolean("finished");
                clip.status = o.optInt("status", -1);
                clip.rating = o.optDouble("rating", -1);
                clip.watched = o.optLong("watched");
                list.add(clip);
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    public static void save(Context c, List<Clip> list) {
        try {
            JSONArray arr = new JSONArray();
            for (Clip clip : list) {
                JSONObject o = new JSONObject();
                o.put("id", clip.id);
                o.put("title", clip.title);
                o.put("director", clip.director);
                o.put("year", clip.year);
                o.put("origTitle", clip.origTitle);
                o.put("url", clip.url);
                if (clip.thumb != null) o.put("thumb", clip.thumb);
                if (clip.info != null) o.put("info", clip.info);
                o.put("pos", clip.pos);
                o.put("dur", clip.dur);
                o.put("finished", clip.finished);
                o.put("status", clip.status);
                o.put("rating", clip.rating);
                o.put("watched", clip.watched);
                arr.put(o);
            }
            prefs(c).edit().putString("list", arr.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    public static Clip get(Context c, String id) {
        for (Clip clip : load(c)) {
            if (clip.id.equals(id)) return clip;
        }
        return null;
    }

    public static synchronized void update(Context c, String id, Edit edit) {
        List<Clip> list = load(c);
        for (Clip clip : list) {
            if (clip.id.equals(id)) edit.apply(clip);
        }
        save(c, list);
    }

    // ---- 백업 / 복원 (TMDB 키는 넣지 않음) ----

    public static String exportJson(Context c) throws Exception {
        JSONObject o = new JSONObject();
        o.put("app", "clipviewer");
        o.put("version", 1);
        o.put("clips", new JSONArray(prefs(c).getString("list", "[]")));
        o.put("channels", new JSONArray(prefs(c).getString("channels", "[]")));
        String filter = c.getSharedPreferences("settings", Context.MODE_PRIVATE).getString("filter", null);
        if (filter != null) o.put("filter", new JSONObject(filter));
        return o.toString(2);
    }

    /** 현재 목록을 백업 내용으로 바꿈. 형식이 틀리면 예외 */
    public static int importJson(Context c, String json) throws Exception {
        JSONObject o = new JSONObject(json);
        if (!"clipviewer".equals(o.optString("app"))) throw new Exception("클립뷰어 백업 파일이 아닙니다");
        JSONArray clips = o.getJSONArray("clips");
        JSONArray channels = o.optJSONArray("channels");
        prefs(c).edit()
                .putString("list", clips.toString())
                .putString("channels", channels == null ? "[]" : channels.toString())
                .apply();
        JSONObject filter = o.optJSONObject("filter");
        if (filter != null) {
            c.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putString("filter", filter.toString()).apply();
        }
        return clips.length();
    }

    // ---- 채널 ----

    private static final String CHANNEL_BASE = "https://example.com/@";

    public static class Channel {
        public String handle;  // @ 뒤의 아이디
        public String name;
        public String desc;
        public String image;

        public String url() {
            return CHANNEL_BASE + handle; // 실제 서비스 주소는 공개본에서 제외
        }
    }

    public static List<Channel> loadChannels(Context c) {
        List<Channel> list = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs(c).getString("channels", "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Channel ch = new Channel();
                ch.handle = o.getString("handle");
                ch.name = o.optString("name");
                ch.desc = o.optString("desc");
                ch.image = o.has("image") ? o.getString("image") : null;
                list.add(ch);
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    public static void saveChannels(Context c, List<Channel> list) {
        try {
            JSONArray arr = new JSONArray();
            for (Channel ch : list) {
                JSONObject o = new JSONObject();
                o.put("handle", ch.handle);
                o.put("name", ch.name);
                o.put("desc", ch.desc);
                if (ch.image != null) o.put("image", ch.image);
                arr.put(o);
            }
            prefs(c).edit().putString("channels", arr.toString()).apply();
        } catch (Exception ignored) {
        }
    }
}
