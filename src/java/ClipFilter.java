package com.personal.clipviewer;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 클립 목록 검색/정렬/태그 필터 (설정에 저장되어 앱을 껐다 켜도 유지) */
public class ClipFilter {

    public static final String[] SORT_KEYS = {"added", "rating", "tmdb", "year", "title", "watched"};
    public static final String[] SORT_NAMES = {"추가순", "내 별점", "TMDB 평점", "연도", "제목", "최근 시청"};
    // 처음 눌렀을 때의 방향 (제목만 가나다 오름차순, 나머지는 큰 값/최신부터)
    private static final boolean[] SORT_DEFAULT_ASC = {false, false, false, false, true, false};

    public String query = "";
    public String sort = "added";
    public boolean asc = false;
    public Set<Integer> status = new LinkedHashSet<>();
    public Set<String> directors = new LinkedHashSet<>();
    public Set<String> genres = new LinkedHashSet<>();
    public Set<String> decades = new LinkedHashSet<>();
    public Set<String> countries = new LinkedHashSet<>();

    // ---- 저장 ----

    public static ClipFilter load(Context c) {
        ClipFilter f = new ClipFilter();
        try {
            JSONObject o = new JSONObject(c.getSharedPreferences("settings", Context.MODE_PRIVATE).getString("filter", "{}"));
            f.query = o.optString("query");
            f.sort = o.optString("sort", "added");
            f.asc = o.optBoolean("asc");
            JSONArray st = o.optJSONArray("status");
            for (int i = 0; st != null && i < st.length(); i++) f.status.add(st.getInt(i));
            readSet(o, "directors", f.directors);
            readSet(o, "genres", f.genres);
            readSet(o, "decades", f.decades);
            readSet(o, "countries", f.countries);
        } catch (Exception ignored) {
        }
        return f;
    }

    public void save(Context c) {
        try {
            JSONObject o = new JSONObject();
            o.put("query", query);
            o.put("sort", sort);
            o.put("asc", asc);
            o.put("status", new JSONArray(status));
            o.put("directors", new JSONArray(directors));
            o.put("genres", new JSONArray(genres));
            o.put("decades", new JSONArray(decades));
            o.put("countries", new JSONArray(countries));
            c.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putString("filter", o.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    private static void readSet(JSONObject o, String name, Set<String> out) throws Exception {
        JSONArray a = o.optJSONArray(name);
        for (int i = 0; a != null && i < a.length(); i++) out.add(a.getString(i));
    }

    /** 정렬 버튼: 같은 걸 다시 누르면 방향을 뒤집고, 다른 걸 누르면 그 정렬의 기본 방향으로 */
    public void pickSort(String key) {
        if (key.equals(sort)) {
            asc = !asc;
        } else {
            sort = key;
            for (int i = 0; i < SORT_KEYS.length; i++) {
                if (SORT_KEYS[i].equals(key)) asc = SORT_DEFAULT_ASC[i];
            }
        }
    }

    /** 검색어나 태그가 하나라도 걸려 있는지 (정렬은 제외) */
    public boolean isFiltering() {
        return !query.trim().isEmpty() || !status.isEmpty() || !directors.isEmpty()
                || !genres.isEmpty() || !decades.isEmpty() || !countries.isEmpty();
    }

    public void clear() {
        query = "";
        sort = "added";
        asc = false;
        status.clear();
        directors.clear();
        genres.clear();
        decades.clear();
        countries.clear();
    }

    // ---- 클립에서 값 뽑기 ----

    private static JSONObject info(Store.Clip c) {
        if (c.info == null) return null;
        try {
            return new JSONObject(c.info);
        } catch (Exception e) {
            return null;
        }
    }

    private static List<String> split(String s) {
        List<String> out = new ArrayList<>();
        if (s == null) return out;
        for (String p : s.split(",")) {
            if (!p.trim().isEmpty()) out.add(p.trim());
        }
        return out;
    }

    public static List<String> directorsOf(Store.Clip c) {
        return split(c.director);
    }

    public static List<String> genresOf(Store.Clip c) {
        JSONObject o = info(c);
        return o == null ? new ArrayList<>() : split(o.optString("genres"));
    }

    public static List<String> countriesOf(Store.Clip c) {
        JSONObject o = info(c);
        List<String> out = new ArrayList<>();
        if (o == null) return out;
        for (String n : split(o.optString("countries"))) out.add(korean(n));
        return out;
    }

    public static String decadeOf(Store.Clip c) {
        try {
            int y = Integer.parseInt(c.year.trim().substring(0, 4));
            return (y / 10 * 10) + "년대";
        } catch (Exception e) {
            return null;
        }
    }

    private static Double tmdbScore(Store.Clip c) {
        JSONObject o = info(c);
        if (o == null || o.optInt("votes") <= 0) return null;
        return o.optDouble("vote");
    }

    private static Double yearOf(Store.Clip c) {
        try {
            return Double.parseDouble(c.year.trim().substring(0, 4));
        } catch (Exception e) {
            return null;
        }
    }

    // ---- 태그 후보: 많이 나온 순서 ----

    /** 태그별 개수. minCount 이상만, 최대 max개 (단, 이미 고른 태그는 항상 포함) */
    public static Map<String, Integer> count(List<Store.Clip> all, String kind, int minCount, int max, Set<String> selected) {
        Map<String, Integer> counts = new HashMap<>();
        for (Store.Clip c : all) {
            for (String v : values(c, kind)) counts.merge(v, 1, Integer::sum);
        }
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(counts.entrySet());
        if (kind.equals("decade")) entries.sort(Map.Entry.comparingByKey());
        else entries.sort((a, b) -> b.getValue() - a.getValue());

        Map<String, Integer> out = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : entries) {
            if (out.size() >= max) break;
            if (e.getValue() >= minCount) out.put(e.getKey(), e.getValue());
        }
        for (String s : selected) {
            if (!out.containsKey(s)) out.put(s, counts.getOrDefault(s, 0));
        }
        return out;
    }

    private static List<String> values(Store.Clip c, String kind) {
        switch (kind) {
            case "director":
                return directorsOf(c);
            case "genre":
                return genresOf(c);
            case "country":
                return countriesOf(c);
            default:
                List<String> l = new ArrayList<>();
                String d = decadeOf(c);
                if (d != null) l.add(d);
                return l;
        }
    }

    // ---- 적용 ----

    /** 같은 줄 안에서는 "또는", 줄끼리는 "그리고" */
    public List<Store.Clip> apply(List<Store.Clip> all) {
        String q = query.trim().toLowerCase(Locale.ROOT);
        List<Store.Clip> out = new ArrayList<>();
        for (Store.Clip c : all) {
            if (!q.isEmpty()) {
                String hay = (c.title + " " + c.origTitle + " " + c.director).toLowerCase(Locale.ROOT);
                if (!hay.contains(q)) continue;
            }
            if (!status.isEmpty() && !status.contains(c.effectiveStatus())) continue;
            if (!matches(directors, directorsOf(c))) continue;
            if (!matches(genres, genresOf(c))) continue;
            if (!matches(countries, countriesOf(c))) continue;
            if (!decades.isEmpty() && !decades.contains(decadeOf(c))) continue;
            out.add(c);
        }

        Map<Store.Clip, Integer> order = new HashMap<>();
        for (int i = 0; i < all.size(); i++) order.put(all.get(i), i);
        Comparator<Store.Clip> cmp;
        switch (sort) {
            case "rating":
                cmp = byValue(c -> c.rating >= 0 ? c.rating : null);
                break;
            case "tmdb":
                cmp = byValue(ClipFilter::tmdbScore);
                break;
            case "year":
                cmp = byValue(ClipFilter::yearOf);
                break;
            case "watched":
                cmp = byValue(c -> c.watched > 0 ? (double) c.watched : null);
                break;
            case "title":
                Collator col = Collator.getInstance(Locale.KOREAN);
                cmp = (a, b) -> asc ? col.compare(a.title, b.title) : col.compare(b.title, a.title);
                break;
            default:
                // 목록 앞쪽이 최근에 추가한 것
                cmp = (a, b) -> asc ? order.get(b) - order.get(a) : order.get(a) - order.get(b);
        }
        out.sort(cmp);
        return out;
    }

    private interface Value {
        Double of(Store.Clip c);
    }

    /** 값이 없는 클립은 방향과 상관없이 항상 맨 뒤 */
    private Comparator<Store.Clip> byValue(Value v) {
        return (a, b) -> {
            Double x = v.of(a), y = v.of(b);
            if (x == null && y == null) return 0;
            if (x == null) return 1;
            if (y == null) return -1;
            return asc ? Double.compare(x, y) : Double.compare(y, x);
        };
    }

    private static boolean matches(Set<String> wanted, List<String> have) {
        if (wanted.isEmpty()) return true;
        for (String h : have) {
            if (wanted.contains(h)) return true;
        }
        return false;
    }

    // TMDB 국가 이름(영어) → 한글
    private static final Map<String, String> COUNTRY = new HashMap<>();

    static {
        String[] pairs = {
                "United States of America", "미국", "United Kingdom", "영국", "France", "프랑스", "Italy", "이탈리아",
                "Germany", "독일", "West Germany", "서독", "East Germany", "동독", "Sweden", "스웨덴", "Japan", "일본",
                "South Korea", "한국", "North Korea", "북한", "Spain", "스페인", "Soviet Union", "소련", "Russia", "러시아",
                "Canada", "캐나다", "Denmark", "덴마크", "Norway", "노르웨이", "Finland", "핀란드", "Poland", "폴란드",
                "China", "중국", "Hong Kong", "홍콩", "Taiwan", "대만", "India", "인도", "Mexico", "멕시코",
                "Brazil", "브라질", "Argentina", "아르헨티나", "Australia", "호주", "Czechoslovakia", "체코슬로바키아",
                "Czech Republic", "체코", "Hungary", "헝가리", "Netherlands", "네덜란드", "Belgium", "벨기에",
                "Austria", "오스트리아", "Switzerland", "스위스", "Ireland", "아일랜드", "Greece", "그리스",
                "Iran", "이란", "Turkey", "튀르키예", "Yugoslavia", "유고슬라비아", "Portugal", "포르투갈"
        };
        for (int i = 0; i < pairs.length; i += 2) COUNTRY.put(pairs[i], pairs[i + 1]);
    }

    private static String korean(String country) {
        String k = COUNTRY.get(country);
        return k != null ? k : country;
    }
}
