package com.automate.app.adapters;

import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.automate.app.R;
import com.google.gson.JsonObject;

import java.util.List;

/**
 * RecyclerView adapter for displaying schedule time slots.
 * Each slot shows its number, time range, and color-coded status.
 */
public class ScheduleAdapter extends RecyclerView.Adapter<ScheduleAdapter.ViewHolder> {

    private final List<JsonObject> slots;

    public ScheduleAdapter(List<JsonObject> slots) {
        this.slots = slots;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_schedule_slot, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        JsonObject slot = slots.get(position);

        int slotNum = slot.get("slot_number").getAsInt();
        String startTime = slot.get("start_time").getAsString();
        String endTime = slot.get("end_time").getAsString();
        String status = slot.get("status").getAsString();

        holder.tvSlotNumber.setText("Slot " + slotNum);
        holder.tvTimeRange.setText(startTime + " - " + endTime);

        int dotColor, labelColor, bgColor;
        String labelText;

        switch (status) {
            case "passed":
                dotColor = R.color.slot_passed;
                labelColor = R.color.slot_passed;
                bgColor = R.color.slot_passed_bg;
                labelText = "Done";
                break;
            case "active":
                dotColor = R.color.slot_active;
                labelColor = R.color.slot_active;
                bgColor = R.color.slot_active_bg;
                labelText = "● Active";
                break;
            default: // upcoming
                dotColor = R.color.slot_upcoming;
                labelColor = R.color.slot_upcoming;
                bgColor = R.color.slot_upcoming_bg;
                labelText = "Upcoming";
                break;
        }

        // Status dot
        GradientDrawable dotDrawable = new GradientDrawable();
        dotDrawable.setShape(GradientDrawable.OVAL);
        dotDrawable.setColor(ContextCompat.getColor(holder.itemView.getContext(), dotColor));
        holder.viewStatusDot.setBackground(dotDrawable);

        // Status label
        holder.tvStatusLabel.setText(labelText);
        holder.tvStatusLabel.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), labelColor));

        // Card background tint for active slot
        if ("active".equals(status)) {
            holder.itemView.setAlpha(1.0f);
        } else if ("passed".equals(status)) {
            holder.itemView.setAlpha(0.6f);
        } else {
            holder.itemView.setAlpha(0.85f);
        }
    }

    @Override
    public int getItemCount() {
        return slots.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        View viewStatusDot;
        TextView tvSlotNumber, tvTimeRange, tvStatusLabel;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            viewStatusDot = itemView.findViewById(R.id.viewStatusDot);
            tvSlotNumber = itemView.findViewById(R.id.tvSlotNumber);
            tvTimeRange = itemView.findViewById(R.id.tvTimeRange);
            tvStatusLabel = itemView.findViewById(R.id.tvStatusLabel);
        }
    }
}
