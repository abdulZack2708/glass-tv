package com.gathin.tvhome;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

final class PrayerLine {
    private static final String ZONE = "SGR03"; // Klang & Kuala Langat
    private static final String[] KEYS = {"fajr", "dhuhr", "asr", "maghrib", "isha"};
    private static final String[] NAMES = {"Subuh", "Zohor", "Asar", "Maghrib", "Isyak"};
    private static final TimeZone KL = TimeZone.getTimeZone("Asia/Kuala_Lumpur");

    private final TextView view;
    private final SharedPreferences prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private JSONObject month;
    private boolean ticking;
    private boolean fetching;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            render();
            if (ticking) handler.postDelayed(this, 60000);
        }
    };

    private PrayerLine(Context app, TextView view) {
        this.view = view;
        this.prefs = app.getSharedPreferences("prayer", Context.MODE_PRIVATE);
    }

    static void install(Activity act) {
        try {
            View clock = act.findViewById(R.id.clock);
            ViewGroup row = (ViewGroup) clock.getParent();
            ViewGroup col = (ViewGroup) row.getParent();
            TextView tv = new TextView(act);
            tv.setSingleLine(true);
            tv.setIncludeFontPadding(false);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            tv.setTextColor(0x99FFFFFF);
            float den = act.getResources().getDisplayMetrics().density;
            tv.setPadding(0, (int) (8 * den), 0, 0);
            col.addView(tv, col.indexOfChild(row) + 1);
            new PrayerLine(act.getApplicationContext(), tv).attach();
        } catch (Exception ignored) {
            // Never let this feature break Home.
        }
    }

    private void attach() {
        loadCached();
        render();
        view.getViewTreeObserver().addOnWindowFocusChangeListener(hasFocus -> {
            handler.removeCallbacks(tick);
            ticking = hasFocus;
            if (hasFocus) {
                loadCached();
                handler.post(tick);
                fetchIfNeeded();
            }
        });
    }

    private void loadCached() {
        if (month != null) return;
        String json = prefs.getString("json", null);
        if (json == null) return;
        try { month = new JSONObject(json); } catch (Exception ignored) { }
    }

    private void fetchIfNeeded() {
        Calendar now = Calendar.getInstance(KL);
        final int y = now.get(Calendar.YEAR);
        final int m = now.get(Calendar.MONTH) + 1;
        if (month != null && month.optInt("year") == y && month.optInt("month_number") == m) return;
        if (fetching) return;
        fetching = true;
        new Thread(() -> {
            String body = null;
            HttpURLConnection c = null;
            try {
                URL u = new URL("https://api.waktusolat.app/v2/solat/" + ZONE + "?year=" + y + "&month=" + m);
                c = (HttpURLConnection) u.openConnection();
                c.setConnectTimeout(10000);
                c.setReadTimeout(10000);
                if (c.getResponseCode() == 200) {
                    BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = r.readLine()) != null) sb.append(line);
                    r.close();
                    body = sb.toString();
                }
            } catch (Exception ignored) {
            } finally {
                if (c != null) c.disconnect();
            }
            final String json = body;
            handler.post(() -> {
                fetching = false;
                if (json == null) return;
                try {
                    JSONObject o = new JSONObject(json);
                    if (o.optJSONArray("prayers") == null) return;
                    prefs.edit().putString("json", json).apply();
                    month = o;
                    render();
                } catch (Exception ignored) { }
            });
        }, "prayer-times").start();
    }

    private void render() {
        if (month == null) { view.setText(""); return; }
        Calendar now = Calendar.getInstance(KL);
        if (month.optInt("year") != now.get(Calendar.YEAR)
                || month.optInt("month_number") != now.get(Calendar.MONTH) + 1) {
            view.setText("");
            return;
        }
        int day = now.get(Calendar.DAY_OF_MONTH);
        JSONArray arr = month.optJSONArray("prayers");
        JSONObject today = null;
        for (int i = 0; arr != null && i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o != null && o.optInt("day") == day) { today = o; break; }
        }
        if (today == null) { view.setText(""); return; }
        long nowSec = System.currentTimeMillis() / 1000L;
        SimpleDateFormat f = new SimpleDateFormat("h:mm", Locale.US);
        f.setTimeZone(KL);
        int next = -1;
        for (int i = 0; i < KEYS.length; i++) {
            if (today.optLong(KEYS[i]) > nowSec) { next = i; break; }
        }
        SpannableStringBuilder sb = new SpannableStringBuilder();
        for (int i = 0; i < KEYS.length; i++) {
            if (i > 0) sb.append("   ");
            int start = sb.length();
            sb.append(NAMES[i]).append(' ')
              .append(f.format(new Date(today.optLong(KEYS[i]) * 1000L)));
            sb.setSpan(new ForegroundColorSpan(i == next ? Color.WHITE : 0x99FFFFFF),
                    start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        view.setText(sb);
    }
}
