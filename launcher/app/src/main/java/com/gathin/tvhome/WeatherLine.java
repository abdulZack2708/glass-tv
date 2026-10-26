package com.gathin.tvhome;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

final class WeatherLine {
    private static final String API = "https://api.open-meteo.com/v1/forecast"
            + "?latitude=3.0449&longitude=101.4455&current=temperature_2m,weather_code"
            + "&timezone=Asia%2FKuala_Lumpur";
    private static final long REFRESH_MS = 30L * 60 * 1000;
    private static final long STALE_MS = 3L * 60 * 60 * 1000;

    private final TextView view;
    private final SharedPreferences prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean fetching;

    private WeatherLine(Context app, TextView view) {
        this.view = view;
        this.prefs = app.getSharedPreferences("weather", Context.MODE_PRIVATE);
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
            tv.setTextColor(0xCCFFFFFF);
            float den = act.getResources().getDisplayMetrics().density;
            tv.setPadding(0, (int) (8 * den), 0, 0);
            col.addView(tv, col.indexOfChild(row) + 1);
            new WeatherLine(act.getApplicationContext(), tv).attach();
        } catch (Exception ignored) {
            // Never let this feature break Home.
        }
    }

    private void attach() {
        render();
        view.getViewTreeObserver().addOnWindowFocusChangeListener(hasFocus -> {
            if (hasFocus) {
                render();
                fetchIfDue();
            }
        });
    }

    private void fetchIfDue() {
        long at = prefs.getLong("at", 0);
        if (fetching || System.currentTimeMillis() - at < REFRESH_MS) return;
        fetching = true;
        new Thread(() -> {
            String body = null;
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) new URL(API).openConnection();
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
                    JSONObject cur = new JSONObject(json).getJSONObject("current");
                    prefs.edit()
                            .putFloat("temp", (float) cur.getDouble("temperature_2m"))
                            .putInt("code", cur.getInt("weather_code"))
                            .putLong("at", System.currentTimeMillis())
                            .apply();
                    render();
                } catch (Exception ignored) { }
            });
        }, "weather").start();
    }

    private void render() {
        long at = prefs.getLong("at", 0);
        if (at == 0 || System.currentTimeMillis() - at > STALE_MS) {
            view.setText("");
            return;
        }
        int t = Math.round(prefs.getFloat("temp", 0f));
        view.setText("Klang   " + t + "\u00B0C   " + describe(prefs.getInt("code", -1)));
    }

    private static String describe(int c) {
        if (c == 0) return "Cerah";
        if (c >= 1 && c <= 2) return "Sebahagian berawan";
        if (c == 3) return "Mendung";
        if (c == 45 || c == 48) return "Berkabus";
        if (c >= 51 && c <= 57) return "Hujan renyai";
        if (c >= 61 && c <= 67) return "Hujan";
        if (c >= 80 && c <= 82) return "Hujan lebat";
        if (c >= 95) return "Ribut petir";
        return "";
    }
}
