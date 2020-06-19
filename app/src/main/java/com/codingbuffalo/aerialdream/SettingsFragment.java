package com.codingbuffalo.aerialdream;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import androidx.core.content.ContextCompat;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.codingbuffalo.aerialdream.data.VideoSource;
import java.util.List;

public class SettingsFragment extends PreferenceFragmentCompat implements Preference.OnPreferenceClickListener, SharedPreferences.OnSharedPreferenceChangeListener {
    public static final String SETTINGS = "android.settings.SETTINGS";
    public static final String SCREENSAVER_SETTINGS = "android.settings.DREAM_SETTINGS";
    public static final String VIDEO_SOURCE = "video_source";
    public static final String SOURCE_APPLE_2019 = "source_apple_2019";
    public static final int PERMISSION_READ_EXTERNAL_STORAGE = 1;

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
        if (key.equals(VIDEO_SOURCE)) {
            checkUserPermission(sharedPreferences);
        } else {
            updateSummaries();
        }
    }

    public void checkUserPermission(SharedPreferences sharedPreferences) {
        int pref = Integer.parseInt(sharedPreferences.getString(VIDEO_SOURCE, "0"));
        if (pref != VideoSource.REMOTE && !hasStoragePermission()) {
            requestPermissions(
                    new String[] {Manifest.permission.READ_EXTERNAL_STORAGE},
                    PERMISSION_READ_EXTERNAL_STORAGE);
        } else {
            updateSummaries();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_READ_EXTERNAL_STORAGE:
                if ((grantResults.length > 0) && (grantResults[0] == PackageManager.PERMISSION_DENIED)) {
                    resetVideoSource();
                } else {
                    updateSummaries();
                }
                break;
        }
    }

    private boolean hasStoragePermission() {
        return (ContextCompat.checkSelfPermission(this.getActivity(),
                Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED);
    }

    private void resetVideoSource() {
        ListPreference pref = (ListPreference) findPreference(VIDEO_SOURCE);
        pref.setValue("0");
    }

    private void updateSummaries() {
        ListPreference pref = (ListPreference) findPreference(SOURCE_APPLE_2019);
        pref.setSummary(pref.getEntry());

        pref = (ListPreference) findPreference(VIDEO_SOURCE);
        pref.setSummary(pref.getEntry());
    }

    private boolean intentAvailable(Intent intent) {
        PackageManager manager = getActivity().getPackageManager();
        List<ResolveInfo> info = manager.queryIntentActivities(intent, 0);
        return !info.isEmpty();
    }
}
