package com.automate.app.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.automate.app.R;
import com.google.gson.JsonObject;

import java.util.List;

/**
 * RecyclerView adapter for displaying complaints in the admin dashboard.
 */
public class ComplaintAdapter extends RecyclerView.Adapter<ComplaintAdapter.ViewHolder> {

    private final List<JsonObject> complaints;

    public ComplaintAdapter(List<JsonObject> complaints) {
        this.complaints = complaints;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_complaint, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        JsonObject complaint = complaints.get(position);

        String type = complaint.has("complaint_type") ?
                complaint.get("complaint_type").getAsString().replace("_", " ") : "Unknown";
        String message = complaint.has("message") ? complaint.get("message").getAsString() : "";
        String status = complaint.has("status") ? complaint.get("status").getAsString() : "pending";
        String studentName = complaint.has("student_name") ?
                complaint.get("student_name").getAsString() : "Unknown";
        String date = complaint.has("created_at") ? complaint.get("created_at").getAsString() : "";

        // Capitalize type
        type = type.substring(0, 1).toUpperCase() + type.substring(1);

        holder.tvComplaintType.setText(type);
        holder.tvComplaintMessage.setText(message);
        holder.tvStudentName.setText("By: " + studentName);
        holder.tvComplaintDate.setText(date.length() > 10 ? date.substring(0, 10) : date);

        // Status color
        holder.tvComplaintStatus.setText(status.substring(0, 1).toUpperCase() + status.substring(1));
        switch (status) {
            case "pending":
                holder.tvComplaintStatus.setTextColor(
                        holder.itemView.getContext().getColor(R.color.warning));
                break;
            case "reviewed":
                holder.tvComplaintStatus.setTextColor(
                        holder.itemView.getContext().getColor(R.color.info));
                break;
            case "resolved":
                holder.tvComplaintStatus.setTextColor(
                        holder.itemView.getContext().getColor(R.color.success));
                break;
        }
    }

    @Override
    public int getItemCount() {
        return complaints.size();
    }

    public void updateData(List<JsonObject> newComplaints) {
        complaints.clear();
        complaints.addAll(newComplaints);
        notifyDataSetChanged();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvComplaintType, tvComplaintMessage, tvComplaintStatus;
        TextView tvStudentName, tvComplaintDate;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvComplaintType = itemView.findViewById(R.id.tvComplaintType);
            tvComplaintMessage = itemView.findViewById(R.id.tvComplaintMessage);
            tvComplaintStatus = itemView.findViewById(R.id.tvComplaintStatus);
            tvStudentName = itemView.findViewById(R.id.tvStudentName);
            tvComplaintDate = itemView.findViewById(R.id.tvComplaintDate);
        }
    }
}
