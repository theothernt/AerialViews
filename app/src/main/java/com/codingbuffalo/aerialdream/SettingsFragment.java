package com.codingbuffalo.aerialdream;

import android.os.Bundle;
import android.support.v17.preference.LeanbackPreferenceFragment;

public class SettingsFragment extends LeanbackPreferenceFragment {
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.settings, rootKey);
    }
}
