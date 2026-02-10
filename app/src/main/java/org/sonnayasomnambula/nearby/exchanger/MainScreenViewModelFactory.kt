package org.sonnayasomnambula.nearby.exchanger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

@Suppress("UNCHECKED_CAST")
class MainScreenViewModelFactory(
    private val app: MyApplication
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return MainScreenViewModel(app.storage, app.locationProvider) as T
    }
}