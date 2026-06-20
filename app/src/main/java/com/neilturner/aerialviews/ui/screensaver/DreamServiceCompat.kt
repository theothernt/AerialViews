package com.neilturner.aerialviews.ui.screensaver

import android.service.dreams.DreamService
import androidx.annotation.CallSuper
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import org.koin.android.ext.android.getKoin
import org.koin.core.parameter.ParametersDefinition
import org.koin.core.qualifier.Qualifier

abstract class DreamServiceCompat : DreamService(), SavedStateRegistryOwner, ViewModelStoreOwner {
    @Suppress("LeakingThis")
    private val lifecycleRegistry = LifecycleRegistry(this)

    @Suppress("LeakingThis")
    private val savedStateRegistryController = SavedStateRegistryController.create(this).apply {
        performAttach()
    }

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore = ViewModelStore()
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    @CallSuper
    override fun onCreate() {
        super.onCreate()

        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()

        lifecycleRegistry.currentState = Lifecycle.State.STARTED
    }

    override fun onDreamingStopped() {
        super.onDreamingStopped()

        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    fun setContent(content: @Composable () -> Unit) {
        val view = ComposeView(this)
        view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)

        view.setViewTreeLifecycleOwner(this)
        view.setViewTreeViewModelStoreOwner(this)
        view.setViewTreeSavedStateRegistryOwner(this)

        view.setContent(content)

        setContentView(view)
    }

    inline fun <reified T : ViewModel> koinViewModel(
        qualifier: Qualifier? = null,
        noinline parameters: ParametersDefinition? = null,
    ): T {
        val factory = ViewModelFactory(getKoin(), qualifier, parameters)
        return ViewModelProvider(this, factory)[T::class.java]
    }
}

class ViewModelFactory(
    private val koin: org.koin.core.Koin,
    private val qualifier: Qualifier? = null,
    private val parameters: ParametersDefinition? = null,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return koin.get(modelClass.kotlin, qualifier, parameters) as T
    }
}
