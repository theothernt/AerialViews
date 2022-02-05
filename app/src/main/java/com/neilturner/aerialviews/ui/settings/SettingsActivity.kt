package com.neilturner.aerialviews.ui.settings

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.neilturner.aerialviews.R

class SettingsActivity :
    AppCompatActivity(),
    PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)

        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings, SettingsFragment())
                .commit()
        } else {
            title = savedInstanceState.getCharSequence("TITLE_TAG")
        }

        supportFragmentManager.addOnBackStackChangedListener {
            if (supportFragmentManager.backStackEntryCount == 0) {
                setTitle(R.string.app_name)
            }
        }

        supportActionBar?.setDisplayHomeAsUpEnabled(false)
    }

    @SuppressLint("MissingSuperCall")
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Save current activity title so we can set it again after a configuration change
        outState.putCharSequence("TITLE_TAG", title)
    }

    override fun onSupportNavigateUp(): Boolean {
        if (supportFragmentManager.popBackStackImmediate()) {
            return true
        }
        return super.onSupportNavigateUp()
    }

    override fun onPreferenceStartFragment(
        caller: PreferenceFragmentCompat,
        pref: Preference
    ): Boolean {
        // Instantiate the new Fragment
        val args = pref.extras
        val fragment = supportFragmentManager.fragmentFactory.instantiate(
            classLoader,
            pref.fragment.toString()
        ).apply {
            arguments = args
            // setTargetFragment(caller, 0)
        }
        // Replace the existing Fragment with the new Fragment
        supportFragmentManager.beginTransaction()
            .replace(R.id.settings, fragment)
            .addToBackStack(null)
            .commitAllowingStateLoss()
        title = pref.title
        return true
    }
}
