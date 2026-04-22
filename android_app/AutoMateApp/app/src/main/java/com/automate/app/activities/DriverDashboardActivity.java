package com.automate.app.activities;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.automate.app.R;
import com.automate.app.adapters.QueueAdapter;
import com.automate.app.api.RetrofitClient;
import com.automate.app.utils.LocaleHelper;
import com.automate.app.utils.SessionManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class DriverDashboardActivity extends AppCompatActivity implements QueueAdapter.OnAttendanceClickListener {

    private static final int MAX_PASSENGERS = 4;

    private SessionManager sessionManager;
    private RecyclerView rvPassengers;
    private QueueAdapter adapter;
    private List<JsonObject> passengerList = new ArrayList<>();
    private SwipeRefreshLayout swipeRefresh;
    private LinearLayout layoutEmpty;
    private ProgressBar progressBar;
    private TextView tvDriverName, tvCurrentSlot, tvWaitingCount;
    private MaterialButton btnStartTrip, btnLogout;
    private MaterialButtonToggleGroup toggleDirection;
    private String currentDirection = "college_to_main";
    private Handler refreshHandler;
    private Runnable refreshRunnable;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase));
    }

    // Track how many students are marked present PER DIRECTION
    private Map<String, Integer> presentCountMap = new HashMap<>();
    private boolean queueEmpty = false;

    private int getPresentCount() {
        return presentCountMap.getOrDefault(currentDirection, 0);
    }

    private void addPresentCount(int delta) {
        presentCountMap.put(currentDirection, getPresentCount() + delta);
    }

    private void resetPresentCount() {
        presentCountMap.put(currentDirection, 0);
    }

    private void resetAllPresentCounts() {
        presentCountMap.clear();
    }

    // 15-minute cooldown
    private static final long COOLDOWN_MS = 15 * 60 * 1000; // 15 minutes
    private CountDownTimer cooldownTimer;
    private boolean isCooldownActive = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_driver_dashboard);

        sessionManager = new SessionManager(this);

        // Bind views
        tvDriverName = findViewById(R.id.tvDriverName);
        tvCurrentSlot = findViewById(R.id.tvCurrentSlot);
        tvWaitingCount = findViewById(R.id.tvWaitingCount);
        rvPassengers = findViewById(R.id.rvPassengers);
        swipeRefresh = findViewById(R.id.swipeRefresh);
        layoutEmpty = findViewById(R.id.layoutEmpty);
        progressBar = findViewById(R.id.progressBar);
        btnStartTrip = findViewById(R.id.btnStartTrip);
        btnLogout = findViewById(R.id.btnLogout);
        toggleDirection = findViewById(R.id.toggleDirection);

        tvDriverName.setText("Welcome, " + sessionManager.getName());

        // Setup RecyclerView
        adapter = new QueueAdapter(passengerList, this);
        rvPassengers.setLayoutManager(new LinearLayoutManager(this));
        rvPassengers.setAdapter(adapter);

        // Direction toggle
        toggleDirection.check(R.id.btnCollegeToMain);
        toggleDirection.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                currentDirection = (checkedId == R.id.btnCollegeToMain)
                        ? "college_to_main" : "main_to_college";
                // Don't reset — each direction keeps its own count
                updateStartTripButton();
                loadPassengers();
            }
        });

        // Swipe to refresh
        swipeRefresh.setOnRefreshListener(this::loadPassengers);
        swipeRefresh.setColorSchemeResources(R.color.primary);

        // Start trip (initially disabled — gray)
        btnStartTrip.setEnabled(false);
        btnStartTrip.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.text_hint)));
        btnStartTrip.setOnClickListener(v -> confirmStartTrip());

        // Logout
        btnLogout.setOnClickListener(v -> logout());

        // Auto-refresh every 30 seconds
        refreshHandler = new Handler(Looper.getMainLooper());
        refreshRunnable = () -> {
            loadPassengers();
            refreshHandler.postDelayed(refreshRunnable, 30000);
        };
    }

    @Override
    protected void onResume() {
        super.onResume();
        resetAllPresentCounts();
        checkExistingCooldown(); // Check if cooldown is still active from a previous trip
        loadPassengers();
        refreshHandler.postDelayed(refreshRunnable, 30000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        refreshHandler.removeCallbacks(refreshRunnable);
    }

    private void loadPassengers() {
        String token = "Bearer " + sessionManager.getToken();

        RetrofitClient.getInstance().getApi().getDriverQueue(token, currentDirection)
                .enqueue(new Callback<JsonObject>() {
                    @Override
                    public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                        swipeRefresh.setRefreshing(false);
                        progressBar.setVisibility(View.GONE);

                        if (response.isSuccessful() && response.body() != null) {
                            JsonObject data = response.body();

                            // Update waiting count
                            int totalWaiting = data.get("total_waiting").getAsInt();
                            tvWaitingCount.setText(totalWaiting + " waiting");

                            // Update time slot
                            if (data.has("current_time_slot") && !data.get("current_time_slot").isJsonNull()) {
                                tvCurrentSlot.setText("Slot: " + data.get("current_time_slot").getAsString());
                            } else {
                                tvCurrentSlot.setText("Service closed");
                            }

                            // Update passenger list
                            JsonArray passengers = data.getAsJsonArray("passengers");
                            passengerList.clear();
                            if (passengers != null) {
                                for (int i = 0; i < passengers.size(); i++) {
                                    passengerList.add(passengers.get(i).getAsJsonObject());
                                }
                            }
                            adapter.notifyDataSetChanged();

                            // Track if queue is empty
                            queueEmpty = passengerList.isEmpty();
                            updateStartTripButton();

                            // Show/hide empty state
                            layoutEmpty.setVisibility(queueEmpty ? View.VISIBLE : View.GONE);
                            rvPassengers.setVisibility(queueEmpty ? View.GONE : View.VISIBLE);
                        }
                    }

                    @Override
                    public void onFailure(Call<JsonObject> call, Throwable t) {
                        swipeRefresh.setRefreshing(false);
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(DriverDashboardActivity.this,
                                getString(R.string.error_network), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    @Override
    public void onPresentClick(int studentId, String name) {
        // Block during cooldown
        if (isCooldownActive) {
            new AlertDialog.Builder(this)
                    .setTitle("🚫 Currently on a Trip")
                    .setMessage("Can't take students while on a trip!\nWait for the cooldown to finish.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        // Check if already at max capacity
        if (getPresentCount() >= MAX_PASSENGERS) {
            new AlertDialog.Builder(this)
                    .setTitle("🚫 Auto Full!")
                    .setMessage("Cannot take more than " + MAX_PASSENGERS + " students.\nStart the trip first!")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        markAttendance(studentId, "present", name);
    }

    @Override
    public void onAbsentClick(int studentId, String name) {
        // Block during cooldown
        if (isCooldownActive) {
            new AlertDialog.Builder(this)
                    .setTitle("🚫 Currently on a Trip")
                    .setMessage("Can't modify queue while on a trip!\nWait for the cooldown to finish.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Mark Absent")
                .setMessage("Mark " + name + " as absent?\nThey will be moved to the end of the queue.")
                .setPositiveButton("Yes", (d, w) -> markAttendance(studentId, "absent", name))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void markAttendance(int studentId, String status, String name) {
        String token = "Bearer " + sessionManager.getToken();

        Map<String, Object> body = new HashMap<>();
        body.put("student_id", studentId);
        body.put("status", status);

        RetrofitClient.getInstance().getApi().markAttendance(token, body)
                .enqueue(new Callback<JsonObject>() {
                    @Override
                    public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            String msg = response.body().get("message").getAsString();

                            if ("present".equals(status)) {
                                addPresentCount(1);
                                Toast.makeText(DriverDashboardActivity.this,
                                        "✓ " + name + " confirmed (" + getPresentCount() + "/" + MAX_PASSENGERS + ")",
                                        Toast.LENGTH_SHORT).show();

                                if (getPresentCount() >= MAX_PASSENGERS) {
                                    Toast.makeText(DriverDashboardActivity.this,
                                            "🛺 Auto is full! Ready to start trip!",
                                            Toast.LENGTH_LONG).show();
                                }
                            } else {
                                // Absent — student goes to end of queue
                                Toast.makeText(DriverDashboardActivity.this,
                                        "✗ " + name + " marked absent (moved to end of queue)",
                                        Toast.LENGTH_SHORT).show();
                            }

                            updateStartTripButton();
                            loadPassengers(); // Refresh list
                        } else {
                            Toast.makeText(DriverDashboardActivity.this,
                                    "Error marking attendance", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<JsonObject> call, Throwable t) {
                        Toast.makeText(DriverDashboardActivity.this,
                                getString(R.string.error_network), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void updateStartTripButton() {
        // Don't override if cooldown timer is running
        if (isCooldownActive) return;

        boolean isFull = getPresentCount() >= MAX_PASSENGERS;
        boolean canGoWithLess = queueEmpty && getPresentCount() > 0;

        if (isFull || canGoWithLess) {
            // GREEN — ready to go!
            btnStartTrip.setEnabled(true);
            btnStartTrip.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(getColor(R.color.success)));

            if (isFull) {
                btnStartTrip.setText("🛺 Start Trip (" + getPresentCount() + " passengers)");
            } else {
                btnStartTrip.setText("🛺 Start Trip (" + getPresentCount() + " — queue empty)");
            }
        } else {
            // GRAY — not ready yet
            btnStartTrip.setEnabled(false);
            btnStartTrip.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(getColor(R.color.text_hint)));
            btnStartTrip.setText("Start Trip (" + getPresentCount() + "/" + MAX_PASSENGERS + " confirmed)");
        }
    }

    private void confirmStartTrip() {
        new AlertDialog.Builder(this)
                .setTitle("🛺 Start Trip")
                .setMessage("Start trip with " + getPresentCount() + " passenger(s)?")
                .setPositiveButton("Start", (d, w) -> startTrip())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void startTrip() {
        btnStartTrip.setEnabled(false);
        String token = "Bearer " + sessionManager.getToken();

        Map<String, String> body = new HashMap<>();
        body.put("direction", currentDirection);

        RetrofitClient.getInstance().getApi().startTrip(token, body)
                .enqueue(new Callback<JsonObject>() {
                    @Override
                    public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            int count = response.body().get("passenger_count").getAsInt();
                            Toast.makeText(DriverDashboardActivity.this,
                                    "Trip started with " + count + " passengers! \uD83D\uDE97",
                                    Toast.LENGTH_LONG).show();

                            // Reset counter and start 15-min cooldown
                            resetAllPresentCounts();
                            startCooldown(COOLDOWN_MS);
                            loadPassengers();
                        } else if (response.code() == 429) {
                            // Backend says cooldown is active
                            try {
                                String errBody = response.errorBody().string();
                                if (errBody.contains("cooldown_remaining")) {
                                    // Parse remaining seconds
                                    int idx = errBody.indexOf("cooldown_remaining");
                                    String numStr = errBody.substring(idx).replaceAll("[^0-9]", "");
                                    long remaining = Long.parseLong(numStr) * 1000;
                                    startCooldown(remaining);
                                } else {
                                    startCooldown(COOLDOWN_MS);
                                }
                            } catch (Exception e) {
                                startCooldown(COOLDOWN_MS);
                            }
                            Toast.makeText(DriverDashboardActivity.this,
                                    "\u23F3 Cooldown active! Wait before next trip.",
                                    Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(DriverDashboardActivity.this,
                                    "Could not start trip", Toast.LENGTH_SHORT).show();
                            btnStartTrip.setEnabled(true);
                        }
                    }

                    @Override
                    public void onFailure(Call<JsonObject> call, Throwable t) {
                        btnStartTrip.setEnabled(true);
                        Toast.makeText(DriverDashboardActivity.this,
                                getString(R.string.error_network), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // ==================== COOLDOWN TIMER ====================

    private void startCooldown(long durationMs) {
        isCooldownActive = true;

        // Save cooldown end time in SharedPreferences (survives app restart)
        long cooldownEnd = System.currentTimeMillis() + durationMs;
        getSharedPreferences("driver_prefs", MODE_PRIVATE)
                .edit()
                .putLong("cooldown_end", cooldownEnd)
                .apply();

        // Cancel existing timer if any
        if (cooldownTimer != null) cooldownTimer.cancel();

        cooldownTimer = new CountDownTimer(durationMs, 1000) {
            @Override
            public void onTick(long millisLeft) {
                int mins = (int) (millisLeft / 60000);
                int secs = (int) ((millisLeft % 60000) / 1000);
                btnStartTrip.setEnabled(false);
                btnStartTrip.setBackgroundTintList(
                        android.content.res.ColorStateList.valueOf(getColor(R.color.text_hint)));
                btnStartTrip.setText(String.format("\u23F3 On Trip — %02d:%02d remaining", mins, secs));
            }

            @Override
            public void onFinish() {
                isCooldownActive = false;
                getSharedPreferences("driver_prefs", MODE_PRIVATE)
                        .edit().remove("cooldown_end").apply();
                Toast.makeText(DriverDashboardActivity.this,
                        "\u2705 Ready for next trip!", Toast.LENGTH_SHORT).show();
                resetAllPresentCounts();
                updateStartTripButton();
                loadPassengers();
            }
        }.start();
    }

    private void checkExistingCooldown() {
        long cooldownEnd = getSharedPreferences("driver_prefs", MODE_PRIVATE)
                .getLong("cooldown_end", 0);
        long remaining = cooldownEnd - System.currentTimeMillis();

        if (remaining > 0) {
            startCooldown(remaining);
        } else {
            isCooldownActive = false;
            updateStartTripButton();
        }
    }

    private void logout() {
        sessionManager.logout();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
