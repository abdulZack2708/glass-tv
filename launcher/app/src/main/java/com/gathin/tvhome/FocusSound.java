package com.gathin.tvhome;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.SystemClock;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

final class FocusSound {
    private static SoundPool pool;
    private static int soundId;
    private static boolean ready;
    private static long lastAt;

    static void init(Context ctx) {
        if (pool != null) return;
        try {
            File f = new File(ctx.getCacheDir(), "tick.wav");
            if (!f.exists()) writeWav(f);
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            pool = new SoundPool.Builder().setMaxStreams(2).setAudioAttributes(attrs).build();
            pool.setOnLoadCompleteListener((p, id, status) -> ready = status == 0);
            soundId = pool.load(f.getPath(), 1);
        } catch (Exception ignored) {
            // No sound is better than no Home.
        }
    }

    static void tick() {
        if (!ready || pool == null) return;
        long now = SystemClock.uptimeMillis();
        if (now - lastAt < 40) return;
        lastAt = now;
        pool.play(soundId, 0.5f, 0.5f, 1, 0, 1f);
    }

    private static void writeWav(File f) throws Exception {
        int rate = 44100;
        int n = rate * 45 / 1000;
        ByteBuffer b = ByteBuffer.allocate(44 + n * 2).order(ByteOrder.LITTLE_ENDIAN);
        b.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36 + n * 2)
                .put("WAVE".getBytes(StandardCharsets.US_ASCII));
        b.put("fmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16)
                .putShort((short) 1).putShort((short) 1)
                .putInt(rate).putInt(rate * 2).putShort((short) 2).putShort((short) 16);
        b.put("data".getBytes(StandardCharsets.US_ASCII)).putInt(n * 2);
        for (int i = 0; i < n; i++) {
            double t = i / (double) rate;
            double env = Math.exp(-t * 120);
            double s = Math.sin(2 * Math.PI * 1500 * t) * 0.7 + Math.sin(2 * Math.PI * 3000 * t) * 0.3;
            b.putShort((short) (s * env * 12000));
        }
        FileOutputStream o = new FileOutputStream(f);
        try {
            o.write(b.array());
        } finally {
            o.close();
        }
    }
}
