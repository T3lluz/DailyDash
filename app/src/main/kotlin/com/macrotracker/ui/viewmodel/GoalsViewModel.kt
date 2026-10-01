package com.macrotracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.macrotracker.data.local.MacroRepository
import com.macrotracker.util.toDecimalOrNull
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

/** The daily calorie and protein goals, for Settings → Nutrition and its row on Settings. */
@HiltViewModel
class GoalsViewModel @Inject constructor(
    private val repository: MacroRepository,
) : ViewModel() {

    private val _calGoal = MutableStateFlow("2000")
    val calGoal: StateFlow<String> = _calGoal

    private val _protGoal = MutableStateFlow("150")
    val protGoal: StateFlow<String> = _protGoal

    fun setCalGoal(v: String) { _calGoal.value = v }
    fun setProtGoal(v: String) { _protGoal.value = v }

    fun loadData() {
        viewModelScope.launch {
            val goals = repository.getGoals()
            _calGoal.value = goals.calorieGoal.toString()
            _protGoal.value = goals.proteinGoal.toString()
        }
    }

    /**
     * Saves both goals when both read as a positive number. @return false otherwise, and
     * nothing is saved: an empty or mistyped box used to save 2000 / 150 without a word.
     */
    fun saveGoals(): Boolean {
        val cal = _calGoal.value.toDecimalOrNull()?.roundToInt()?.takeIf { it > 0 } ?: return false
        val prot = _protGoal.value.toDecimalOrNull()?.roundToInt()?.takeIf { it > 0 } ?: return false
        viewModelScope.launch {
            repository.saveGoals(cal, prot)
            loadData()
        }
        return true
    }
}
