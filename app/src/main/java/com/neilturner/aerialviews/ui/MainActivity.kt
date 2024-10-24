package com.neilturner.aerialviews.ui

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.utils.FirebaseHelper

class MainActivity :
    AppCompatActivity(),
    PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {

    private val fragmentStates = mutableMapOf<String, Bundle>()

    private val lifecycleCallbacks = object : FragmentManager.FragmentLifecycleCallbacks() {
        override fun onFragmentPaused(fm: FragmentManager, fragment: Fragment) {
            //saveFragmentState(fragment)
        }

        override fun onFragmentResumed(fm: FragmentManager, f: Fragment) {
            // Restore state when fragment is created
            restoreFragmentState(f)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)

        supportFragmentManager.registerFragmentLifecycleCallbacks(lifecycleCallbacks, false)

        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings, MainFragment())
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

    private fun saveFragmentState(fragment: Fragment) {
        val state = Bundle()

        // Default state saving for regular fragments
        fragment.onSaveInstanceState(state)

        // Store the state with a unique key
        fragmentStates[getFragmentKey(fragment)] = state
    }

    private fun restoreFragmentState(fragment: Fragment) {
        val state = fragmentStates[getFragmentKey(fragment)]
        if (state != null) {
            fragment.arguments = fragment.arguments?.apply {
                putAll(state)
            } ?: state
        }
    }

    private fun getFragmentKey(fragment: Fragment): String {
        // Create a unique key based on fragment class and tag
        return "${fragment.javaClass.name}_${fragment.tag ?: fragment.id}"
    }

    override fun onResume() {
        super.onResume()
        FirebaseHelper.logScreenView("Main", this)
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
        pref: Preference,
    ): Boolean {
        // Instantiate the new Fragment
        val args = pref.extras
        val fragment =
            supportFragmentManager.fragmentFactory
                .instantiate(
                    classLoader,
                    pref.fragment.toString(),
                ).apply {
                    arguments = args
                }
        // Replace the existing Fragment with the new Fragment
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.settings, fragment)
            .addToBackStack(null)
            .commitAllowingStateLoss()
        title = pref.title
        return true
    }
}
