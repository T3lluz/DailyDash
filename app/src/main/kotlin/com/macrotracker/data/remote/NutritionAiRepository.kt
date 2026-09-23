package com.macrotracker.data.remote

import com.macrotracker.data.local.SettingsRepository
import okhttp3.OkHttpClient
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

data class NutritionEstimate(
    val foodName: String,
    val servingDescription: String,
    val calories: Int,
    val protein: Int,
    val confidence: String,   // "low", "medium", "high"
    val notes: String,
)

data class ScanResult(
    val foodName: String,
    val caloriesPerServing: Int,
    val proteinPerServing: Int,
    val servingsPerContainer: Double,
    val servingSizeGrams: Int,
    val packageWeightGrams: Int,
    val totalCalories: Int,
    val totalProtein: Int,
)

@Singleton
class NutritionAiRepository @Inject constructor(
    private val httpClient: OkHttpClient,
    private val settings: SettingsRepository,
    private val credentials: AiCredentialResolver,
) {
    private val provider: AiProvider
        get() = settings.getAiProvider()

    val hasApiKey: Boolean get() = credentials.hasCredentials()

    // ─── Estimate nutrition from text ─────────────────────────────────────────
    suspend fun estimateNutritionWithAI(foodQuery: String): NutritionEstimate {
        if (foodQuery.isBlank()) throw Exception("Enter a food to estimate first.")
        val auth = requireAuth()

        val prompt = """
            Estimate nutrition values for this food query: "$foodQuery".
            Use common nutrition databases and practical serving assumptions.
            Return ONLY a JSON object with this exact shape:
            {
              "foodName": "string",
              "servingDescription": "string",
              "calories": number,
              "protein": number,
              "confidence": "low" | "medium" | "high",
              "notes": "brief caveat"
            }
            Rules:
            - Calories and protein must be non-negative numbers.
            - If uncertain, provide best estimate and set confidence accordingly.
            - Keep notes under 120 characters.
        """.trimIndent()

        val responseText = AiApiClient.generate(
            httpClient = httpClient,
            provider = provider,
            apiKey = auth.secret,
            params = AiApiClient.GenerateParams(
                prompt = prompt,
                temperature = 0.2,
                maxOutputTokens = 1024,
                jsonMode = true,
                openRouterModelId = settings.getOpenRouterModelId(),
                anthropicModelId = settings.getAnthropicModelId(),
                anthropicOAuth = auth.anthropicOAuth,
            ),
        )
        return parseNutritionEstimate(responseText, foodQuery)
    }

    // ─── Estimate nutrition from a meal photo (not a label) ───────────────────
    suspend fun estimateNutritionFromMealImage(base64Image: String): NutritionEstimate {
        val auth = requireAuth()

        val prompt = """
            Look at this photo of a prepared meal or food on a plate/bowl.
            Identify what was eaten and estimate calories and protein for the portion shown.
            Do NOT read nutrition facts labels — ignore packaging text if present.
            Return ONLY a JSON object with this exact shape:
            {
              "foodName": "string",
              "servingDescription": "string",
              "calories": number,
              "protein": number,
              "confidence": "low" | "medium" | "high",
              "notes": "brief caveat"
            }
            Rules:
            - Estimate for the visible portion, not a whole package.
            - Calories and protein must be non-negative numbers.
            - If the image is unclear or not food, still return best-effort JSON with low confidence.
            - Keep notes under 120 characters.
        """.trimIndent()

        val responseText = AiApiClient.generate(
            httpClient = httpClient,
            provider = provider,
            apiKey = auth.secret,
            params = AiApiClient.GenerateParams(
                prompt = prompt,
                base64Jpeg = base64Image,
                temperature = 0.2,
                maxOutputTokens = 1024,
                jsonMode = true,
                openRouterModelId = settings.getOpenRouterModelId(),
                anthropicModelId = settings.getAnthropicModelId(),
                anthropicOAuth = auth.anthropicOAuth,
            ),
        )
        return parseNutritionEstimate(responseText, "Meal photo")
    }

    suspend fun analyzeNutritionLabelImage(base64Image: String): ScanResult {
        val auth = requireAuth()

        val prompt = """
            Read the nutrition facts label in this image.
            Return ONLY JSON with these keys:
            {
              "foodName": string,
              "caloriesPerServing": number,
              "proteinPerServing": number,
              "servingsPerContainer": number,
              "servingSizeGrams": number,
              "packageWeightGrams": number
            }
            Use 0 for missing numbers. No markdown. No explanation.
        """.trimIndent()

        val responseText = AiApiClient.generate(
            httpClient = httpClient,
            provider = provider,
            apiKey = auth.secret,
            params = AiApiClient.GenerateParams(
                prompt = prompt,
                base64Jpeg = base64Image,
                temperature = 0.2,
                maxOutputTokens = 1024,
                jsonMode = true,
                openRouterModelId = settings.getOpenRouterModelId(),
                anthropicModelId = settings.getAnthropicModelId(),
                anthropicOAuth = auth.anthropicOAuth,
            ),
        )
        return parseScanResult(responseText)
    }

    private suspend fun requireAuth(): ResolvedAiAuth {
        val auth = credentials.resolve()
        if (!auth.isBlank) return auth
        val selected = provider
        val hint = when (selected) {
            AiProvider.GEMINI ->
                "No Gemini API key set. Go to Settings, choose Gemini, and paste your free key from aistudio.google.com."
            AiProvider.OPENAI ->
                "No OpenAI API key set. Go to Settings, choose OpenAI, and paste your key from platform.openai.com."
            AiProvider.OPENROUTER ->
                "No OpenRouter API key set. Go to Settings, choose OpenRouter, and paste your key from openrouter.ai/keys."
            AiProvider.ANTHROPIC ->
                "Claude is not connected. Go to Settings → AI, choose Claude, and tap Connect Claude (or paste an API key)."
        }
        throw Exception(hint)
    }

    // ─── JSON Parsing helpers ────────────────────────────────────────────────
    private fun parseNutritionEstimate(rawText: String, fallbackName: String): NutritionEstimate {
        val cleaned = rawText.replace("```json", "").replace("```", "").trim()
        val json = extractJsonObject(cleaned)
        return try {
            val obj = JSONObject(json)
            NutritionEstimate(
                foodName = obj.optString("foodName", fallbackName),
                servingDescription = obj.optString("servingDescription", "1 serving"),
                calories = maxOf(0, obj.optDouble("calories", 0.0).toInt()),
                protein = maxOf(0, obj.optDouble("protein", 0.0).toInt()),
                confidence = normalizeConfidence(obj.optString("confidence", "medium")),
                notes = obj.optString("notes", "Estimate only. Verify with package label when possible."),
            )
        } catch (_: Exception) {
            throw Exception("Could not parse the AI estimate. Please try again.")
        }
    }

    private fun parseScanResult(rawText: String): ScanResult {
        val cleaned = rawText.replace("```json", "").replace("```", "").trim()
        val json = extractJsonObject(cleaned)
        return try {
            val obj = JSONObject(json)
            maybeComputeTotals(
                foodName = obj.optString("foodName", "Scanned Food"),
                caloriesPerServing = maxOf(0, obj.optDouble("caloriesPerServing", 0.0).toInt()),
                proteinPerServing = maxOf(0, obj.optDouble("proteinPerServing", 0.0).toInt()),
                servingsPerContainer = maxOf(0.0, obj.optDouble("servingsPerContainer", 0.0)),
                servingSizeGrams = maxOf(0, obj.optDouble("servingSizeGrams", 0.0).toInt()),
                packageWeightGrams = maxOf(0, obj.optDouble("packageWeightGrams", 0.0).toInt()),
            )
        } catch (_: Exception) {
            throw Exception("Could not parse AI response. Please retake the photo and try again.")
        }
    }

    private fun extractJsonObject(text: String): String {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        return if (start != -1 && end != -1 && end > start) text.substring(start, end + 1) else text
    }

    private fun normalizeConfidence(value: String): String {
        return when (value.trim().lowercase()) {
            "low", "medium", "high" -> value.trim().lowercase()
            else -> "medium"
        }
    }
}

fun maybeComputeTotals(
    foodName: String,
    caloriesPerServing: Int,
    proteinPerServing: Int,
    servingsPerContainer: Double,
    servingSizeGrams: Int,
    packageWeightGrams: Int,
): ScanResult {
    var servings = servingsPerContainer
    var servingSize = servingSizeGrams
    var packageWeight = packageWeightGrams

    if (servings <= 0 && servingSize > 0 && packageWeight > 0) {
        servings = packageWeight.toDouble() / servingSize
    }
    if (servingSize <= 0 && servings > 0 && packageWeight > 0) {
        servingSize = (packageWeight / servings).toInt()
    }
    if (packageWeight <= 0 && servings > 0 && servingSize > 0) {
        packageWeight = (servings * servingSize).toInt()
    }

    return ScanResult(
        foodName = foodName.ifBlank { "Scanned Food" },
        caloriesPerServing = caloriesPerServing,
        proteinPerServing = proteinPerServing,
        servingsPerContainer = servings,
        servingSizeGrams = servingSize,
        packageWeightGrams = packageWeight,
        totalCalories = (caloriesPerServing * servings).toInt(),
        totalProtein = (proteinPerServing * servings).toInt(),
    )
}
