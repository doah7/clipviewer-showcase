package com.personal.clipviewer;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** 오른쪽 검색 패널: 검색창 / 정렬 / 시청 상태 / 감독 / 장르 / 연대 / 국가 */
public class FilterPanel extends ScrollView {

    public interface Listener {
        void onChange();
    }

    private static final String[] STATUS_NAMES = {"시청 전", "시청 중", "시청 완료"};
    private static final int[] STATUS_COLORS = {0xFF9E9E9E, 0xFF43A047, 0xFFE53935};

    private final ClipFilter filter;
    private final Listener listener;
    private final EditText search;
    private final LinearLayout body;
    private final int dp;

    public FilterPanel(Context c, ClipFilter filter, Listener listener) {
        super(c);
        this.filter = filter;
        this.listener = listener;
        dp = (int) c.getResources().getDisplayMetrics().density;
        setBackgroundColor(0xFF181818);

        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(16 * dp, 12 * dp, 16 * dp, 24 * dp);
        addView(col);

        // 검색창은 다시 그리지 않음 (입력 중 커서가 튀지 않도록)
        search = new EditText(c);
        search.setHint("제목, 원제, 감독 검색");
        search.setSingleLine();
        search.setTextSize(15);
        search.setText(filter.query);
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int d) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int d) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (s.toString().equals(filter.query)) return;
                filter.query = s.toString();
                listener.onChange();
            }
        });
        col.addView(search);

        body = new LinearLayout(c);
        body.setOrientation(LinearLayout.VERTICAL);
        col.addView(body);
    }

    /** 태그 후보와 선택 상태를 다시 그림. shown = 지금 보이는 클립 수 */
    public void render(List<Store.Clip> all, int shown) {
        if (!search.getText().toString().equals(filter.query)) search.setText(filter.query);
        body.removeAllViews();

        // 정렬 (하나만)
        FlowLayout sorts = section("정렬");
        for (int i = 0; i < ClipFilter.SORT_KEYS.length; i++) {
            String key = ClipFilter.SORT_KEYS[i];
            boolean on = key.equals(filter.sort);
            String label = ClipFilter.SORT_NAMES[i] + (on ? (filter.asc ? " ▲" : " ▼") : "");
            sorts.addView(chip(label, null, on, 0, v -> {
                filter.pickSort(key);
                listener.onChange();
            }));
        }

        FlowLayout st = section("시청 상태");
        for (int i = 0; i < 3; i++) {
            int s = i;
            st.addView(chip(STATUS_NAMES[i], null, filter.status.contains(i), STATUS_COLORS[i], v -> {
                if (!filter.status.remove(s)) filter.status.add(s);
                listener.onChange();
            }));
        }

        tags("감독", ClipFilter.count(all, "director", 2, 10, filter.directors), filter.directors);
        tags("장르", ClipFilter.count(all, "genre", 1, 30, filter.genres), filter.genres);
        tags("연대", ClipFilter.count(all, "decade", 1, 30, filter.decades), filter.decades);
        tags("국가", ClipFilter.count(all, "country", 1, 12, filter.countries), filter.countries);

        LinearLayout foot = new LinearLayout(getContext());
        foot.setGravity(Gravity.CENTER_VERTICAL);
        foot.setPadding(0, 20 * dp, 0, 0);
        TextView count = new TextView(getContext());
        count.setText(shown + " / " + all.size() + "개");
        count.setTextColor(0xFF999999);
        count.setTextSize(14);
        foot.addView(count, new LinearLayout.LayoutParams(0, -2, 1));
        foot.addView(chip("초기화", null, false, 0, v -> {
            filter.clear();
            listener.onChange();
        }));
        body.addView(foot);
    }

    private void tags(String title, Map<String, Integer> counts, Set<String> selected) {
        if (counts.isEmpty()) return;
        FlowLayout box = section(title);
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            String name = e.getKey();
            box.addView(chip(name, String.valueOf(e.getValue()), selected.contains(name), 0, v -> {
                if (!selected.remove(name)) selected.add(name);
                listener.onChange();
            }));
        }
    }

    private FlowLayout section(String title) {
        TextView t = new TextView(getContext());
        t.setText(title);
        t.setTextSize(13);
        t.setTextColor(0xFF888888);
        t.setTypeface(null, Typeface.BOLD);
        t.setPadding(0, 18 * dp, 0, 8 * dp);
        body.addView(t);
        FlowLayout box = new FlowLayout(getContext(), 6 * dp);
        body.addView(box);
        return box;
    }

    /** color가 0이면 흰/회색 기본 태그, 아니면 그 색의 태그 */
    private TextView chip(String text, String count, boolean on, int color, OnClickListener click) {
        TextView c = new TextView(getContext());
        int fg = on ? (color != 0 ? Color.WHITE : 0xFF111111) : (color != 0 ? color : 0xFFCCCCCC);
        if (count != null) {
            SpannableString s = new SpannableString(text + " " + count);
            s.setSpan(new ForegroundColorSpan(on ? 0xFF666666 : 0xFF777777), text.length(), s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (on && color != 0) s.setSpan(new ForegroundColorSpan(0xFFDDDDDD), text.length(), s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            c.setText(s);
        } else {
            c.setText(text);
        }
        c.setTextSize(14);
        c.setTextColor(fg);
        c.setPadding(12 * dp, 6 * dp, 12 * dp, 6 * dp);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(16 * dp);
        int base = color != 0 ? color : 0xFFE0E0E0;
        bg.setStroke(Math.max(1, dp), on ? base : (color != 0 ? color : 0xFF555555));
        bg.setColor(on ? base : Color.TRANSPARENT);
        c.setBackground(bg);
        c.setOnClickListener(click);
        return c;
    }
}
