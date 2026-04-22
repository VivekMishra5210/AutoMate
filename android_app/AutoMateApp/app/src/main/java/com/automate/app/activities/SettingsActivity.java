package com.automate.app.activities;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.RadioGroup;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.automate.app.R;
import com.automate.app.utils.LocaleHelper;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.switchmaterial.SwitchMaterial;

/**
 * Settings screen for language selection and dark/light mode toggle.
 */
public class SettingsActivity extends AppCompatActivity {

    private RadioGroup rgLanguage;
    private SwitchMaterial switchDarkMode;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // Toolbar back
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        rgLanguage = findViewById(R.id.rgLanguage);
        switchDarkMode = findViewById(R.id.switchDarkMode);

        // --- Load saved language ---
        String currentLang = LocaleHelper.getSavedLanguage(this);
        switch (currentLang) {
            case "hi":
                rgLanguage.check(R.id.rbHindi);
                break;
            case "mr":
                rgLanguage.check(R.id.rbMarathi);
                break;
            default:
                rgLanguage.check(R.id.rbEnglish);
                break;
        }

        // --- Load saved dark mode ---
        boolean isDark = LocaleHelper.isDarkMode(this);
        switchDarkMode.setChecked(isDark);

        // --- Language change listener ---
        rgLanguage.setOnCheckedChangeListener((group, checkedId) -> {
            String langCode;
            if (checkedId == R.id.rbHindi) {
                langCode = "hi";
            } else if (checkedId == R.id.rbMarathi) {
                langCode = "mr";
            } else {
                langCode = "en";
            }

            String savedLang = LocaleHelper.getSavedLanguage(this);
            if (!langCode.equals(savedLang)) {
                LocaleHelper.setLocale(this, langCode);
                // Restart the entire app so all screens pick up the new language
                restartApp();
            }
        });

        // --- Dark mode toggle listener ---
        switchDarkMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            LocaleHelper.setDarkMode(this, isChecked);
            if (isChecked) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            }
        });
    }

    /**
     * Restarts the app from the Login screen so the new locale is applied everywhere.
     */
    private void restartApp() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
