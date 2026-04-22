package com.automate.app.activities;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.cardview.widget.CardView;

import com.automate.app.R;
import com.automate.app.api.RetrofitClient;
import com.automate.app.utils.LocaleHelper;
import com.automate.app.utils.SessionManager;
import com.google.android.material.button.MaterialButton;
import com.google.gson.JsonObject;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class StudentDashboardActivity extends AppCompatActivity {

    private SessionManager sessionManager;
    private TextView tvWelcome, tvQueuePosition, tvEstimatedTime, tvDirection;
    private CardView cardQueueStatus, cardJoinQueue, cardViewStatus, cardComplaint, cardSettings;
    private MaterialButton btnLogout;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Apply dark mode preference before setContentView
        if (LocaleHelper.isDarkMode(this)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_student_dashboard);

        sessionManager = new SessionManager(this);

        // Bind views
        tvWelcome = findViewById(R.id.tvWelcome);
        tvQueuePosition = findViewById(R.id.tvQueuePosition);
        tvEstimatedTime = findViewById(R.id.tvEstimatedTime);
        tvDirection = findViewById(R.id.tvDirection);
        cardQueueStatus = findViewById(R.id.cardQueueStatus);
        cardJoinQueue = findViewById(R.id.cardJoinQueue);
        cardViewStatus = findViewById(R.id.cardViewStatus);
        cardComplaint = findViewById(R.id.cardComplaint);
        cardSettings = findViewById(R.id.cardSettings);
        btnLogout = findViewById(R.id.btnLogout);

        // Set welcome message
        tvWelcome.setText(String.format("Hello, %s", sessionManager.getName()));

        // Card click listeners
        cardJoinQueue.setOnClickListener(v -> {
            Intent intent = new Intent(this, QueueActivity.class);
            startActivity(intent);
        });

        cardViewStatus.setOnClickListener(v -> {
            startActivity(new Intent(this, ScheduleActivity.class));
        });

        cardComplaint.setOnClickListener(v -> {
            startActivity(new Intent(this, ComplaintActivity.class));
        });

        cardSettings.setOnClickListener(v -> {
            startActivity(new Intent(this, SettingsActivity.class));
        });

        btnLogout.setOnClickListener(v -> logout());
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadQueueStatus();
    }

    private void loadQueueStatus() {
        String token = "Bearer " + sessionManager.getToken();

        RetrofitClient.getInstance().getApi().getQueueStatus(token).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                if (response.isSuccessful() && response.body() != null) {
                    JsonObject data = response.body();
                    boolean inQueue = data.has("in_queue") && data.get("in_queue").getAsBoolean();

                    if (inQueue) {
                        cardQueueStatus.setVisibility(View.VISIBLE);

                        int position = data.get("position").getAsInt();
                        String estTime = data.get("estimated_time").getAsString();
                        String direction = data.get("direction").getAsString();

                        tvQueuePosition.setText("#" + position);
                        tvEstimatedTime.setText("Est. wait: " + estTime);

                        if ("college_to_main".equals(direction)) {
                            tvDirection.setText("→ Main Rd");
                            tvDirection.setTextColor(getColor(R.color.college_to_main));
                        } else {
                            tvDirection.setText("→ College");
                            tvDirection.setTextColor(getColor(R.color.main_to_college));
                        }
                    } else {
                        cardQueueStatus.setVisibility(View.GONE);
                    }
                }
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                // Silently fail - status card stays hidden
            }
        });
    }

    private void logout() {
        sessionManager.logout();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
