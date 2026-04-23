package com.example.Peerly;

import android.view.*;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.MsgVH> {

    public interface OnMessageLongClickListener {
        void onMessageLongClick(Message message, int position);
    }

    private static final int TYPE_SENT = 0, TYPE_RECV = 1;
    private final List<Message> messages = new ArrayList<>();
    private final String myUsername;
    private OnMessageLongClickListener longClickListener;

    public MessageAdapter(String myUsername) { this.myUsername = myUsername; }

    public void setOnMessageLongClickListener(OnMessageLongClickListener listener) {
        this.longClickListener = listener;
    }

    public void addMessage(Message m) {
        messages.add(m);
        notifyItemInserted(messages.size() - 1);
    }

    public void removeMessage(int position) {
        if (position >= 0 && position < messages.size()) {
            messages.remove(position);
            notifyItemRemoved(position);
        }
    }

    public int getPositionOfMessage(String key) {
        if (key == null) return -1;
        for (int i = 0; i < messages.size(); i++) {
            if (key.equals(messages.get(i).key)) return i;
        }
        return -1;
    }

    @Override public int getItemViewType(int pos) {
        // Updated to use the isSent boolean directly from the Message object
        return messages.get(pos).isSent ? TYPE_SENT : TYPE_RECV;
    }

    @Override public int getItemCount() { return messages.size(); }

    @NonNull @Override
    public MsgVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layout = (viewType == TYPE_SENT)
            ? R.layout.item_message_sent
            : R.layout.item_message_recv;
        View v = LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);
        return new MsgVH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull MsgVH h, int pos) {
        Message m = messages.get(pos);
        h.text.setText(m.text);
        if (h.sender != null) h.sender.setText(m.sender);
        
        h.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null) {
                longClickListener.onMessageLongClick(m, h.getAdapterPosition());
                return true;
            }
            return false;
        });

        // Entrance animation
        h.itemView.setAlpha(0f);
        h.itemView.setScaleX(0.85f);
        h.itemView.setScaleY(0.85f);
        h.itemView.animate().alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(220).setStartDelay(0).start();
    }

    static class MsgVH extends RecyclerView.ViewHolder {
        TextView text, sender;
        MsgVH(View v) {
            super(v);
            text   = v.findViewById(R.id.msgText);
            sender = v.findViewById(R.id.msgSender); // null in sent layout
        }
    }
}
