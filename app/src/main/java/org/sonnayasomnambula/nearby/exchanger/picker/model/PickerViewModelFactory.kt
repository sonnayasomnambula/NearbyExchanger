package org.sonnayasomnambula.nearby.exchanger.picker.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.sonnayasomnambula.nearby.exchanger.app.NearbyApplication

class PickerViewModelFactory(
    private val app: NearbyApplication
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return PickerViewModel(
            fileManager = LegacyFileManager(app.applicationContext)
        ) as T
    }
}