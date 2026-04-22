package com.automate.app.activities;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.automate.app.R;
import com.automate.app.adapters.ComplaintAdapter;
import com.automate.app.api.RetrofitClient;
import com.automate.app.utils.LocaleHelper;
import com.automate.app.utils.SessionManager;
import com.google.android.material.button.MaterialButton;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AdminDashboardActivity extends AppCompatActivity {

    private SessionManager sessionManager;
    private TextView tvCollegeToMain, tvMainToCollege, tvTripsToday, tvPassengersToday;
    private RecyclerView rvComplaints;
    private ComplaintAdapter complaintAdapter;
    private List<JsonObject> complaintList = new ArrayList<>();
    private SwipeRefreshLayout swipeRefresh;
    private ProgressBar progressBar;
    private MaterialButton btnLogout;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_dashboard);

        sessionManager = new SessionManager(this);

        // Bind views
        tvCollegeToMain = findViewById(R.id.tvCollegeToMain);
        tvMainToCollege = findViewById(R.id.tvMainToCollege);
        tvTripsToday = findViewById(R.id.tvTripsToday);
        tvPassengersToday = findViewById(R.id.tvPassengersToday);
        rvComplaints = findViewById(R.id.rvComplaints);
        swipeRefresh = findViewById(R.id.swipeRefresh);
        progressBar = findViewById(R.id.progressBar);
        btnLogout = findViewById(R.id.btnLogout);

        // Setup RecyclerView
        complaintAdapter = new ComplaintAdapter(complaintList);
        rvComplaints.setLayoutManager(new LinearLayoutManager(this));
        rvComplaints.setAdapter(complaintAdapter);

        // Swipe refresh
        swipeRefresh.setOnRefreshListener(this::loadAllData);
        swipeRefresh.setColorSchemeResources(R.color.primary);

        // Logout
        btnLogout.setOnClickListener(v -> logout());
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadAllData();
    }

    private void loadAllData() {
        loadStats();
        loadComplaints();
    }

    private void loadStats() {
        String token = "Bearer " + sessionManager.getToken();

        RetrofitClient.getInstance().getApi().getQueueStats(token)
                .enqueue(new Callback<JsonObject>() {
                    @Override
                    public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            JsonObject stats = response.body();

                            tvCollegeToMain.setText(String.valueOf(
                                    stats.get("college_to_main_waiting").getAsInt()));
                            tvMainToCollege.setText(String.valueOf(
                                    stats.get("main_to_college_waiting").getAsInt()));
                            tvTripsToday.setText(String.valueOf(
                                    stats.get("trips_today").getAsInt()));
                            tvPassengersToday.setText(String.valueOf(
                                    stats.get("passengers_today").getAsInt()));
                        }
                    }

                    @Override
                    public void onFailure(Call<JsonObject> call, Throwable t) {
                        Toast.makeText(AdminDashboardActivity.this,
                                "Failed to load stats", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void loadComplaints() {
        String token = "Bearer " + sessionManager.getToken();

        RetrofitClient.getInstance().getApi().getComplaints(token)
                .enqueue(new Callback<JsonObject>() {
                    @Override
                    public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                        swipeRefresh.setRefreshing(false);
                        progressBar.setVisibility(View.GONE);

                        if (response.isSuccessful() && response.body() != null) {
                            JsonObject data = response.body();
                            JsonArray complaints = data.getAsJsonArray("complaints");

                            complaintList.clear();
                            if (complaints != null) {
                                for (int i = 0; i < complaints.size(); i++) {
                                    complaintList.add(complaints.get(i).getAsJsonObject());
                                }
                            }
                            complaintAdapter.notifyDataSetChanged();
                        }
                    }

                    @Override
                    public void onFailure(Call<JsonObject> call, Throwable t) {
                        swipeRefresh.setRefreshing(false);
                        progressBar.setVisibility(View.GONE);
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
