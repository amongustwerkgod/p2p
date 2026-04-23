package com.example.Peerly;

public class Message {
    public String key;
    public final String sender;
    public final String text;
    public final boolean isSent;  // true = right bubble, false = left bubble
    public final long timestamp;

    public Message(String sender, String text, boolean isSent) {
        this(null, sender, text, isSent);
    }

    public Message(String key, String sender, String text, boolean isSent) {
        this.key       = key;
        this.sender    = sender;
        this.text      = text;
        this.isSent    = isSent;
        this.timestamp = System.currentTimeMillis();
    }
}
