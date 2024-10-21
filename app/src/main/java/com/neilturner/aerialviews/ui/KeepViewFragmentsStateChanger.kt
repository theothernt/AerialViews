package com.neilturner.aerialviews.ui

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import com.zhuinden.simplestackextensions.fragments.DefaultFragmentStateChanger

class KeepViewFragmentStateChanger(fragmentManager: FragmentManager, containerId: Int) :
    DefaultFragmentStateChanger(fragmentManager, containerId) {

    override fun startShowing(fragmentTransaction: FragmentTransaction, fragment: Fragment) {
        fragmentTransaction.show(fragment)
    }

    override fun stopShowing(fragmentTransaction: FragmentTransaction, fragment: Fragment) {
        fragmentTransaction.hide(fragment)
    }

    protected override fun isNotShowing(fragment: Fragment): Boolean {
        return fragment.isHidden
    }
}