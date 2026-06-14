package com.meterx.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.meterx.app.data.MeterEntity
import com.meterx.app.data.MeterRepository
import com.meterx.app.data.MeterType
import com.meterx.app.data.MeterWithReadings
import com.meterx.app.data.ReadingEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MeterViewModel(private val repository: MeterRepository) : ViewModel() {
    val meters: StateFlow<List<MeterWithReadings>> = repository.meters.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    fun addMeter(
        nickname: String,
        type: MeterType,
        meterNumber: String,
        consumerNumber: String?,
        freeUnits: Double?,
    ) = viewModelScope.launch {
        repository.addMeter(nickname, type, meterNumber, consumerNumber, freeUnits)
    }

    fun deleteMeter(meter: MeterEntity) = viewModelScope.launch {
        repository.deleteMeter(meter)
    }

    fun addReading(meter: MeterEntity, value: Double, date: Long, isBilled: Boolean) =
        viewModelScope.launch {
            repository.addReading(meter, value, date, isBilled)
        }

    fun deleteReading(reading: ReadingEntity) = viewModelScope.launch {
        repository.deleteReading(reading)
    }

    fun updateReading(
        meter: MeterEntity,
        reading: ReadingEntity,
        value: Double,
        date: Long,
        isBilled: Boolean,
    ) = viewModelScope.launch {
        repository.updateReading(meter, reading, value, date, isBilled)
    }

    fun resetFreeUnits(meter: MeterEntity, billedReading: ReadingEntity) =
        viewModelScope.launch {
            repository.resetFreeUnits(meter, billedReading)
        }

    class Factory(private val repository: MeterRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            MeterViewModel(repository) as T
    }
}
