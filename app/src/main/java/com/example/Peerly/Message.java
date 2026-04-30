package com.example.Peerly;

import android.net.Uri;
import java.util.HashMap;
import java.util.Map;

public class Message {
    public enum Type { TEXT, IMAGE, AUDIO }

    public final String key; // Unique ID for P2P sync
    public final String sender;
    public final String text;
    public final Uri mediaUrl;
    public final Type type;
    public final boolean isSent;
    public final long timestamp;
    public final Map<String, String> reactions = new HashMap<>();

    public Message(String key, String sender, String text, Uri mediaUrl, Type type, boolean isSent, long timestamp) {
        this.key = key;
        this.sender = sender;
        this.text = text;
        this.mediaUrl = mediaUrl;
        this.type = type;
        this.isSent = isSent;
        this.timestamp = timestamp;
    }

    // Constructor for Firebase/WorldChat usage
    public Message(String key, String sender, String text, boolean isSent) {
        this(key, sender, text, null, Type.TEXT, isSent, System.currentTimeMillis());
    }
}
