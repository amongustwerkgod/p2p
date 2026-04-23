package com.example.Peerly;

import android.view.*;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.MsgVH> {

    public interface OnMessageInteractionListener {
        void onMessageLongClick(Message message, int position);
        void onReactionClick(Message message, int position);
    }

    private static final int TYPE_SENT = 0, TYPE_RECV = 1;
    private final List<Message> messages = new ArrayList<>();
    private final String myUsername;
    private OnMessageInteractionListener interactionListener;

    public MessageAdapter(String myUsername) { this.myUsername = myUsername; }

    public void setOnMessageInteractionListener(OnMessageInteractionListener listener) {
        this.interactionListener = listener;
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
        
        if (m.type == Message.Type.IMAGE) {
            h.text.setVisibility(View.GONE);
            h.imageCard.setVisibility(View.VISIBLE);
            Glide.with(h.itemView.getContext())
                .load(m.imageUrl)
                .transition(DrawableTransitionOptions.withCrossFade())
                .placeholder(R.drawable.bg_bubble_recv)
                .into(h.image);
        } else {
            h.text.setVisibility(View.VISIBLE);
            h.imageCard.setVisibility(View.GONE);
            h.text.setText(m.text);
        }

        if (h.sender != null) h.sender.setText(m.sender);

        // Reactions logic
        if (m.reactions != null && !m.reactions.isEmpty()) {
            h.reactionContainer.setVisibility(View.VISIBLE);
            StringBuilder sb = new StringBuilder();
            Map<String, Integer> counts = new java.util.HashMap<>();
            for (String r : m.reactions.values()) {
                counts.put(r, counts.getOrDefault(r, 0) + 1);
            }
            for (Map.Entry<String, Integer> entry : counts.entrySet()) {
                sb.append(entry.getKey()).append(" ").append(entry.getValue()).append("  ");
            }
            h.reactionContainer.setText(sb.toString().trim());
        } else {
            h.reactionContainer.setVisibility(View.GONE);
        }
        
        h.itemView.setOnLongClickListener(v -> {
            if (interactionListener != null) {
                interactionListener.onMessageLongClick(m, h.getAdapterPosition());
                return true;
            }
            return false;
        });

        if (h.reactionContainer != null) {
            h.reactionContainer.setOnClickListener(v -> {
                if (interactionListener != null) {
                    interactionListener.onReactionClick(m, h.getAdapterPosition());
                }
            });
        }

        // Entrance animation
        h.itemView.setAlpha(0f);
        h.itemView.setScaleX(0.9f);
        h.itemView.setScaleY(0.9f);
        h.itemView.animate().alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(200).start();
    }

    static class MsgVH extends RecyclerView.ViewHolder {
        TextView text, sender, reactionContainer;
        ImageView image;
        View imageCard;

        MsgVH(View v) {
            super(v);
            text   = v.findViewById(R.id.msgText);
            sender = v.findViewById(R.id.msgSender);
            image  = v.findViewById(R.id.msgImage);
            imageCard = v.findViewById(R.id.msgImageCard);
            reactionContainer = v.findViewById(R.id.reactionContainer);
        }
    }
}
