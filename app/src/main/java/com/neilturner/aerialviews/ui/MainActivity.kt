package com.neilturner.aerialviews.ui

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.utils.FirebaseHelper
import timber.log.Timber

class MainActivity :
    AppCompatActivity(),
    PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {

    private val fragments = mutableMapOf<String, Fragment>()
    private lateinit var stateHelper: FragmentStateHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)

        stateHelper = FragmentStateHelper(supportFragmentManager)

        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                replace(R.id.settings, MainFragment())
                addToBackStack(null)
            }
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

        val fragmentName = pref.fragment.toString()
        val fragment = fragments[fragmentName] ?: supportFragmentManager.fragmentFactory
            .instantiate(
                classLoader,
                fragmentName
            ).apply {
                arguments = pref.extras
            }
        fragments[fragmentName] = fragment

        Timber.d("onPreferenceStartFragment - Fragment: ${caller.toString()}")
        Timber.i(fragments.toString())

        fragments.forEach { fragment ->
            saveCurrentState(fragment.value)
        }

        supportFragmentManager.commit {
            replace(R.id.settings, fragment)
            addToBackStack(null)
        }

        title = pref.title
        return true
    }

    private fun saveCurrentState(fragment: Fragment) {
        fragments[fragment.toString()]?.let { oldFragment->
            stateHelper.saveState(oldFragment, fragment.toString())
        }
    }
}
