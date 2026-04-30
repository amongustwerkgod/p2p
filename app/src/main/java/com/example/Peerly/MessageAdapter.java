package com.example.Peerly;

import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.*;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.MsgVH> {

    public interface OnMessageInteractionListener {
        void onMessageLongClick(Message message, int position);
        void onReactionClick(Message message, int position);
    }

    private static final int TYPE_SENT = 0, TYPE_RECV = 1;
    public final List<Message> messages = new ArrayList<>();
    private final String myUsername;
    private OnMessageInteractionListener interactionListener;
    private MediaPlayer currentAudioPlayer;

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

    // New helper to find message by UUID/ID
    public int getPositionByMsgId(String msgId) {
        if (msgId == null) return -1;
        for (int i = 0; i < messages.size(); i++) {
            if (msgId.equals(messages.get(i).key)) return i;
        }
        return -1;
    }

    public void updateMessage(int position, Message m) {
        if (position >= 0 && position < messages.size()) {
            messages.set(position, m);
            notifyItemChanged(position);
        }
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
        
        h.text.setVisibility(View.GONE);
        h.imageCard.setVisibility(View.GONE);
        h.audioContainer.setVisibility(View.GONE);

        if (m.type == Message.Type.IMAGE) {
            h.imageCard.setVisibility(View.VISIBLE);
            Glide.with(h.itemView.getContext())
                .load(m.mediaUrl)
                .transition(DrawableTransitionOptions.withCrossFade())
                .placeholder(R.drawable.bg_bubble_recv)
                .into(h.image);
            
            h.saveImageBtn.setOnClickListener(v -> saveImageToGallery(h.itemView.getContext(), m.mediaUrl));
        } else if (m.type == Message.Type.AUDIO) {
            h.audioContainer.setVisibility(View.VISIBLE);
            h.audioPlayBtn.setOnClickListener(v -> playAudio(h.itemView.getContext(), m.mediaUrl));
        } else {
            h.text.setVisibility(View.VISIBLE);
            h.text.setText(m.text);
        }

        if (h.sender != null) h.sender.setText(m.sender);

        if (m.reactions != null && !m.reactions.isEmpty()) {
            h.reactionContainer.setVisibility(View.VISIBLE);
            StringBuilder sb = new StringBuilder();
            Map<String, Integer> counts = new HashMap<>();
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
    }

    private void saveImageToGallery(Context context, Uri uri) {
        Glide.with(context)
                .asBitmap()
                .load(uri)
                .into(new CustomTarget<Bitmap>() {
                    @Override
                    public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                        try {
                            String filename = "Peerly_" + System.currentTimeMillis() + ".jpg";
                            OutputStream fos;
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                ContentValues values = new ContentValues();
                                values.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);
                                values.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
                                values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Peerly");
                                Uri imageUri = context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                                fos = context.getContentResolver().openOutputStream(imageUri);
                            } else {
                                String imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString() + "/Peerly";
                                File file = new File(imagesDir);
                                if (!file.exists()) file.mkdir();
                                File image = new File(imagesDir, filename);
                                fos = new FileOutputStream(image);
                            }
                            resource.compress(Bitmap.CompressFormat.JPEG, 100, fos);
                            fos.flush();
                            fos.close();
                            Toast.makeText(context, "Saved to Gallery", Toast.LENGTH_SHORT).show();
                        } catch (Exception e) {
                            Log.e("MessageAdapter", "Save failed", e);
                            Toast.makeText(context, "Save failed", Toast.LENGTH_SHORT).show();
                        }
                    }
                    @Override public void onLoadCleared(@Nullable Drawable placeholder) {}
                });
    }

    private void playAudio(Context context, Uri uri) {
        if (currentAudioPlayer != null) {
            currentAudioPlayer.release();
            currentAudioPlayer = null;
        }
        try {
            currentAudioPlayer = new MediaPlayer();
            currentAudioPlayer.setDataSource(context, uri);
            currentAudioPlayer.prepare();
            currentAudioPlayer.start();
            currentAudioPlayer.setOnCompletionListener(mp -> {
                mp.release();
                currentAudioPlayer = null;
            });
        } catch (Exception e) {
            Log.e("MessageAdapter", "Error playing audio", e);
            if (currentAudioPlayer != null) {
                currentAudioPlayer.release();
                currentAudioPlayer = null;
            }
        }
    }

    static class MsgVH extends RecyclerView.ViewHolder {
        TextView text, sender, reactionContainer;
        ImageView image;
        View imageCard, audioContainer;
        ImageButton audioPlayBtn, saveImageBtn;

        MsgVH(View v) {
            super(v);
            text   = v.findViewById(R.id.msgText);
            sender = v.findViewById(R.id.msgSender);
            image  = v.findViewById(R.id.msgImage);
            imageCard = v.findViewById(R.id.msgImageCard);
            reactionContainer = v.findViewById(R.id.reactionContainer);
            audioContainer = v.findViewById(R.id.audioContainer);
            audioPlayBtn = v.findViewById(R.id.audioPlayBtn);
            saveImageBtn = v.findViewById(R.id.saveImageBtn);
        }
    }
}
