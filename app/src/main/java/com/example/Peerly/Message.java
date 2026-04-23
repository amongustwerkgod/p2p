package com.example.Peerly;

import java.util.HashMap;
import java.util.Map;

public class Message {
    public enum Type { TEXT, IMAGE, AUDIO }

    public String key;
    public final String sender;
    public final String text;
    public final String mediaUrl;
    public final Type type;
    public final boolean isSent;
    public final long timestamp;
    public Map<String, String> reactions = new HashMap<>();

    public Message(String sender, String text, boolean isSent) {
        this(null, sender, text, null, Type.TEXT, isSent);
    }

    public Message(String key, String sender, String text, boolean isSent) {
        this(key, sender, text, null, Type.TEXT, isSent);
    }

    public Message(String sender, String mediaUrl, Type type, boolean isSent) {
        this(null, sender, null, mediaUrl, type, isSent);
    }

    public Message(String key, String sender, String text, String mediaUrl, Type type, boolean isSent) {
        this.key       = key;
        this.sender    = sender;
        this.text      = text;
        this.mediaUrl  = mediaUrl;
        this.type      = type;
        this.isSent    = isSent;
        this.timestamp = System.currentTimeMillis();
    }
}
