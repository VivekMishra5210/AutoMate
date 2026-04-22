package com.automate.app.activities;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.automate.app.R;
import com.automate.app.api.RetrofitClient;
import com.automate.app.utils.LocaleHelper;
import com.automate.app.utils.SessionManager;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ComplaintActivity extends AppCompatActivity {

    private SessionManager sessionManager;
    private Spinner spinnerType;
    private TextInputEditText etMessage;
    private MaterialButton btnSubmit;
    private ProgressBar progressBar;

    // Maps display labels to API values
    private final String[] typeLabels = {
            "Driver Absent", "Auto Not Arrived", "Misbehavior", "Other"
    };
    private final String[] typeValues = {
            "driver_absent", "auto_not_arrived", "misbehavior", "other"
    };

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_complaint);

        sessionManager = new SessionManager(this);

        // Toolbar
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        // Bind views
        spinnerType = findViewById(R.id.spinnerComplaintType);
        etMessage = findViewById(R.id.etMessage);
        btnSubmit = findViewById(R.id.btnSubmit);
        progressBar = findViewById(R.id.progressBar);

        // Setup spinner
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, typeLabels);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerType.setAdapter(spinnerAdapter);

        btnSubmit.setOnClickListener(v -> submitComplaint());
    }

    private void submitComplaint() {
        String message = etMessage.getText() != null ? etMessage.getText().toString().trim() : "";

        if (message.length() < 10) {
            etMessage.setError("Message must be at least 10 characters");
            return;
        }

        int typeIndex = spinnerType.getSelectedItemPosition();
        String complaintType = typeValues[typeIndex];

        setLoading(true);

        String token = "Bearer " + sessionManager.getToken();
        Map<String, String> body = new HashMap<>();
        body.put("complaint_type", complaintType);
        body.put("message", message);

        RetrofitClient.getInstance().getApi().submitComplaint(token, body)
                .enqueue(new Callback<JsonObject>() {
                    @Override
                    public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                        setLoading(false);
                        if (response.isSuccessful()) {
                            Toast.makeText(ComplaintActivity.this,
                                    getString(R.string.complaint_submitted),
                                    Toast.LENGTH_LONG).show();
                            finish();
                        } else {
                            Toast.makeText(ComplaintActivity.this,
                                    "Failed to submit complaint", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<JsonObject> call, Throwable t) {
                        setLoading(false);
                        Toast.makeText(ComplaintActivity.this,
                                getString(R.string.error_network), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnSubmit.setEnabled(!loading);
    }
}
