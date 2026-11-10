package com.neilturner.aerialviews.ui.screensaver

import android.service.dreams.DreamService
import androidx.annotation.CallSuper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner

/**
 * Adds the three owners Compose needs to a [DreamService] without adding any Compose types.
 *
 * The reference implementation this is based on never reaches [Lifecycle.State.DESTROYED] and
 * never clears its [ViewModelStore]. Both matter here: the composition must be disposed after the
 * dream ends, and `ViewModel.onCleared()` is what releases the shared controller.
 */
abstract class DreamServiceCompat :
    DreamService(),
    SavedStateRegistryOwner,
    ViewModelStoreOwner {
    @Suppress("LeakingThis")
    private val lifecycleRegistry = LifecycleRegistry(this)

    @Suppress("LeakingThis")
    private val savedStateRegistryController =
        SavedStateRegistryController.create(this).apply {
            performAttach()
        }

    private var currentViewModelStore = ViewModelStore()

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    override val viewModelStore: ViewModelStore
        get() = currentViewModelStore

    @CallSuper
    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    @CallSuper
    override fun onDreamingStarted() {
        super.onDreamingStarted()
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
    }

    @CallSuper
    override fun onDreamingStopped() {
        super.onDreamingStopped()
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    @CallSuper
    override fun onDetachedFromWindow() {
        // DreamActivity can be attached again on the same service instance. Clear the old session
        // before the next UI asks for a ViewModel from this owner.
        currentViewModelStore.clear()
        super.onDetachedFromWindow()
    }

    @CallSuper
    override fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        currentViewModelStore.clear()
        super.onDestroy()
    }
}
