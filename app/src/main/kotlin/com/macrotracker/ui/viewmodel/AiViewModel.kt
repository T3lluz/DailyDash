package com.macrotracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.macrotracker.data.local.MacroLogEntity
import com.macrotracker.data.local.MacroRepository
import com.macrotracker.data.remote.NutritionAiRepository
import com.macrotracker.data.remote.NutritionEstimate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject

sealed interface NutritionChatMessage {
    val id: String

    data class Doctor(
        override val id: String = UUID.randomUUID().toString(),
        val text: String,
        val estimate: NutritionEstimate? = null,
        val estimateLogged: Boolean = false,
        val isError: Boolean = false,
        val retryQuery: String? = null,
        val showSettingsCta: Boolean = false,
    ) : NutritionChatMessage

    data class User(
        override val id: String = UUID.randomUUID().toString(),
        val text: String,
    ) : NutritionChatMessage

    data class Typing(
        override val id: String = "typing",
    ) : NutritionChatMessage
}

@HiltViewModel
class AiViewModel @Inject constructor(
    private val aiRepo: NutritionAiRepository,
    private val macroRepo: MacroRepository,
) : ViewModel() {

    private val welcome = NutritionChatMessage.Doctor(
        id = "welcome",
        text = "Hey — I'm Clanker. Describe a meal, or use + to send a meal photo, and I'll estimate " +
            "calories and protein. Need a package label instead? Use Scan label up top.",
    )

    private val _messages = MutableStateFlow<List<NutritionChatMessage>>(listOf(welcome))
    val messages: StateFlow<List<NutritionChatMessage>> = _messages

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _loggedCount = MutableStateFlow(0)
    val loggedCount: StateFlow<Int> = _loggedCount

    val hasApiKey: Boolean get() = aiRepo.hasApiKey

    private var estimateJob: Job? = null
    private var lastMealPhotoBase64: String? = null

    fun sendFoodQuery(foodQuery: String) {
        val query = foodQuery.trim()
        if (query.isBlank()) {
            appendDoctor(
                text = "I need a food description first — even a rough one works.",
                isError = true,
            )
            return
        }
        if (_loading.value) return

        if (!aiRepo.hasApiKey) {
            _messages.update { current ->
                current.filterNot { it is NutritionChatMessage.Typing } +
                    NutritionChatMessage.User(text = query) +
                    NutritionChatMessage.Doctor(
                        text = "No AI login set. Connect Claude or add a key in Settings → AI, then try again.",
                        isError = true,
                        retryQuery = query,
                        showSettingsCta = true,
                    )
            }
            return
        }

        _messages.update { current ->
            current.filterNot { it is NutritionChatMessage.Typing } +
                NutritionChatMessage.User(text = query) +
                NutritionChatMessage.Typing()
        }
        runEstimate(retryQuery = query) {
            aiRepo.estimateNutritionWithAI(query)
        }
    }

    /** Estimate macros from a plate/meal photo (chat flow — not the label scanner). */
    fun sendMealPhoto(base64Jpeg: String) {
        if (base64Jpeg.isBlank() || _loading.value) return
        lastMealPhotoBase64 = base64Jpeg

        if (!aiRepo.hasApiKey) {
            _messages.update { current ->
                current.filterNot { it is NutritionChatMessage.Typing } +
                    NutritionChatMessage.User(text = "Meal photo") +
                    NutritionChatMessage.Doctor(
                        text = "No AI login set. Connect Claude or add a key in Settings → AI, then try again.",
                        isError = true,
                        retryQuery = MEAL_PHOTO_RETRY,
                        showSettingsCta = true,
                    )
            }
            return
        }

        _messages.update { current ->
            current.filterNot { it is NutritionChatMessage.Typing } +
                NutritionChatMessage.User(text = "Meal photo") +
                NutritionChatMessage.Typing()
        }
        runEstimate(retryQuery = MEAL_PHOTO_RETRY) {
            aiRepo.estimateNutritionFromMealImage(base64Jpeg)
        }
    }

    fun retryQuery(query: String) {
        if (query.isBlank() || _loading.value) return
        if (query == MEAL_PHOTO_RETRY) {
            lastMealPhotoBase64?.let { sendMealPhoto(it) }
            return
        }
        sendFoodQuery(query)
    }

    private fun runEstimate(
        retryQuery: String,
        block: suspend () -> NutritionEstimate,
    ) {
        _loading.value = true
        estimateJob?.cancel()
        estimateJob = viewModelScope.launch {
            try {
                val result = block()
                val reply = buildEstimateReply(result)
                _messages.update { current ->
                    current.filterNot { it is NutritionChatMessage.Typing } +
                        NutritionChatMessage.Doctor(text = reply, estimate = result)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                val message = e.message ?: "Couldn't estimate those macros. Try again?"
                _messages.update { current ->
                    current.filterNot { it is NutritionChatMessage.Typing } +
                        NutritionChatMessage.Doctor(
                            text = message,
                            isError = true,
                            retryQuery = retryQuery,
                            showSettingsCta = looksLikeSettingsError(message),
                        )
                }
            } finally {
                _loading.value = false
                estimateJob = null
            }
        }
    }

    fun cancelEstimate() {
        estimateJob?.cancel()
        estimateJob = null
        _loading.value = false
        _messages.update { current ->
            current.filterNot { it is NutritionChatMessage.Typing } +
                NutritionChatMessage.Doctor(text = "Cancelled. Ask me about another meal whenever you're ready.")
        }
    }

    fun logEstimate(messageId: String, estimate: NutritionEstimate) {
        val alreadyLogged = _messages.value.any {
            it is NutritionChatMessage.Doctor && it.id == messageId && it.estimateLogged
        }
        if (alreadyLogged) return

        viewModelScope.launch {
            try {
                val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")
                macroRepo.saveLog(
                    MacroLogEntity(
                        id = System.currentTimeMillis().toString(),
                        date = LocalDate.now().format(dateFormat),
                        foodName = "${estimate.foodName} (AI)",
                        calories = estimate.calories,
                        protein = estimate.protein,
                    ),
                )
                _messages.update { list ->
                    list.map { msg ->
                        if (msg is NutritionChatMessage.Doctor && msg.id == messageId) {
                            msg.copy(estimateLogged = true, estimate = estimate)
                        } else {
                            msg
                        }
                    } + NutritionChatMessage.Doctor(
                        text = "Logged ${estimate.foodName} (${estimate.calories} kcal · ${estimate.protein}g protein). Ask about another meal anytime.",
                    )
                }
                _loggedCount.update { it + 1 }
            } catch (e: Exception) {
                appendDoctor(
                    text = e.message ?: "Couldn't save that log. Try again.",
                    isError = true,
                )
            }
        }
    }

    fun clearChat() {
        if (_loading.value) return
        _messages.value = listOf(welcome)
        _loggedCount.value = 0
    }

    private fun appendDoctor(text: String, isError: Boolean = false) {
        _messages.update { it + NutritionChatMessage.Doctor(text = text, isError = isError) }
    }

    private fun buildEstimateReply(est: NutritionEstimate): String {
        val notes = est.notes.trim().takeIf { it.isNotEmpty() }?.let { " $it" } ?: ""
        return "Here's my take on ${est.foodName} — ${est.servingDescription}." +
            " About ${est.calories} kcal and ${est.protein}g protein.$notes" +
            " Adjust the portion below if needed, then log it."
    }

    private fun looksLikeSettingsError(message: String): Boolean {
        val lower = message.lowercase()
        return lower.contains("api key") ||
            lower.contains("settings") ||
            lower.contains("unauthorized") ||
            lower.contains("invalid")
    }

    companion object {
        private const val MEAL_PHOTO_RETRY = "__meal_photo__"
    }
}
