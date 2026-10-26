package com.gathin.tvhome;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.util.Locale;

final class StartupVoice {
    static void say(Context ctx, String text) {
        try {
            final TextToSpeech[] holder = new TextToSpeech[1];
            holder[0] = new TextToSpeech(ctx.getApplicationContext(), status -> {
                final TextToSpeech t = holder[0];
                if (status != TextToSpeech.SUCCESS || t == null) return;
                t.setLanguage(Locale.US);
                t.setPitch(0.4f);
                t.setSpeechRate(0.8f);
                t.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String id) { }
                    @Override public void onDone(String id) { t.shutdown(); }
                    @Override public void onError(String id) { t.shutdown(); }
                });
                t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ready");
            });
        } catch (Exception ignored) {
            // Never let a sound break Home.
        }
    }
}
