package com.automate.app.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.automate.app.R;
import com.google.android.material.button.MaterialButton;
import com.google.gson.JsonObject;

import java.util.List;

/**
 * RecyclerView adapter for displaying passengers in the driver's dashboard.
 * Each item shows student name, email, and present/absent buttons.
 */
public class QueueAdapter extends RecyclerView.Adapter<QueueAdapter.ViewHolder> {

    private final List<JsonObject> passengers;
    private final OnAttendanceClickListener listener;

    public interface OnAttendanceClickListener {
        void onPresentClick(int studentId, String name);
        void onAbsentClick(int studentId, String name);
    }

    public QueueAdapter(List<JsonObject> passengers, OnAttendanceClickListener listener) {
        this.passengers = passengers;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_queue_passenger, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        JsonObject passenger = passengers.get(position);

        int studentId = passenger.get("student_id").getAsInt();
        String name = passenger.has("student_name") ? passenger.get("student_name").getAsString() : "Student";
        String email = passenger.has("student_email") ? passenger.get("student_email").getAsString() : "";

        holder.tvPosition.setText(String.valueOf(position + 1));
        holder.tvStudentName.setText(name);
        holder.tvStudentEmail.setText(email);

        holder.btnPresent.setOnClickListener(v -> listener.onPresentClick(studentId, name));
        holder.btnAbsent.setOnClickListener(v -> listener.onAbsentClick(studentId, name));
    }

    @Override
    public int getItemCount() {
        return passengers.size();
    }

    public void updateData(List<JsonObject> newPassengers) {
        passengers.clear();
        passengers.addAll(newPassengers);
        notifyDataSetChanged();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvPosition, tvStudentName, tvStudentEmail;
        MaterialButton btnPresent, btnAbsent;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvPosition = itemView.findViewById(R.id.tvPosition);
            tvStudentName = itemView.findViewById(R.id.tvStudentName);
            tvStudentEmail = itemView.findViewById(R.id.tvStudentEmail);
            btnPresent = itemView.findViewById(R.id.btnPresent);
            btnAbsent = itemView.findViewById(R.id.btnAbsent);
        }
    }
}
