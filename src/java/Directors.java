package com.personal.clipviewer;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.Collator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 클립에 한 번이라도 나온 감독 모으기 */
public class Directors {

    public static class Director {
        public String name;
        public int tmdbId;      // 0이면 TMDB 정보 없음 (직접 입력만)
        public String photo;
        public List<Store.Clip> clips = new ArrayList<>();
    }

    public static List<Director> collect(List<Store.Clip> all) {
        Map<String, Director> map = new LinkedHashMap<>();
        for (Store.Clip c : all) {
            List<String> names = ClipFilter.directorsOf(c);
            JSONArray ids = null, photos = null;
            if (c.info != null) {
                try {
                    JSONObject o = new JSONObject(c.info);
                    // 이름 순서와 id 순서가 맞을 때만 id를 씀
                    if (o.optString("director").equals(c.director)) {
                        ids = o.optJSONArray("directorIds");
                        photos = o.optJSONArray("directorPhotos");
                    }
                } catch (Exception ignored) {
                }
            }
            for (int i = 0; i < names.size(); i++) {
                int id = ids != null && i < ids.length() ? ids.optInt(i) : 0;
                String key = id > 0 ? "id:" + id : "name:" + names.get(i);
                Director d = map.get(key);
                if (d == null) {
                    d = new Director();
                    d.name = names.get(i);
                    d.tmdbId = id;
                    map.put(key, d);
                }
                if (d.photo == null && photos != null && i < photos.length() && !photos.optString(i).isEmpty()) {
                    d.photo = photos.optString(i);
                }
                d.clips.add(c);
            }
        }
        List<Director> out = new ArrayList<>(map.values());
        Collator col = Collator.getInstance(Locale.KOREAN);
        out.sort((a, b) -> a.clips.size() != b.clips.size() ? b.clips.size() - a.clips.size() : col.compare(a.name, b.name));
        return out;
    }
}
