package com.automate.app.activities;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.automate.app.R;
import com.automate.app.api.RetrofitClient;
import com.automate.app.utils.LocaleHelper;
import com.automate.app.utils.SessionManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginActivity extends AppCompatActivity {

    private TextInputLayout tilEmail, tilPassword;
    private TextInputEditText etEmail, etPassword;
    private RadioGroup rgRole;
    private MaterialButton btnLogin;
    private ProgressBar progressBar;
    private SessionManager sessionManager;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Apply dark mode preference on app launch
        if (LocaleHelper.isDarkMode(this)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        }

        super.onCreate(savedInstanceState);

        sessionManager = new SessionManager(this);

        // Auto-redirect if already logged in
        if (sessionManager.isLoggedIn()) {
            navigateToDashboard(sessionManager.getRole());
            return;
        }

        setContentView(R.layout.activity_login);

        // Bind views
        tilEmail = findViewById(R.id.tilEmail);
        tilPassword = findViewById(R.id.tilPassword);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        rgRole = findViewById(R.id.rgRole);
        btnLogin = findViewById(R.id.btnLogin);
        progressBar = findViewById(R.id.progressBar);

        btnLogin.setOnClickListener(v -> attemptLogin());

        // Register link
        findViewById(R.id.tvRegisterLink).setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class)));
    }

    private void attemptLogin() {
        // Clear previous errors
        tilEmail.setError(null);
        tilPassword.setError(null);

        String email = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
        String password = etPassword.getText() != null ? etPassword.getText().toString().trim() : "";

        // Validate
        if (email.isEmpty()) {
            tilEmail.setError(getString(R.string.error_email_required));
            return;
        }
        if (!email.contains("@")) {
            tilEmail.setError(getString(R.string.error_invalid_email));
            return;
        }
        if (password.isEmpty()) {
            tilPassword.setError(getString(R.string.error_password_required));
            return;
        }

        // Determine selected role
        String role;
        int selectedId = rgRole.getCheckedRadioButtonId();
        if (selectedId == R.id.rbDriver) {
            role = "driver";
        } else if (selectedId == R.id.rbAdmin) {
            role = "admin";
        } else {
            role = "student";
        }

        // Show loading
        setLoading(true);

        // Make API call
        Map<String, String> body = new HashMap<>();
        body.put("email", email);
        body.put("password", password);
        body.put("role", role);

        RetrofitClient.getInstance().getApi().login(body).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                setLoading(false);

                if (response.isSuccessful() && response.body() != null) {
                    JsonObject data = response.body();

                    String token = data.get("token").getAsString();
                    int userId = data.get("user_id").getAsInt();
                    String userRole = data.get("role").getAsString();
                    String name = data.get("name").getAsString();
                    String userEmail = data.get("email").getAsString();

                    // Save session
                    sessionManager.saveLogin(token, userId, userRole, name, userEmail);

                    Toast.makeText(LoginActivity.this,
                            "Welcome, " + name + "!", Toast.LENGTH_SHORT).show();

                    navigateToDashboard(userRole);
                } else {
                    // Parse the exact error message from backend
                    String errorMsg = "Invalid email or password";
                    try {
                        if (response.errorBody() != null) {
                            String errJson = response.errorBody().string();
                            com.google.gson.JsonObject errObj = com.google.gson.JsonParser.parseString(errJson).getAsJsonObject();
                            if (errObj.has("error")) {
                                errorMsg = errObj.get("error").getAsString();
                            }
                        }
                    } catch (Exception e) { /* use default */ }

                    new androidx.appcompat.app.AlertDialog.Builder(LoginActivity.this)
                            .setTitle("⚠️ Login Error")
                            .setMessage(errorMsg)
                            .setPositiveButton("OK", null)
                            .show();
                }
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                setLoading(false);
                Toast.makeText(LoginActivity.this,
                        getString(R.string.error_network), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void navigateToDashboard(String role) {
        Intent intent;
        switch (role) {
            case "driver":
                intent = new Intent(this, DriverDashboardActivity.class);
                break;
            case "admin":
                intent = new Intent(this, AdminDashboardActivity.class);
                break;
            default:
                intent = new Intent(this, StudentDashboardActivity.class);
                break;
        }
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnLogin.setEnabled(!loading);
        etEmail.setEnabled(!loading);
        etPassword.setEnabled(!loading);
    }
}
