package com.neilturner.aerialviews.ui

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.utils.FirebaseHelper

class MainActivity :
    AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        //navHostFragment.navController.navigate(R.id.to_mainFragment)

//        val navController = findNavController(R.id.nav_host_fragment)
//        NavigationUI.setupActionBarWithNavController(this, navController)

//        if (savedInstanceState == null) {
//            supportFragmentManager
//                .beginTransaction()
//                .replace(R.id.settings, MainFragment())
//                .commit()
//        } else {
//            title = savedInstanceState.getCharSequence("TITLE_TAG")
//        }

//        supportFragmentManager.addOnBackStackChangedListener {
//            if (supportFragmentManager.backStackEntryCount == 0) {
//                setTitle(R.string.app_name)
//            }
//        }
//
//        supportActionBar?.setDisplayHomeAsUpEnabled(false)
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

//    override fun onSupportNavigateUp(): Boolean {
//        if (supportFragmentManager.popBackStackImmediate()) {
//            return true
//        }
//        return super.onSupportNavigateUp()
//    }
}
