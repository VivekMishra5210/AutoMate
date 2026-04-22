package com.automate.app.activities;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.automate.app.R;
import com.automate.app.adapters.ScheduleAdapter;
import com.automate.app.api.RetrofitClient;
import com.automate.app.utils.LocaleHelper;
import com.automate.app.utils.SessionManager;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ScheduleActivity extends AppCompatActivity {

    private SessionManager sessionManager;
    private RecyclerView rvSchedule;
    private ProgressBar progressBar;
    private ScheduleAdapter adapter;
    private List<JsonObject> slotList = new ArrayList<>();

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_schedule);

        sessionManager = new SessionManager(this);

        // Toolbar
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        // Bind views
        rvSchedule = findViewById(R.id.rvSchedule);
        progressBar = findViewById(R.id.progressBar);

        // Setup RecyclerView
        adapter = new ScheduleAdapter(slotList);
        rvSchedule.setLayoutManager(new LinearLayoutManager(this));
        rvSchedule.setAdapter(adapter);

        // Load schedule
        loadSchedule();
    }

    private void loadSchedule() {
        progressBar.setVisibility(View.VISIBLE);
        rvSchedule.setVisibility(View.GONE);
        String token = "Bearer " + sessionManager.getToken();

        RetrofitClient.getInstance().getApi().getSchedule(token)
                .enqueue(new Callback<JsonObject>() {
                    @Override
                    public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                        progressBar.setVisibility(View.GONE);
                        rvSchedule.setVisibility(View.VISIBLE);

                        if (response.isSuccessful() && response.body() != null) {
                            JsonObject data = response.body();
                            JsonArray slotsArray = data.getAsJsonArray("slots");

                            slotList.clear();
                            if (slotsArray != null) {
                                for (int i = 0; i < slotsArray.size(); i++) {
                                    slotList.add(slotsArray.get(i).getAsJsonObject());
                                }
                            }
                            adapter.notifyDataSetChanged();

                            // Scroll to the active slot
                            scrollToActiveSlot();
                        } else {
                            Toast.makeText(ScheduleActivity.this,
                                    "Could not load schedule", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<JsonObject> call, Throwable t) {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(ScheduleActivity.this,
                                getString(R.string.error_network), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void scrollToActiveSlot() {
        for (int i = 0; i < slotList.size(); i++) {
            if ("active".equals(slotList.get(i).get("status").getAsString())) {
                // Scroll so the active slot is near the top with some context
                int scrollPosition = Math.max(0, i - 2);
                rvSchedule.scrollToPosition(scrollPosition);
                break;
            }
        }
    }
}
