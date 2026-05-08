package org.sonnayasomnambula.nearby.exchanger.main.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import org.sonnayasomnambula.nearby.exchanger.app.NearbyApplication

@Suppress("UNCHECKED_CAST")
class MainViewModelFactory(
    private val app: NearbyApplication
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        val savedStateHandle = extras.createSavedStateHandle()
        return MainViewModel(
            keeper = SavedStateKeeper(savedStateHandle),
            storage = app.storage,
            directoryProvider = app.directoryProvider,
            permissionPolicy = app.permissionPolicy
        ) as T
    }
}