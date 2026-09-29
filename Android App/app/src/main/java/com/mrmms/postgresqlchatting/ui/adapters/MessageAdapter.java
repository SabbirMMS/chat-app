package com.mrmms.postgresqlchatting.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.mrmms.postgresqlchatting.R;
import com.mrmms.postgresqlchatting.network.models.Message;

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

        if (holder instanceof MeViewHolder) {
            MeViewHolder meHolder = (MeViewHolder) holder;
            meHolder.tvContent.setText(msg.getContent());
            meHolder.tvTime.setText(formattedTime);
        } else if (holder instanceof OtherViewHolder) {
            OtherViewHolder otherHolder = (OtherViewHolder) holder;
            otherHolder.tvContent.setText(msg.getContent());
            otherHolder.tvTime.setText(formattedTime);
            String senderName = (msg.getSender() != null && msg.getSender().getUsername() != null)
                    ? msg.getSender().getUsername()
                    : "User";
            otherHolder.tvSender.setText(senderName);
        }
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

        MeViewHolder(@NonNull View itemView) {
            super(itemView);
            tvContent = itemView.findViewById(R.id.tvMessageContentMe);
            tvTime = itemView.findViewById(R.id.tvMessageTimeMe);
        }
    }

    static class OtherViewHolder extends RecyclerView.ViewHolder {
        final TextView tvContent;
        final TextView tvTime;
        final TextView tvSender;

        OtherViewHolder(@NonNull View itemView) {
            super(itemView);
            tvContent = itemView.findViewById(R.id.tvMessageContentOther);
            tvTime = itemView.findViewById(R.id.tvMessageTimeOther);
            tvSender = itemView.findViewById(R.id.tvMessageSenderOther);
        }
    }
}
