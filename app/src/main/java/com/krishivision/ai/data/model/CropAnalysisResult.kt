package com.krishivision.ai.data.model

enum class CropType(
    val displayNameHinglish: String,
    val englishName: String,
    val iconEmoji: String
) {
    WHEAT("Gehun (Wheat)", "Wheat", "🌾"),
    RICE("Chawal (Rice)", "Rice", "🌾"),
    TOMATO("Tamatar (Tomato)", "Tomato", "🍅"),
    POTATO("Aloo (Potato)", "Potato", "🥔"),
    OTHER("Anya Fasal (Other)", "Other Crop", "🌱")
}

data class CropAnalysisResult(
    val detectedIssue: String,
    val possibleCauses: List<String>,
    val remedies: List<String>,
    val nextSteps: String,
    val rawResponse: String = ""
)

sealed interface UiState {
    object Idle : UiState
    object Loading : UiState
    data class Success(val result: CropAnalysisResult) : UiState
    data class Error(val message: String) : UiState
}
