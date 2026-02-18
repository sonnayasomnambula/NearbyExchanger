package org.sonnayasomnambula.nearby.exchanger.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.sonnayasomnambula.nearby.exchanger.app.MyApplication

@Suppress("UNCHECKED_CAST")
class MainScreenViewModelFactory(
    private val app: MyApplication
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return MainScreenViewModel(app.storage, app.locationProvider) as T
    }
}