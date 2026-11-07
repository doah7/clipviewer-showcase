package com.personal.clipviewer;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 감독 페이지: 왼쪽 인물 정보, 오른쪽 감독한 영화(필모그래피). 내 클립에 있는 영화는 표시 */
public class DirectorActivity extends Activity {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<JSONObject> films = new ArrayList<>();
    private final Map<Integer, Store.Clip> owned = new HashMap<>();
    private String name;
    private int tmdbId;
    private FilmAdapter adapter;
    private TextView status, meta, bio;
    private ImageView photo;
    private int dp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        dp = (int) getResources().getDisplayMetrics().density;
        name = getIntent().getStringExtra("name");
        tmdbId = getIntent().getIntExtra("tmdbId", 0);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setBackgroundColor(0xFF111111);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(8 * dp, 8 * dp, 20 * dp, 8 * dp);
        ImageView back = new ImageView(this);
        back.setImageResource(R.drawable.ic_back);
        back.setPadding(12 * dp, 12 * dp, 12 * dp, 12 * dp);
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(48 * dp, 48 * dp));
        TextView title = new TextView(this);
        title.setText(name);
        title.setTextSize(22);
        title.setTextColor(Color.WHITE);
        title.setPadding(8 * dp, 0, 0, 0);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        col.addView(header);
        View line = new View(this);
        line.setBackgroundColor(0xFF2E2E2E);
        col.addView(line, new LinearLayout.LayoutParams(-1, Math.max(1, dp)));

        LinearLayout body = new LinearLayout(this);
        col.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));

        // 왼쪽 1/3: 사진, 출생, 소개
        ScrollView left = new ScrollView(this);
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(24 * dp, 20 * dp, 20 * dp, 24 * dp);
        left.addView(info);
        photo = new ImageView(this) {
            @Override
            protected void onMeasure(int w, int h) {
                int width = Math.min(MeasureSpec.getSize(w), 240 * dp);
                setMeasuredDimension(width, width * 3 / 2);
            }
        };
        photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        photo.setBackgroundColor(0xFF2A2A2A);
        info.addView(photo);
        meta = text(info, 14, 0xFFAAAAAA);
        bio = text(info, 15, 0xFFDDDDDD);
        bio.setLineSpacing(0, 1.2f);
        body.addView(left, new LinearLayout.LayoutParams(0, -1, 1));

        View vline = new View(this);
        vline.setBackgroundColor(0xFF2E2E2E);
        body.addView(vline, new LinearLayout.LayoutParams(Math.max(1, dp), -1));

        // 오른쪽 2/3: 필모그래피
        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        status = new TextView(this);
        status.setTextColor(0xFF888888);
        status.setTextSize(14);
        status.setPadding(16 * dp, 12 * dp, 16 * dp, 4 * dp);
        right.addView(status);
        GridView grid = new GridView(this);
        grid.setColumnWidth(150 * dp);
        grid.setNumColumns(GridView.AUTO_FIT);
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setHorizontalSpacing(12 * dp);
        grid.setVerticalSpacing(18 * dp);
        grid.setPadding(16 * dp, 8 * dp, 16 * dp, 16 * dp);
        grid.setClipToPadding(false);
        adapter = new FilmAdapter();
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((p, v, pos, id) -> onFilm(films.get(pos)));
        right.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1));
        body.addView(right, new LinearLayout.LayoutParams(0, -1, 2));

        setContentView(col);
        load();
    }

    private TextView text(LinearLayout box, int size, int color) {
        TextView t = new TextView(this);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setPadding(0, 12 * dp, 0, 0);
        box.addView(t);
        return t;
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadOwned();
        adapter.notifyDataSetChanged();
    }

    private void loadOwned() {
        owned.clear();
        for (Store.Clip c : Store.load(this)) {
            if (c.info == null) continue;
            try {
                owned.put(new JSONObject(c.info).optInt("tmdbId"), c);
            } catch (Exception ignored) {
            }
        }
    }

    private void load() {
        String key = Tmdb.key(this);
        if (tmdbId == 0 || key.isEmpty()) {
            // TMDB 정보가 없는 감독: 내 클립만
            showMyClipsOnly();
            return;
        }
        status.setText("불러오는 중...");
        new Thread(() -> {
            try {
                Tmdb.Person p = Tmdb.person(key, tmdbId, name);
                handler.post(() -> {
                    if (isFinishing()) return;
                    Images.load(photo, p.photo);
                    String m = (p.birthday.length() >= 4 ? p.birthday.substring(0, 4) + "년생" : "")
                            + (p.birthday.length() >= 4 && !p.birth.isEmpty() ? " · " : "") + p.birth;
                    meta.setText(m);
                    meta.setVisibility(m.isEmpty() ? View.GONE : View.VISIBLE);
                    bio.setText(p.bio);
                    films.clear();
                    films.addAll(p.films);
                    adapter.notifyDataSetChanged();
                    updateStatus();
                });
            } catch (Exception e) {
                handler.post(() -> {
                    status.setText("불러오지 못했습니다: " + e.getMessage());
                    showMyClipsOnly();
                });
            }
        }).start();
    }

    private void showMyClipsOnly() {
        photo.setVisibility(View.GONE);
        meta.setText("TMDB 정보가 없는 감독입니다");
        films.clear();
        for (Store.Clip c : Store.load(this)) {
            if (!ClipFilter.directorsOf(c).contains(name)) continue;
            JSONObject f = new JSONObject();
            try {
                f.put("clipId", c.id);
                f.put("title", c.title);
                f.put("year", c.year);
                if (c.displayThumb() != null) f.put("poster", c.displayThumb());
            } catch (Exception ignored) {
            }
            films.add(f);
        }
        adapter.notifyDataSetChanged();
        status.setText("내 영화 " + films.size() + "편");
    }

    private void updateStatus() {
        int mine = 0;
        for (JSONObject f : films) if (owned.containsKey(f.optInt("id"))) mine++;
        status.setText("감독 작품 " + films.size() + "편 · 내 클립 " + mine + "편");
    }

    private Store.Clip clipOf(JSONObject f) {
        if (f.has("clipId")) return Store.get(this, f.optString("clipId"));
        return owned.get(f.optInt("id"));
    }

    private void onFilm(JSONObject f) {
        Store.Clip mine = clipOf(f);
        if (mine != null) {
            MovieUi.showInfo(this, mine, () -> {
            });
            return;
        }
        String t = f.optString("title") + (f.optString("year").isEmpty() ? "" : " (" + f.optString("year") + ")");
        new AlertDialog.Builder(this)
                .setTitle("클립 목록에 추가할까요?")
                .setMessage(t + "\n\n링크 없이 영화 정보만 추가됩니다. 나중에 ⋮ → 수정에서 링크를 넣을 수 있습니다.")
                .setPositiveButton("추가", (d, w) -> addFilm(f))
                .setNegativeButton("취소", null)
                .show();
    }

    private void addFilm(JSONObject f) {
        Store.Clip clip = new Store.Clip();
        clip.id = UUID.randomUUID().toString();
        clip.title = f.optString("title");
        clip.origTitle = f.optString("origTitle");
        clip.year = f.optString("year");
        clip.url = "";
        List<Store.Clip> list = Store.load(this);
        list.add(0, clip);
        Store.save(this, list);
        String key = Tmdb.key(this);
        // 어떤 영화인지 이미 알고 있으므로 고르는 과정 없이 바로 정보를 붙임
        new Thread(() -> {
            try {
                Tmdb.Movie m = Tmdb.details(key, f.optInt("id"));
                MovieUi.apply(this, clip.id, m);
            } catch (Exception ignored) {
            }
            handler.post(() -> {
                loadOwned();
                adapter.notifyDataSetChanged();
                updateStatus();
                Toast.makeText(this, "추가했습니다", Toast.LENGTH_SHORT).show();
            });
        }).start();
    }

    private class FilmAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return films.size();
        }

        @Override
        public Object getItem(int i) {
            return films.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int pos, View view, ViewGroup parent) {
            LinearLayout cell;
            if (view == null) {
                cell = new LinearLayout(DirectorActivity.this);
                cell.setOrientation(LinearLayout.VERTICAL);
                FrameLayout frame = new FrameLayout(DirectorActivity.this);
                ImageView iv = new ImageView(DirectorActivity.this) {
                    @Override
                    protected void onMeasure(int w, int h) {
                        int width = MeasureSpec.getSize(w);
                        setMeasuredDimension(width, width * 3 / 2);
                    }
                };
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                iv.setBackgroundColor(0xFF2A2A2A);
                frame.addView(iv, new FrameLayout.LayoutParams(-1, -2));
                TextView mark = new TextView(DirectorActivity.this);
                mark.setText("✓ 내 클립");
                mark.setTextSize(12);
                mark.setTextColor(Color.WHITE);
                mark.setPadding(8 * dp, 3 * dp, 8 * dp, 3 * dp);
                GradientDrawable mbg = new GradientDrawable();
                mbg.setCornerRadius(12 * dp);
                mbg.setColor(0xE643A047);
                mark.setBackground(mbg);
                FrameLayout.LayoutParams mlp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
                mlp.setMargins(0, 6 * dp, 6 * dp, 0);
                frame.addView(mark, mlp);
                cell.addView(frame);
                TextView t = new TextView(DirectorActivity.this);
                t.setTextColor(Color.WHITE);
                t.setTextSize(14);
                t.setMaxLines(2);
                t.setEllipsize(TextUtils.TruncateAt.END);
                t.setPadding(2 * dp, 6 * dp, 2 * dp, 0);
                cell.addView(t);
                TextView y = new TextView(DirectorActivity.this);
                y.setTextColor(0xFF999999);
                y.setTextSize(12);
                y.setPadding(2 * dp, 2 * dp, 2 * dp, 0);
                cell.addView(y);
            } else {
                cell = (LinearLayout) view;
            }
            FrameLayout frame = (FrameLayout) cell.getChildAt(0);
            ImageView iv = (ImageView) frame.getChildAt(0);
            TextView mark = (TextView) frame.getChildAt(1);
            TextView t = (TextView) cell.getChildAt(1);
            TextView y = (TextView) cell.getChildAt(2);

            JSONObject f = films.get(pos);
            Images.load(iv, f.optString("poster", null));
            boolean mine = clipOf(f) != null;
            mark.setVisibility(mine ? View.VISIBLE : View.GONE);
            // 내 클립이 아닌 작품은 살짝 어둡게
            iv.setAlpha(mine ? 1f : 0.6f);
            t.setText(f.optString("title"));
            t.setTypeface(null, mine ? Typeface.BOLD : Typeface.NORMAL);
            y.setText(f.optString("year"));
            return cell;
        }
    }
}
