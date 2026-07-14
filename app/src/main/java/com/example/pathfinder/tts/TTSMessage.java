package com.example.pathfinder.tts;

/**
 * An auxiliary object to encapsulate a message String and its associated Priority.
 */
public class TTSMessage {

    /**
     * Defines the priority levels for a TTS message.
     * A message with a higher priority can interrupt a message with a lower priority.
     */
    public enum Priority {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }

    private final String text;
    private final Priority priority;

    public TTSMessage(String text, Priority priority) {
        this.text = text;
        this.priority = priority;
    }

    public String getText() {
        return text;
    }

    public Priority getPriority() {
        return priority;
    }

    // Optional: for easier logging
    @Override
    public String toString() {
        return "TTSMessage{" +
                "text='" + text + '\'' +
                ", priority=" + priority +
                '}';
    }
}
