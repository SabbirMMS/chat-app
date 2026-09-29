package com.mrmms.postgresqlchatting.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.mrmms.postgresqlchatting.R;
import com.mrmms.postgresqlchatting.network.models.Room;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class RoomAdapter extends RecyclerView.Adapter<RoomAdapter.RoomViewHolder> {

    public interface OnRoomClickListener {
        void onRoomClick(Room room);
    }

    private final List<Room> rooms = new ArrayList<>();
    private final OnRoomClickListener listener;
    private final SimpleDateFormat displayFormat = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());
    private final SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);

    public RoomAdapter(OnRoomClickListener listener) {
        this.listener = listener;
    }

    public void setRooms(List<Room> newRooms) {
        this.rooms.clear();
        if (newRooms != null) {
            this.rooms.addAll(newRooms);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public RoomViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_room, parent, false);
        return new RoomViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RoomViewHolder holder, int position) {
        Room room = rooms.get(position);
        holder.bind(room);
    }

    @Override
    public int getItemCount() {
        return rooms.size();
    }

    class RoomViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvCode;
        private final TextView tvMemberCount;
        private final TextView tvCreator;
        private final TextView tvDate;

        public RoomViewHolder(@NonNull View itemView) {
            super(itemView);
            tvCode = itemView.findViewById(R.id.tvItemRoomCode);
            tvMemberCount = itemView.findViewById(R.id.tvItemMemberCount);
            tvCreator = itemView.findViewById(R.id.tvItemCreator);
            tvDate = itemView.findViewById(R.id.tvItemDate);

            itemView.setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && listener != null) {
                    listener.onRoomClick(rooms.get(pos));
                }
            });
        }

        public void bind(Room room) {
            tvCode.setText(room.getCode());
            tvMemberCount.setText(String.valueOf(Math.max(room.getMemberCount(), 1)));

            String creator = room.getCreatorUsername() != null ? room.getCreatorUsername() : "user";
            tvCreator.setText("By @" + creator);

            String formattedDate = "";
            String rawDate = room.getCreatedAt() != null ? room.getCreatedAt() : room.getJoinedAt();
            if (rawDate != null) {
                try {
                    // Normalize standard ISO
                    String clean = rawDate.replace("Z", "+00:00");
                    if (clean.length() > 19) {
                        clean = clean.substring(0, 19);
                    }
                    Date date = isoFormat.parse(clean);
                    if (date != null) {
                        formattedDate = displayFormat.format(date);
                    }
                } catch (Exception e) {
                    formattedDate = rawDate.split("T")[0];
                }
            }
            tvDate.setText(formattedDate);
        }
    }
}
