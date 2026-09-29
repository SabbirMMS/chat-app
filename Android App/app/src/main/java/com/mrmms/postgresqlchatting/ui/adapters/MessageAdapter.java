package com.mrmms.postgresqlchatting.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.mrmms.postgresqlchatting.R;
import com.mrmms.postgresqlchatting.network.models.Message;
import com.mrmms.postgresqlchatting.network.models.User;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int VIEW_TYPE_ME = 1;
    private static final int VIEW_TYPE_OTHER = 2;

    private final List<Message> messages = new ArrayList<>();
    private final String currentUserId;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());
    private final SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);

    public MessageAdapter(String currentUserId) {
        this.currentUserId = currentUserId;
    }

    public void setMessages(List<Message> newMessages) {
        this.messages.clear();
        if (newMessages != null) {
            this.messages.addAll(newMessages);
        }
        notifyDataSetChanged();
    }

    public void addMessage(Message message) {
        if (message == null) return;
        // Prevent duplicate messages
        for (Message m : messages) {
            if (m.getId() != null && m.getId().equals(message.getId())) {
                return;
            }
        }
        messages.add(message);
        notifyItemInserted(messages.size() - 1);
    }

    public void markMessageSeen(String messageId, User user) {
        if (messageId == null || user == null) return;
        for (int i = 0; i < messages.size(); i++) {
            Message m = messages.get(i);
            if (messageId.equals(m.getId())) {
                m.addSeenUser(user);
                notifyItemChanged(i);
                break;
            }
        }
    }

    public void markAllSeenByUser(User user) {
        if (user == null || user.getId() == null) return;
        boolean changed = false;
        for (Message m : messages) {
            if (m.getSender() != null && !user.getId().equals(m.getSender().getId())) {
                m.addSeenUser(user);
                changed = true;
            }
        }
        if (changed) {
            notifyDataSetChanged();
        }
    }

    @Override
    public int getItemViewType(int position) {
        Message msg = messages.get(position);
        if (msg.getSender() != null && currentUserId != null && currentUserId.equals(msg.getSender().getId())) {
            return VIEW_TYPE_ME;
        }
        return VIEW_TYPE_OTHER;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == VIEW_TYPE_ME) {
            View view = inflater.inflate(R.layout.item_message_me, parent, false);
            return new MeViewHolder(view);
        } else {
            View view = inflater.inflate(R.layout.item_message_other, parent, false);
            return new OtherViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Message msg = messages.get(position);
        String formattedTime = formatTime(msg.getCreatedAt());
        String seenText = buildSeenText(msg);

        if (holder instanceof MeViewHolder) {
            MeViewHolder meHolder = (MeViewHolder) holder;
            meHolder.tvContent.setText(msg.getContent());
            meHolder.tvTime.setText(formattedTime);

            if (!seenText.isEmpty()) {
                meHolder.tvSeenBy.setText("✓ " + seenText);
                meHolder.tvSeenBy.setVisibility(View.VISIBLE);
            } else {
                meHolder.tvSeenBy.setVisibility(View.GONE);
            }
        } else if (holder instanceof OtherViewHolder) {
            OtherViewHolder otherHolder = (OtherViewHolder) holder;
            otherHolder.tvContent.setText(msg.getContent());
            otherHolder.tvTime.setText(formattedTime);
            String senderName = (msg.getSender() != null && msg.getSender().getUsername() != null)
                    ? msg.getSender().getUsername()
                    : "User";
            otherHolder.tvSender.setText(senderName);

            if (!seenText.isEmpty()) {
                otherHolder.tvSeenBy.setText(seenText);
                otherHolder.tvSeenBy.setVisibility(View.VISIBLE);
            } else {
                otherHolder.tvSeenBy.setVisibility(View.GONE);
            }
        }
    }

    private String buildSeenText(Message msg) {
        if (msg.getSeenBy() == null || msg.getSeenBy().isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (User u : msg.getSeenBy()) {
            if (msg.getSender() != null && u.getId() != null && u.getId().equals(msg.getSender().getId())) {
                continue; // sender already read their own
            }
            if (count > 0) sb.append(", ");
            sb.append(u.getUsername() != null ? u.getUsername() : "user");
            count++;
        }
        if (count == 0) return "";
        return "Seen by " + sb.toString();
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    private String formatTime(String rawDate) {
        if (rawDate == null) return "";
        try {
            String clean = rawDate.replace("Z", "+00:00");
            if (clean.length() > 19) {
                clean = clean.substring(0, 19);
            }
            Date date = isoFormat.parse(clean);
            if (date != null) {
                return timeFormat.format(date);
            }
        } catch (Exception e) {
            // fallback
        }
        return "";
    }

    static class MeViewHolder extends RecyclerView.ViewHolder {
        final TextView tvContent;
        final TextView tvTime;
        final TextView tvSeenBy;

        MeViewHolder(@NonNull View itemView) {
            super(itemView);
            tvContent = itemView.findViewById(R.id.tvMessageContentMe);
            tvTime = itemView.findViewById(R.id.tvMessageTimeMe);
            tvSeenBy = itemView.findViewById(R.id.tvSeenByMe);
        }
    }

    static class OtherViewHolder extends RecyclerView.ViewHolder {
        final TextView tvContent;
        final TextView tvTime;
        final TextView tvSender;
        final TextView tvSeenBy;

        OtherViewHolder(@NonNull View itemView) {
            super(itemView);
            tvContent = itemView.findViewById(R.id.tvMessageContentOther);
            tvTime = itemView.findViewById(R.id.tvMessageTimeOther);
            tvSender = itemView.findViewById(R.id.tvMessageSenderOther);
            tvSeenBy = itemView.findViewById(R.id.tvSeenByOther);
        }
    }
}
