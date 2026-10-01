package com.macrotracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import java.util.Locale
import com.macrotracker.util.toDecimalOrNull
import androidx.lifecycle.viewModelScope
import com.macrotracker.data.local.MacroLogEntity
import com.macrotracker.data.local.MacroRepository
import com.macrotracker.data.remote.NutritionAiRepository
import com.macrotracker.data.remote.ScanResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToLong
import javax.inject.Inject

enum class ScanPhase { CAMERA, PREVIEW, RESULT }

data class LogSummary(
    val foodName: String,
    val caloriesPerServing: Int,
    val proteinPerServing: Double,
    val servingsPerContainer: Double,
    val servingSizeGrams: Int,
    val packageWeightGrams: Int,
    val loggedCalories: Int,
    val loggedProtein: Int,
    val multiplier: Double
)

@HiltViewModel
class CameraScanViewModel @Inject constructor(
    private val aiRepo: NutritionAiRepository,
    private val macroRepo: MacroRepository,
) : ViewModel() {

    private val _phase = MutableStateFlow(ScanPhase.CAMERA)
    val phase: StateFlow<ScanPhase> = _phase

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning

    private val _result = MutableStateFlow<ScanResult?>(null)
    val result: StateFlow<ScanResult?> = _result

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    // Override fields
    private val _foodNameOverride = MutableStateFlow("")
    val foodNameOverride: StateFlow<String> = _foodNameOverride

    private val _caloriesOverride = MutableStateFlow("")
    val caloriesOverride: StateFlow<String> = _caloriesOverride

    private val _proteinOverride = MutableStateFlow("")
    val proteinOverride: StateFlow<String> = _proteinOverride

    private val _servingsOverride = MutableStateFlow("")
    val servingsOverride: StateFlow<String> = _servingsOverride

    private val _servingSizeOverride = MutableStateFlow("")
    val servingSizeOverride: StateFlow<String> = _servingSizeOverride

    private val _packageWeightOverride = MutableStateFlow("")
    val packageWeightOverride: StateFlow<String> = _packageWeightOverride

    // Eaten fields
    private val _amountEaten = MutableStateFlow("1")
    val amountEaten: StateFlow<String> = _amountEaten

    private val _unitEaten = MutableStateFlow("servings")
    val unitEaten: StateFlow<String> = _unitEaten

    val units = listOf("servings", "packages", "g", "ml", "dl", "L", "kg")

    fun setFoodNameOverride(v: String) { _foodNameOverride.value = v }
    fun setCaloriesOverride(v: String) { _caloriesOverride.value = v }
    fun setProteinOverride(v: String) { _proteinOverride.value = v }
    fun setServingsOverride(v: String) { _servingsOverride.value = v }
    fun setServingSizeOverride(v: String) { _servingSizeOverride.value = v }
    fun setPackageWeightOverride(v: String) { _packageWeightOverride.value = v }

    fun setAmountEaten(v: String) { _amountEaten.value = v }
    fun setUnitEaten(v: String) { _unitEaten.value = v }

    fun setPhase(p: ScanPhase) { _phase.value = p }

    val hasApiKey: Boolean get() = aiRepo.hasApiKey

    private var analyzeJob: Job? = null

    fun analyzeImage(base64: String) {
        analyzeJob?.cancel()
        _scanning.value = true
        _error.value = null
        analyzeJob = viewModelScope.launch {
            try {
                val scanResult = aiRepo.analyzeNutritionLabelImage(base64)
                _result.value = scanResult
                _foodNameOverride.value = if (scanResult.foodName == "Scanned Food") "" else scanResult.foodName
                _caloriesOverride.value = formatNum(scanResult.caloriesPerServing)
                _proteinOverride.value = formatNum(scanResult.proteinPerServing)
                _servingsOverride.value = if (scanResult.servingsPerContainer > 0) formatDouble(scanResult.servingsPerContainer) else "1"
                _servingSizeOverride.value = formatNum(scanResult.servingSizeGrams)
                _packageWeightOverride.value = formatNum(scanResult.packageWeightGrams)

                // Defaults for consumption
                _amountEaten.value = "1"
                _unitEaten.value = "servings"

                _phase.value = ScanPhase.RESULT
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _error.value = e.message ?: "Scan failed."
            } finally {
                _scanning.value = false
                analyzeJob = null
            }
        }
    }

    /** Cancel an in-flight label analysis so the user is never stuck waiting on OkHttp. */
    fun cancelAnalyze() {
        analyzeJob?.cancel()
        analyzeJob = null
        _scanning.value = false
    }

    fun getLogSummary(
        foodNameStr: String,
        calsStr: String,
        protStr: String,
        servsStr: String,
        servSizeStr: String,
        pkgWeightStr: String,
        amtStr: String,
        unitStr: String
    ): LogSummary? {
        val r = _result.value ?: return null
        
        val cals = calsStr.toDecimalOrNull()?.toInt()?.coerceAtLeast(0) ?: r.caloriesPerServing
        // Protein can be a decimal, so we keep it as a double to avoid issues when the user types a decimal
        val prot = protStr.toDecimalOrNull()?.coerceAtLeast(0.0) ?: r.proteinPerServing.toDouble()
        
        val fallbackServings = if (r.servingsPerContainer > 0) r.servingsPerContainer else 1.0
        var finalServs = servsStr.toDecimalOrNull()?.coerceAtLeast(0.0) ?: fallbackServings
        var finalServSize = servSizeStr.toDecimalOrNull()?.toInt()?.coerceAtLeast(0) ?: r.servingSizeGrams
        var finalPkgWeight = pkgWeightStr.toDecimalOrNull()?.toInt()?.coerceAtLeast(0) ?: r.packageWeightGrams

        if (finalServs <= 0 && finalServSize > 0 && finalPkgWeight > 0) {
            finalServs = finalPkgWeight.toDouble() / finalServSize
        }
        if (finalServSize <= 0 && finalServs > 0 && finalPkgWeight > 0) {
            finalServSize = (finalPkgWeight / finalServs).toInt()
        }
        if (finalPkgWeight <= 0 && finalServs > 0 && finalServSize > 0) {
            finalPkgWeight = (finalServs * finalServSize).toInt()
        }

        val amt = amtStr.toDecimalOrNull()?.coerceAtLeast(0.0) ?: 0.0
        
        val multiplier = when (unitStr) {
            "servings" -> amt
            "packages" -> amt * finalServs
            "g", "ml" -> if (finalServSize > 0) amt / finalServSize else 0.0
            "kg", "L" -> if (finalServSize > 0) (amt * 1000) / finalServSize else 0.0
            "dl" -> if (finalServSize > 0) (amt * 100) / finalServSize else 0.0
            else -> amt
        }

        return LogSummary(
            foodName = foodNameStr.ifBlank { r.foodName },
            caloriesPerServing = cals,
            proteinPerServing = prot,
            servingsPerContainer = finalServs,
            servingSizeGrams = finalServSize,
            packageWeightGrams = finalPkgWeight,
            // Calculate correctly and round to avoid strange decimal point behaviour with the multiplier
            loggedCalories = (cals * multiplier).roundToLong().toInt(),
            loggedProtein = (prot * multiplier).roundToLong().toInt(),
            multiplier = multiplier
        )
    }

    /** Emits true once after a successful log so the UI can navigate away. */
    private val _loggedEvent = MutableStateFlow(false)
    val loggedEvent: StateFlow<Boolean> = _loggedEvent
    fun consumeLoggedEvent() { _loggedEvent.value = false }

    fun saveScannedFood(summary: LogSummary) {
        viewModelScope.launch {
            val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            macroRepo.saveLog(
                MacroLogEntity(
                    id = System.currentTimeMillis().toString(),
                    date = LocalDate.now().format(dateFormat),
                    foodName = "${summary.foodName} (Scan)",
                    calories = summary.loggedCalories,
                    protein = summary.loggedProtein,
                )
            )
            _loggedEvent.value = true
        }
    }

    fun resetForNewScan() {
        _phase.value = ScanPhase.CAMERA
        _result.value = null
        _error.value = null
        _foodNameOverride.value = ""
        _caloriesOverride.value = ""
        _proteinOverride.value = ""
        _servingsOverride.value = ""
        _servingSizeOverride.value = ""
        _packageWeightOverride.value = ""
        _amountEaten.value = "1"
        _unitEaten.value = "servings"
    }

    private fun formatNum(v: Int): String = if (v > 0) v.toString() else ""
    private fun formatDouble(v: Double): String = if (v > 0) {
        if (v == v.toLong().toDouble()) v.toLong().toString() else String.format(Locale.US, "%.2f", v)
    } else ""
}
