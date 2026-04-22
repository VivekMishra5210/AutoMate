package com.automate.app.activities;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.automate.app.R;
import com.automate.app.api.RetrofitClient;
import com.automate.app.utils.SessionManager;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import com.automate.app.utils.LocaleHelper;

import android.widget.TextView;

public class QueueActivity extends AppCompatActivity {

    private SessionManager sessionManager;
    private MaterialButtonToggleGroup toggleDirection;
    private CardView cardQueueStatus, cardJoinQueue;
    private TextView tvPosition, tvEstimatedWait, tvTimeSlot;
    private MaterialButton btnJoinQueue, btnLeaveQueue;
    private ProgressBar progressBar;
    private String currentDirection = "college_to_main";

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_queue);

        sessionManager = new SessionManager(this);

        // Toolbar
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        // Bind views
        toggleDirection = findViewById(R.id.toggleDirection);
        cardQueueStatus = findViewById(R.id.cardQueueStatus);
        cardJoinQueue = findViewById(R.id.cardJoinQueue);
        tvPosition = findViewById(R.id.tvPosition);
        tvEstimatedWait = findViewById(R.id.tvEstimatedWait);
        tvTimeSlot = findViewById(R.id.tvTimeSlot);
        btnJoinQueue = findViewById(R.id.btnJoinQueue);
        btnLeaveQueue = findViewById(R.id.btnLeaveQueue);
        progressBar = findViewById(R.id.progressBar);

        // Direction toggle - default checked
        toggleDirection.check(R.id.btnCollegeToMain);
        toggleDirection.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                currentDirection = (checkedId == R.id.btnCollegeToMain)
                        ? "college_to_main" : "main_to_college";
            }
        });

        btnJoinQueue.setOnClickListener(v -> joinQueue());
        btnLeaveQueue.setOnClickListener(v -> leaveQueue());

        // Check if already in queue
        checkQueueStatus();
    }

    private void checkQueueStatus() {
        progressBar.setVisibility(View.VISIBLE);
        String token = "Bearer " + sessionManager.getToken();

        RetrofitClient.getInstance().getApi().getQueueStatus(token).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                progressBar.setVisibility(View.GONE);

                if (response.isSuccessful() && response.body() != null) {
                    JsonObject data = response.body();
                    boolean inQueue = data.has("in_queue") && data.get("in_queue").getAsBoolean();

                    if (inQueue) {
                        showQueueStatus(data);
                    } else {
                        showJoinQueue();
                    }
                } else {
                    showJoinQueue();
                }
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                progressBar.setVisibility(View.GONE);
                showJoinQueue();
                Toast.makeText(QueueActivity.this, getString(R.string.error_network), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showQueueStatus(JsonObject data) {
        cardQueueStatus.setVisibility(View.VISIBLE);
        cardJoinQueue.setVisibility(View.GONE);
        toggleDirection.setVisibility(View.GONE);

        int position = data.get("position").getAsInt();
        String estTime = data.get("estimated_time").getAsString();
        String timeSlot = data.has("current_time_slot") && !data.get("current_time_slot").isJsonNull()
                ? data.get("current_time_slot").getAsString() : "--:--";

        tvPosition.setText("#" + position);
        tvEstimatedWait.setText(estTime);
        tvTimeSlot.setText(timeSlot);
    }

    private void showJoinQueue() {
        cardQueueStatus.setVisibility(View.GONE);
        cardJoinQueue.setVisibility(View.VISIBLE);
        toggleDirection.setVisibility(View.VISIBLE);
    }

    private void joinQueue() {
        btnJoinQueue.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);
        String token = "Bearer " + sessionManager.getToken();

        Map<String, String> body = new HashMap<>();
        body.put("direction", currentDirection);

        RetrofitClient.getInstance().getApi().joinQueue(token, body).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                progressBar.setVisibility(View.GONE);
                btnJoinQueue.setEnabled(true);

                if (response.isSuccessful() && response.body() != null) {
                    JsonObject data = response.body();
                    Toast.makeText(QueueActivity.this,
                            "Joined queue at position " + data.get("position").getAsInt(),
                            Toast.LENGTH_SHORT).show();
                    checkQueueStatus();
                } else {
                    // Show actual error from backend as a popup
                    String errorMsg = "Could not join queue";
                    try {
                        if (response.errorBody() != null) {
                            String errJson = response.errorBody().string();
                            com.google.gson.JsonObject errObj = com.google.gson.JsonParser.parseString(errJson).getAsJsonObject();
                            if (errObj.has("error")) {
                                errorMsg = errObj.get("error").getAsString();
                            }
                        }
                    } catch (Exception e) { /* use default */ }
                    new androidx.appcompat.app.AlertDialog.Builder(QueueActivity.this)
                            .setTitle("🚫 Service Closed")
                            .setMessage(errorMsg)
                            .setPositiveButton("OK", null)
                            .show();
                }
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                progressBar.setVisibility(View.GONE);
                btnJoinQueue.setEnabled(true);
                Toast.makeText(QueueActivity.this, getString(R.string.error_network), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void leaveQueue() {
        btnLeaveQueue.setEnabled(false);
        String token = "Bearer " + sessionManager.getToken();

        RetrofitClient.getInstance().getApi().leaveQueue(token).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                btnLeaveQueue.setEnabled(true);

                if (response.isSuccessful()) {
                    Toast.makeText(QueueActivity.this, "Left the queue", Toast.LENGTH_SHORT).show();
                    showJoinQueue();
                } else {
                    Toast.makeText(QueueActivity.this, "Error leaving queue", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                btnLeaveQueue.setEnabled(true);
                Toast.makeText(QueueActivity.this, getString(R.string.error_network), Toast.LENGTH_SHORT).show();
            }
        });
    }
}
