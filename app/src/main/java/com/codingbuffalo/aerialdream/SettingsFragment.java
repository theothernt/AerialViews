package com.codingbuffalo.aerialdream;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.support.v14.preference.PreferenceFragment;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;

import java.util.List;

public class SettingsFragment extends PreferenceFragment implements Preference.OnPreferenceClickListener, SharedPreferences.OnSharedPreferenceChangeListener {
    public static final String SETTINGS = "android.settings.SETTINGS";
    public static final String SCREENSAVER_SETTINGS = "android.settings.DREAM_SETTINGS";

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.settings, rootKey);
        findPreference("system_options").setOnPreferenceClickListener(this);

        getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);

        updateSummaries();
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        // Check if the daydream intent is available - some devices (e.g. NVidia Shield) do not support it
        Intent intent = new Intent(SCREENSAVER_SETTINGS);
        if (!intentAvailable(intent)) {
            // Try opening the daydream settings activity directly: https://gist.github.com/reines/bc798a2cb539f51877bb279125092104
            intent = new Intent(Intent.ACTION_MAIN).setClassName("com.android.tv.settings", "com.android.tv.settings.device.display.daydream.DaydreamActivity");
            if (!intentAvailable(intent)) {
                // If all else fails, open the normal settings screen
                intent = new Intent(SETTINGS);
            }
        }

        startActivity(intent);
        return true;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        updateSummaries();
    }

    private void updateSummaries() {
        ListPreference pref = (ListPreference) findPreference("source_apple_2015");
        pref.setSummary(pref.getEntry());
        pref = (ListPreference) findPreference("source_apple_2017");
        pref.setSummary(pref.getEntry());
        pref = (ListPreference) findPreference("cache_size");
        pref.setSummary(pref.getEntry());
    }

    private boolean intentAvailable(Intent intent) {
        PackageManager manager = getActivity().getPackageManager();
        List<ResolveInfo> infos = manager.queryIntentActivities(intent, 0);
        return !infos.isEmpty();
    }
}
