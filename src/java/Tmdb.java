package com.personal.clipviewer;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** TMDB 검색/상세 조회 (네트워크 작업이므로 백그라운드 스레드에서 호출) */
public class Tmdb {

    private static final String API = "https://api.themoviedb.org/3";
    public static final String IMG = "https://image.tmdb.org/t/p/";

    public static String key(Context c) {
        return c.getSharedPreferences("settings", Context.MODE_PRIVATE).getString("tmdb_key", "").trim();
    }

    public static void setKey(Context c, String key) {
        c.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putString("tmdb_key", key.trim()).apply();
    }

    /** 후보 영화 하나 = 앱에 저장할 정보 전부 */
    public static class Movie {
        public JSONObject json = new JSONObject();
        public Set<Integer> directorIds = new HashSet<>();

        public String title() {
            return json.optString("title");
        }

        public String year() {
            return json.optString("year");
        }

        public String director() {
            return json.optString("director");
        }

        public String poster() {
            return json.optString("poster", null);
        }
    }

    /**
     * 제목(+원제, 연도, 감독)으로 찾아 후보 목록을 돌려줌.
     * 감독이나 연도가 맞는 후보가 있으면 그것만 남김.
     */
    public static List<Movie> find(String key, String title, String origTitle, String year, String director) throws Exception {
        List<Integer> ids = new ArrayList<>();
        for (String q : new String[]{title, origTitle}) {
            if (q == null || q.trim().isEmpty()) continue;
            if (!year.isEmpty()) search(key, q, year, ids);
            if (ids.isEmpty()) search(key, q, "", ids);
            if (!ids.isEmpty()) break;
        }

        List<Movie> movies = new ArrayList<>();
        for (int i = 0; i < Math.min(8, ids.size()); i++) movies.add(details(key, ids.get(i)));

        if (!director.isEmpty()) {
            Set<Integer> people = searchPeople(key, director);
            String d = norm(director);
            List<Movie> hit = new ArrayList<>();
            for (Movie m : movies) {
                boolean idMatch = false;
                for (int id : m.directorIds) idMatch |= people.contains(id);
                if (idMatch || norm(m.director()).contains(d)) hit.add(m);
            }
            if (!hit.isEmpty()) movies = hit;
        }
        if (!year.isEmpty() && movies.size() > 1) {
            List<Movie> hit = new ArrayList<>();
            for (Movie m : movies) {
                try {
                    if (Math.abs(Integer.parseInt(m.year()) - Integer.parseInt(year)) <= 1) hit.add(m);
                } catch (NumberFormatException ignored) {
                }
            }
            if (!hit.isEmpty()) movies = hit;
        }
        return movies;
    }

    private static void search(String key, String q, String year, List<Integer> out) throws Exception {
        String url = API + "/search/movie?language=ko-KR&query=" + enc(q) + (year.isEmpty() ? "" : "&primary_release_year=" + year);
        JSONArray res = get(key, url).optJSONArray("results");
        if (res == null) return;
        for (int i = 0; i < res.length(); i++) {
            int id = res.getJSONObject(i).getInt("id");
            if (!out.contains(id)) out.add(id);
        }
    }

    private static Set<Integer> searchPeople(String key, String name) throws Exception {
        Set<Integer> ids = new HashSet<>();
        JSONArray res = get(key, API + "/search/person?language=ko-KR&query=" + enc(name)).optJSONArray("results");
        if (res != null) {
            for (int i = 0; i < Math.min(5, res.length()); i++) ids.add(res.getJSONObject(i).getInt("id"));
        }
        return ids;
    }

    public static Movie details(String key, int id) throws Exception {
        JSONObject d = get(key, API + "/movie/" + id + "?language=ko-KR&append_to_response=credits");
        Movie m = new Movie();
        JSONObject o = m.json;
        o.put("tmdbId", id);
        o.put("title", d.optString("title"));
        o.put("origTitle", d.optString("original_title"));
        String date = d.optString("release_date");
        o.put("year", date.length() >= 4 ? date.substring(0, 4) : "");
        o.put("runtime", d.optInt("runtime"));
        o.put("tagline", d.optString("tagline"));
        o.put("vote", d.optDouble("vote_average", 0));
        o.put("votes", d.optInt("vote_count"));
        if (!d.isNull("poster_path")) o.put("poster", IMG + "w342" + d.optString("poster_path"));
        if (!d.isNull("backdrop_path")) o.put("backdrop", IMG + "w780" + d.optString("backdrop_path"));
        o.put("genres", names(d.optJSONArray("genres"), 10));
        o.put("countries", names(d.optJSONArray("production_countries"), 3));

        String overview = d.optString("overview");
        if (overview.isEmpty()) {
            overview = get(key, API + "/movie/" + id + "?language=en-US").optString("overview");
        }
        o.put("overview", overview);

        JSONObject credits = d.optJSONObject("credits");
        List<String> directors = new ArrayList<>();
        JSONArray ids = new JSONArray(), photos = new JSONArray();
        boolean koreanFilm = "ko".equals(d.optString("original_language"));
        if (credits != null) {
            JSONArray crew = credits.optJSONArray("crew");
            for (int i = 0; crew != null && i < crew.length(); i++) {
                JSONObject p = crew.getJSONObject(i);
                if ("Director".equals(p.optString("job")) && m.directorIds.add(p.optInt("id"))) {
                    directors.add(directorName(key, p.optInt("id"), p.optString("name"), koreanFilm));
                    ids.put(p.optInt("id"));
                    photos.put(p.isNull("profile_path") ? "" : IMG + "w185" + p.optString("profile_path"));
                }
            }
            o.put("cast", names(credits.optJSONArray("cast"), 5));
        }
        o.put("director", String.join(", ", directors));
        // 감독 탭/감독 페이지용 (director 이름과 같은 순서)
        o.put("directorIds", ids);
        o.put("directorPhotos", photos);
        return m;
    }

    private static final Pattern HANGUL = Pattern.compile("^[가-힣\\s]+$");
    private static final Pattern KOREA = Pattern.compile("(?i)korea|한국|대한민국|서울|seoul|busan|부산");

    /**
     * 한국인 감독은 한글 이름, 나머지는 영어(원어) 이름.
     * 한국인 판단: 출생지에 한국이 들어 있거나, 출생지가 없으면 한국어 영화인지로.
     */
    public static String directorName(String key, int personId, String fallback, boolean koreanFilm) {
        try {
            JSONObject p = get(key, API + "/person/" + personId + "?language=en-US");
            String english = p.optString("name", fallback);
            String birth = p.optString("place_of_birth");
            boolean korean = birth.isEmpty() || "null".equals(birth) ? koreanFilm : KOREA.matcher(birth).find();
            if (!korean) return english;
            if (HANGUL.matcher(english).matches()) return english;
            JSONArray aka = p.optJSONArray("also_known_as");
            for (int i = 0; aka != null && i < aka.length(); i++) {
                String n = aka.optString(i).trim();
                if (HANGUL.matcher(n).matches()) return n;
            }
            return english;
        } catch (Exception e) {
            return fallback;
        }
    }

    /** 감독 페이지: 인물 정보 + 감독한 영화 목록 (연도순) */
    public static class Person {
        public String name, photo, bio, birth, birthday;
        public List<JSONObject> films = new ArrayList<>();
    }

    public static Person person(String key, int id, String displayName) throws Exception {
        JSONObject p = get(key, API + "/person/" + id + "?language=ko-KR&append_to_response=movie_credits");
        Person out = new Person();
        out.name = displayName;
        out.photo = p.isNull("profile_path") ? null : IMG + "w342" + p.optString("profile_path");
        out.bio = p.optString("biography");
        if (out.bio.isEmpty()) out.bio = get(key, API + "/person/" + id + "?language=en-US").optString("biography");
        out.birth = p.isNull("place_of_birth") ? "" : p.optString("place_of_birth");
        out.birthday = p.isNull("birthday") ? "" : p.optString("birthday");
        JSONObject credits = p.optJSONObject("movie_credits");
        JSONArray crew = credits == null ? null : credits.optJSONArray("crew");
        Set<Integer> seen = new HashSet<>();
        for (int i = 0; crew != null && i < crew.length(); i++) {
            JSONObject c = crew.getJSONObject(i);
            if (!"Director".equals(c.optString("job")) || !seen.add(c.optInt("id"))) continue;
            JSONObject f = new JSONObject();
            f.put("id", c.optInt("id"));
            f.put("title", c.optString("title"));
            f.put("origTitle", c.optString("original_title"));
            String date = c.isNull("release_date") ? "" : c.optString("release_date");
            f.put("year", date.length() >= 4 ? date.substring(0, 4) : "");
            if (!c.isNull("poster_path")) f.put("poster", IMG + "w342" + c.optString("poster_path"));
            out.films.add(f);
        }
        // 연도 없는 작품(미개봉 등)은 맨 뒤
        out.films.sort((a, b) -> {
            String x = a.optString("year"), y = b.optString("year");
            if (x.isEmpty() != y.isEmpty()) return x.isEmpty() ? 1 : -1;
            return x.compareTo(y);
        });
        return out;
    }

    private static String names(JSONArray arr, int max) {
        List<String> out = new ArrayList<>();
        for (int i = 0; arr != null && i < Math.min(max, arr.length()); i++) {
            out.add(arr.optJSONObject(i).optString("name"));
        }
        return String.join(", ", out);
    }

    private static String norm(String s) {
        return s.toLowerCase().replaceAll("[\\s.·-]", "");
    }

    private static String enc(String s) throws Exception {
        return URLEncoder.encode(s.trim(), "UTF-8");
    }

    private static JSONObject get(String key, String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url + "&api_key=" + key).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(15000);
        int code = c.getResponseCode();
        if (code == 401) throw new Exception("TMDB 키가 올바르지 않습니다");
        if (code != 200) throw new Exception("TMDB 오류 " + code);
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) > 0) buf.write(b, 0, n);
            return new JSONObject(buf.toString("UTF-8"));
        }
    }
}
