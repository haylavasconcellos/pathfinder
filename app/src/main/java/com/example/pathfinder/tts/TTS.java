package com.example.pathfinder.tts;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.Locale;

public class TTS implements TTSInterface, TextToSpeech.OnInitListener {
    public static final String TAG = "TTS";

    public static final int SUCCESS = 0;
    public static final int NOT_INITIALIZED = -1;
    public static final int SPEAK_FAILED = -2;
    public static final int TEXT_TOO_LONG = 1;
    public static final int TEXT_EMPTY = 2;
    public static final int LOWER_PRIORITY = 3;

    // Store the last spoken TTSMessage object. Initialize with a default SAFE message.
    private TTSMessage lastSpokenMessage = new TTSMessage("", TTSMessage.Priority.LOW);

    private final TextToSpeech tts;
    private final MutableLiveData<Boolean> isInitialized = new MutableLiveData<>(false);

    public TTS(Context context) {
        tts = new TextToSpeech(context, this);
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            // Set language to Brazilian Portuguese
            int result = tts.setLanguage(Locale.forLanguageTag("pt-BR"));
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                // Language not supported
                Log.e(TAG, "Language not supported: pt-BR");
                isInitialized.postValue(false);
            } else {
                Log.i(TAG, "Initialization successful");
                isInitialized.postValue(true);
            }
        } else {
            Log.e(TAG, "Initialization failed");
            isInitialized.postValue(false);
        }
    }

    /**
     * Speaks the given message if its priority is higher than the currently speaking message.
     * @param message The TTSMessage object containing the text and priority.
     * @return A status code indicating success or failure.
     */
    @Override
    public int speak(TTSMessage message) {
        Log.d(TAG, "speak called with: " + message.toString());

        if (message == null || message.getText() == null || message.getText().isEmpty()) return TEXT_EMPTY;
        if (message.getText().length() > TextToSpeech.getMaxSpeechInputLength()) return TEXT_TOO_LONG;
        if (Boolean.FALSE.equals(isInitialized.getValue())) return NOT_INITIALIZED;

        // --- PRIORITY LOGIC ---
        if (tts.isSpeaking()) {
            // A message can only interrupt if its priority is strictly higher.
            // We use ordinal() because enums are ordered from LOW (0) to CRITICAL (3).
            if (message.getPriority().ordinal() <= lastSpokenMessage.getPriority().ordinal()) {
                Log.d(TAG, "New message priority (" + message.getPriority() + ") is not higher than current (" + lastSpokenMessage.getPriority() + "). Ignoring.");
                return LOWER_PRIORITY;
            }
            Log.d(TAG, "New message priority (" + message.getPriority() + ") is higher. Interrupting current speech.");
        }

        // Store the new message that is about to be spoken.
        this.lastSpokenMessage = message;

        // Use QUEUE_FLUSH to stop any current speech and start the new one.
        int result = tts.speak(message.getText(), TextToSpeech.QUEUE_FLUSH, null, null);
        if (result == TextToSpeech.ERROR) {
            return SPEAK_FAILED;
        }

        return SUCCESS;
    }

    public int repeatLastAlert() {
        return speak(lastSpokenMessage);
    }

    public void stop() {
        tts.stop();
        // Reset the priority when manually stopped, so the next message can play.
        lastSpokenMessage = new TTSMessage("", TTSMessage.Priority.LOW);
    }

    public LiveData<Boolean> isInitialized() {
        return isInitialized;
    }

    public void shutdown() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
    }
}
