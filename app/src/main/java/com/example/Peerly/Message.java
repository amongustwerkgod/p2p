// ─── Message.java ──────────────────────────────────────────────────────────
package com.example.p2p;

public class Message {
    public final String sender;
    public final String text;
    public final boolean isSent;  // true = right bubble, false = left bubble
    public final long timestamp;

    public Message(String sender, String text, boolean isSent) {
        this.sender    = sender;
        this.text      = text;
        this.isSent    = isSent;
        this.timestamp = System.currentTimeMillis();
    }
}
