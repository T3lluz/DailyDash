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

/**
 * Every daily goal, for Settings → Goals and its row on Settings: what to eat (calories and
 * protein, in the food log's database) and what to do (steps, calories burned and sleep, in
 * [SettingsRepository.healthGoals], which Home, Health, the island and widgets all read).
 */
@HiltViewModel
class GoalsViewModel @Inject constructor(
    private val repository: MacroRepository,
    private val settings: com.macrotracker.data.local.SettingsRepository,
) : ViewModel() {

    val healthGoals: StateFlow<com.macrotracker.data.health.HealthGoals> = settings.healthGoals

    fun setHealthGoals(goals: com.macrotracker.data.health.HealthGoals) = settings.setHealthGoals(goals.clamped())

    /**
     * Steps the food goals by [calories] and [protein] and saves at once, kept inside sensible
     * ranges and on their steps, as the health goals are.
     */
    fun stepFood(calories: Int = 0, protein: Int = 0) {
        val cal = (_calGoal.value.toDecimalOrNull()?.roundToInt() ?: FOOD_CAL_DEFAULT) + calories
        val prot = (_protGoal.value.toDecimalOrNull()?.roundToInt() ?: FOOD_PROTEIN_DEFAULT) + protein
        val c = (cal.coerceIn(FOOD_CAL_RANGE) / FOOD_CAL_STEP) * FOOD_CAL_STEP
        val p = (prot.coerceIn(FOOD_PROTEIN_RANGE) / FOOD_PROTEIN_STEP) * FOOD_PROTEIN_STEP
        _calGoal.value = c.toString()
        _protGoal.value = p.toString()
        viewModelScope.launch { repository.saveGoals(c, p) }
    }

    companion object {
        const val FOOD_CAL_DEFAULT = 2000
        const val FOOD_PROTEIN_DEFAULT = 150
        val FOOD_CAL_RANGE = 800..6000
        const val FOOD_CAL_STEP = 50
        val FOOD_PROTEIN_RANGE = 20..400
        const val FOOD_PROTEIN_STEP = 5
    }

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
