package com.automate.app.activities;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

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

public class RegisterActivity extends AppCompatActivity {

    private TextInputLayout tilName, tilEmail, tilPhone, tilPassword, tilConfirmPassword;
    private TextInputEditText etName, etEmail, etPhone, etPassword, etConfirmPassword;
    private RadioGroup rgRole;
    private MaterialButton btnRegister;
    private ProgressBar progressBar;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        // Bind views
        tilName = findViewById(R.id.tilName);
        tilEmail = findViewById(R.id.tilEmail);
        tilPhone = findViewById(R.id.tilPhone);
        tilPassword = findViewById(R.id.tilPassword);
        tilConfirmPassword = findViewById(R.id.tilConfirmPassword);
        etName = findViewById(R.id.etName);
        etEmail = findViewById(R.id.etEmail);
        etPhone = findViewById(R.id.etPhone);
        etPassword = findViewById(R.id.etPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        rgRole = findViewById(R.id.rgRole);
        btnRegister = findViewById(R.id.btnRegister);
        progressBar = findViewById(R.id.progressBar);

        MaterialButton btnBack = findViewById(R.id.btnBack);
        TextView tvLoginLink = findViewById(R.id.tvLoginLink);

        btnRegister.setOnClickListener(v -> attemptRegister());
        btnBack.setOnClickListener(v -> finish());
        tvLoginLink.setOnClickListener(v -> finish());
    }

    private void attemptRegister() {
        // Clear errors
        tilName.setError(null);
        tilEmail.setError(null);
        tilPhone.setError(null);
        tilPassword.setError(null);
        tilConfirmPassword.setError(null);

        String name = getText(etName);
        String email = getText(etEmail);
        String phone = getText(etPhone);
        String password = getText(etPassword);
        String confirmPassword = getText(etConfirmPassword);

        // Validate
        if (name.isEmpty()) { tilName.setError("Name is required"); return; }
        if (name.length() < 2) { tilName.setError("Name too short"); return; }
        if (email.isEmpty()) { tilEmail.setError("Email is required"); return; }
        if (!email.contains("@")) { tilEmail.setError("Invalid email"); return; }
        if (phone.isEmpty()) { tilPhone.setError("Phone is required"); return; }
        if (phone.length() < 10) { tilPhone.setError("Invalid phone number"); return; }
        if (password.isEmpty()) { tilPassword.setError("Password is required"); return; }
        if (password.length() < 6) { tilPassword.setError("Min 6 characters"); return; }
        if (!password.equals(confirmPassword)) {
            tilConfirmPassword.setError("Passwords don't match");
            return;
        }

        // Get role
        String role = (rgRole.getCheckedRadioButtonId() == R.id.rbDriver) ? "driver" : "student";

        setLoading(true);

        Map<String, String> body = new HashMap<>();
        body.put("name", name);
        body.put("email", email);
        body.put("phone", phone);
        body.put("password", password);
        body.put("role", role);

        RetrofitClient.getInstance().getApi().register(body).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                setLoading(false);

                if (response.isSuccessful() && response.body() != null) {
                    JsonObject data = response.body();

                    Toast.makeText(RegisterActivity.this,
                            "Account created! Please log in.",
                            Toast.LENGTH_LONG).show();

                    // Go back to login
                    finish();
                } else {
                    // Parse the exact error message from backend
                    String errorMsg = "Registration failed";
                    try {
                        if (response.errorBody() != null) {
                            String errJson = response.errorBody().string();
                            com.google.gson.JsonObject errObj = com.google.gson.JsonParser.parseString(errJson).getAsJsonObject();
                            if (errObj.has("error")) {
                                errorMsg = errObj.get("error").getAsString();
                            }
                        }
                    } catch (Exception e) { /* use default */ }

                    // Show a proper popup with the specific error
                    new androidx.appcompat.app.AlertDialog.Builder(RegisterActivity.this)
                            .setTitle("⚠️ Registration Error")
                            .setMessage(errorMsg)
                            .setPositiveButton("OK", null)
                            .show();
                }
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                setLoading(false);
                Toast.makeText(RegisterActivity.this,
                        getString(R.string.error_network), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private String getText(TextInputEditText et) {
        return et.getText() != null ? et.getText().toString().trim() : "";
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnRegister.setEnabled(!loading);
    }
}
