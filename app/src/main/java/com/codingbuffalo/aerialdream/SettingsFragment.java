package com.codingbuffalo.aerialdream;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.support.v14.preference.PreferenceFragment;
import android.support.v7.preference.Preference;

import java.util.List;

public class SettingsFragment extends PreferenceFragment implements Preference.OnPreferenceClickListener {
    public static final String SETTINGS = "android.settings.SETTINGS";
    public static final String SCREENSAVER_SETTINGS = "android.settings.DREAM_SETTINGS";

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.settings, rootKey);
        findPreference("system_options").setOnPreferenceClickListener(this);
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        // Some devices (e.g. NVidia Shield) do not support the screensaver intent
        // Check if the intent is available and if not, open the normal preferences
        Intent intent = new Intent(SCREENSAVER_SETTINGS);
        PackageManager manager = getActivity().getPackageManager();
        List<ResolveInfo> infos = manager.queryIntentActivities(intent, 0);

        if (infos.isEmpty()) {
            intent = new Intent(SETTINGS);
        }

        startActivity(intent);
        return true;
    }
}
