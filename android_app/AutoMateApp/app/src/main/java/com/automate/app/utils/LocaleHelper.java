package com.automate.app.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;

import java.util.Locale;

/**
 * Utility to change the app's language at runtime.
 * Supports: English (en), Hindi (hi), Marathi (mr).
 */
public class LocaleHelper {

    private static final String PREF_NAME = "AutoMateSettings";
    private static final String KEY_LANGUAGE = "app_language";
    private static final String KEY_DARK_MODE = "dark_mode";

    /**
     * Wraps the context with the saved locale so all getString() calls
     * return the correct language.
     */
    public static Context applyLocale(Context context) {
        String lang = getSavedLanguage(context);
        return updateResources(context, lang);
    }

    /**
     * Persists the chosen language code and applies it.
     */
    public static Context setLocale(Context context, String languageCode) {
        saveLanguage(context, languageCode);
        return updateResources(context, languageCode);
    }

    public static String getSavedLanguage(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_LANGUAGE, "en"); // default English
    }

    private static void saveLanguage(Context context, String languageCode) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_LANGUAGE, languageCode).apply();
    }

    private static Context updateResources(Context context, String languageCode) {
        Locale locale = new Locale(languageCode);
        Locale.setDefault(locale);

        Resources resources = context.getResources();
        Configuration config = new Configuration(resources.getConfiguration());
        config.setLocale(locale);

        return context.createConfigurationContext(config);
    }

    // --- Dark Mode helpers ---

    public static boolean isDarkMode(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_DARK_MODE, true); // default dark
    }

    public static void setDarkMode(Context context, boolean enabled) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_DARK_MODE, enabled).apply();
    }
}
