package com.personal.clipviewer;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 클립 입력 창, TMDB 영화 정보 찾기/고르기/보기 */
public class MovieUi {

    private static final Handler handler = new Handler(Looper.getMainLooper());
    private static final ExecutorService pool = Executors.newSingleThreadExecutor();

    public interface FormListener {
        void onSave(String title, String origTitle, String director, String year, String link);
    }

    /** 채널 영상 제목 "돌아오지 않는 강(River of No Return) [1954년 미국][한글자막]" → {제목, 원제, 연도} */
    public static String[] parseTitle(String raw) {
        String year = "";
        Matcher y = Pattern.compile("(\\d{4})\\s*년").matcher(raw);
        if (y.find()) year = y.group(1);
        String s = raw.replaceAll("\\[[^\\]]*\\]", "").trim();
        String orig = "";
        Matcher m = Pattern.compile("^(.+?)\\s*\\(([^)]*[A-Za-z][^)]*)\\)\\s*$").matcher(s);
        if (m.find()) {
            s = m.group(1).trim();
            orig = m.group(2).trim();
        }
        return new String[]{s, orig, year};
    }

    /** 제목 / 원제 / 감독 / 연도 / (링크) 입력 창. showLink가 false면 링크 칸 없음 */
    public static void showForm(Activity a, String dialogTitle, Store.Clip init, boolean showLink, FormListener l) {
        int dp = (int) a.getResources().getDisplayMetrics().density;
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(24 * dp, 8 * dp, 24 * dp, 0);
        EditText title = field(a, box, "제목", init.title);
        EditText orig = field(a, box, "원제 (선택, 검색 정확도↑)", init.origTitle);
        EditText director = field(a, box, "감독 (선택)", init.director);
        EditText year = field(a, box, "연도 (선택)", init.year);
        year.setInputType(InputType.TYPE_CLASS_NUMBER);
        EditText link = field(a, box, "링크 (선택)", init.url);
        link.setSingleLine(false);
        if (!showLink) link.setVisibility(View.GONE);

        ScrollView sv = new ScrollView(a);
        sv.addView(box);
        new AlertDialog.Builder(a)
                .setTitle(dialogTitle)
                .setView(sv)
                .setPositiveButton("저장", (d, w) -> l.onSave(
                        title.getText().toString().trim(),
                        orig.getText().toString().trim(),
                        director.getText().toString().trim(),
                        year.getText().toString().trim(),
                        link.getText().toString()))
                .setNegativeButton("취소", null)
                .show();
    }

    private static EditText field(Activity a, LinearLayout box, String hint, String value) {
        EditText e = new EditText(a);
        e.setHint(hint);
        e.setSingleLine();
        if (value != null) e.setText(value);
        box.addView(e);
        return e;
    }

    /**
     * 클립의 영화 정보를 TMDB에서 찾음. 하나면 바로 붙이고, 여러 개면 고르게 함.
     * done은 정보가 바뀌었을 때 메인 스레드에서 불림.
     */
    public static void lookup(Activity a, String clipId, Runnable done) {
        String key = Tmdb.key(a);
        if (key.isEmpty()) {
            Toast.makeText(a, "설정(⚙)에 TMDB 키를 넣으면 영화 정보를 찾아줍니다", Toast.LENGTH_LONG).show();
            return;
        }
        Store.Clip clip = Store.get(a, clipId);
        if (clip == null || TextUtils.isEmpty(clip.title)) return;
        android.content.Context app = a.getApplicationContext();
        pool.execute(() -> {
            try {
                List<Tmdb.Movie> found = Tmdb.find(key, clip.title, clip.origTitle, clip.year, clip.director);
                handler.post(() -> {
                    if (found.isEmpty()) {
                        Toast.makeText(app, "'" + clip.title + "' 영화 정보를 찾지 못했습니다", Toast.LENGTH_SHORT).show();
                    } else if (found.size() == 1) {
                        apply(app, clipId, found.get(0));
                        done.run();
                    } else if (!a.isFinishing() && !a.isDestroyed()) {
                        choose(a, clip, found, done);
                    }
                });
            } catch (Exception e) {
                handler.post(() -> Toast.makeText(app, e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    static void apply(android.content.Context c, String clipId, Tmdb.Movie m) {
        Store.update(c, clipId, clip -> {
            // 영화 정보를 고르면 사용자가 입력한 값 대신 정보 기준으로 고정
            clip.info = m.json.toString();
            if (!m.title().isEmpty()) clip.title = m.title();
            clip.origTitle = m.json.optString("origTitle");
            if (!m.director().isEmpty()) clip.director = m.director();
            if (!m.year().isEmpty()) clip.year = m.year();
        });
    }

    private static void choose(Activity a, Store.Clip clip, List<Tmdb.Movie> movies, Runnable done) {
        int dp = (int) a.getResources().getDisplayMetrics().density;
        ArrayAdapter<Tmdb.Movie> adapter = new ArrayAdapter<Tmdb.Movie>(a, 0, movies) {
            @Override
            public View getView(int pos, View view, ViewGroup parent) {
                LinearLayout row = new LinearLayout(a);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(20 * dp, 8 * dp, 20 * dp, 8 * dp);
                ImageView iv = new ImageView(a);
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                iv.setBackgroundColor(0xFF2A2A2A);
                row.addView(iv, new LinearLayout.LayoutParams(54 * dp, 80 * dp));
                Tmdb.Movie m = getItem(pos);
                Images.load(iv, m.poster());

                LinearLayout text = new LinearLayout(a);
                text.setOrientation(LinearLayout.VERTICAL);
                text.setPadding(14 * dp, 0, 0, 0);
                TextView t = new TextView(a);
                t.setTextSize(16);
                t.setText(m.title() + (m.year().isEmpty() ? "" : " (" + m.year() + ")"));
                text.addView(t);
                TextView sub = new TextView(a);
                sub.setTextSize(13);
                sub.setTextColor(0xFF999999);
                sub.setText(m.json.optString("origTitle") + (m.director().isEmpty() ? "" : "\n감독 " + m.director()));
                text.addView(sub);
                row.addView(text);
                return row;
            }
        };
        new AlertDialog.Builder(a)
                .setTitle("'" + clip.title + "' 어떤 영화인가요?")
                .setAdapter(adapter, (d, which) -> {
                    apply(a, clip.id, movies.get(which));
                    done.run();
                })
                .setNegativeButton("선택 안 함", null)
                .show();
    }

    /** 영화 정보 보기 */
    public static void showInfo(Activity a, Store.Clip clip, Runnable findAgain) {
        if (clip.info == null) {
            new AlertDialog.Builder(a)
                    .setMessage("영화 정보가 없습니다")
                    .setPositiveButton("정보 찾기", (d, w) -> findAgain.run())
                    .setNegativeButton("닫기", null)
                    .show();
            return;
        }
        JSONObject o;
        try {
            o = new JSONObject(clip.info);
        } catch (Exception e) {
            return;
        }
        int dp = (int) a.getResources().getDisplayMetrics().density;
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, 0, 0, 16 * dp);

        String img = o.optString("backdrop", o.optString("poster", null));
        if (img != null) {
            ImageView iv = new ImageView(a) {
                @Override
                protected void onMeasure(int w, int h) {
                    int width = MeasureSpec.getSize(w);
                    setMeasuredDimension(width, width * 9 / 16);
                }
            };
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setBackgroundColor(0xFF2A2A2A);
            Images.load(iv, img);
            box.addView(iv);
        }

        LinearLayout text = new LinearLayout(a);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setPadding(24 * dp, 16 * dp, 24 * dp, 0);
        box.addView(text);

        String year = o.optString("year");
        line(text, o.optString("title") + (year.isEmpty() ? "" : " (" + year + ")"), 22, Color.WHITE, Typeface.BOLD);
        line(text, "원제  " + o.optString("origTitle"), 15, 0xFF999999, Typeface.NORMAL);

        StringBuilder meta = new StringBuilder(o.optString("genres"));
        int runtime = o.optInt("runtime");
        if (runtime > 0) meta.append(meta.length() > 0 ? " · " : "").append(runtime).append("분");
        String countries = o.optString("countries");
        if (!countries.isEmpty()) meta.append(meta.length() > 0 ? " · " : "").append(countries);
        line(text, meta.toString(), 14, 0xFFBBBBBB, Typeface.NORMAL);

        if (!o.optString("director").isEmpty()) line(text, "감독  " + o.optString("director"), 15, Color.WHITE, Typeface.NORMAL);
        if (!o.optString("cast").isEmpty()) line(text, "출연  " + o.optString("cast"), 15, Color.WHITE, Typeface.NORMAL);
        if (o.optInt("votes") > 0) {
            line(text, String.format(Locale.US, "TMDB ★ %.1f  (%,d명)", o.optDouble("vote"), o.optInt("votes")), 15, 0xFFFFC107, Typeface.NORMAL);
        }
        if (!o.optString("tagline").isEmpty()) line(text, "“" + o.optString("tagline") + "”", 15, 0xFFDDDDDD, Typeface.ITALIC);
        if (!o.optString("overview").isEmpty()) line(text, o.optString("overview"), 15, 0xFFDDDDDD, Typeface.NORMAL);
        line(text, "정보 제공: TMDB", 12, 0xFF777777, Typeface.NORMAL);

        ScrollView sv = new ScrollView(a);
        sv.addView(box);
        new AlertDialog.Builder(a)
                .setView(sv)
                .setPositiveButton("닫기", null)
                .show();
    }

    private static void line(LinearLayout box, String s, int size, int color, int style) {
        if (TextUtils.isEmpty(s)) return;
        int dp = (int) box.getResources().getDisplayMetrics().density;
        TextView t = new TextView(box.getContext());
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setTypeface(null, style);
        t.setPadding(0, 6 * dp, 0, 0);
        t.setLineSpacing(0, 1.15f);
        box.addView(t);
    }

    /**
     * 한 번만: 영화 정보가 있는 기존 클립의 감독 이름을 새 규칙(한국인만 한글)으로 다시 가져옴.
     * 키가 없거나 중간에 실패하면 다음 실행 때 다시 시도.
     */
    public static void refreshDirectorsOnce(Activity a, Runnable done) {
        android.content.SharedPreferences prefs = a.getSharedPreferences("settings", Activity.MODE_PRIVATE);
        String key = Tmdb.key(a);
        if (prefs.getInt("directorFix", 0) >= 2 || key.isEmpty() || refreshing) return;
        refreshing = true;
        android.content.Context app = a.getApplicationContext();
        pool.execute(() -> {
            try {
                for (Store.Clip c : Store.load(app)) {
                    if (c.info == null) continue;
                    int id = new JSONObject(c.info).optInt("tmdbId");
                    if (id == 0) continue;
                    Tmdb.Movie m = Tmdb.details(key, id);
                    Store.update(app, c.id, clip -> {
                        clip.info = m.json.toString();
                        if (!m.director().isEmpty()) clip.director = m.director();
                    });
                }
                prefs.edit().putInt("directorFix", 2).apply();
                handler.post(done);
            } catch (Exception ignored) {
            }
            refreshing = false;
        });
    }

    private static volatile boolean refreshing;

    /** 설정: TMDB 키 */
    public static void showSettings(Activity a, Runnable backup, Runnable restore) {
        int dp = (int) a.getResources().getDisplayMetrics().density;
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(24 * dp, 8 * dp, 24 * dp, 0);
        EditText key = new EditText(a);
        key.setHint("TMDB API Key");
        key.setSingleLine();
        key.setText(Tmdb.key(a));
        box.addView(key);
        TextView note = new TextView(a);
        note.setText("This product uses the TMDB API but is not endorsed or certified by TMDB.");
        note.setTextSize(12);
        note.setTextColor(0xFF888888);
        note.setPadding(0, 12 * dp, 0, 0);
        box.addView(note);

        TextView head = new TextView(a);
        head.setText("백업 / 복원");
        head.setTextSize(13);
        head.setTextColor(0xFF888888);
        head.setTypeface(null, Typeface.BOLD);
        head.setPadding(0, 24 * dp, 0, 4 * dp);
        box.addView(head);
        LinearLayout row = new LinearLayout(a);
        android.widget.Button out = new android.widget.Button(a);
        out.setText("파일로 백업");
        android.widget.Button in = new android.widget.Button(a);
        in.setText("백업에서 복원");
        row.addView(out, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(in, new LinearLayout.LayoutParams(0, -2, 1));
        box.addView(row);
        TextView bnote = new TextView(a);
        bnote.setText("클립, 채널, 별점, 시청 기록, 필터가 저장됩니다. (TMDB 키는 제외)");
        bnote.setTextSize(12);
        bnote.setTextColor(0xFF888888);
        box.addView(bnote);

        AlertDialog dlg = new AlertDialog.Builder(a)
                .setTitle("설정")
                .setView(box)
                .setPositiveButton("저장", (d, w) -> Tmdb.setKey(a, key.getText().toString()))
                .setNegativeButton("취소", null)
                .show();
        out.setOnClickListener(v -> {
            dlg.dismiss();
            backup.run();
        });
        in.setOnClickListener(v -> {
            dlg.dismiss();
            restore.run();
        });
    }
}
