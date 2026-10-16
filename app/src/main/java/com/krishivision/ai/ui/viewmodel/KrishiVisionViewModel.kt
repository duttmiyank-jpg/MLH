package com.krishivision.ai.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.krishivision.ai.data.model.CropType
import com.krishivision.ai.data.model.UiState
import com.krishivision.ai.data.repository.CropRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class KrishiVisionViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CropRepository(application)

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _selectedCrop = MutableStateFlow(CropType.WHEAT)
    val selectedCrop: StateFlow<CropType> = _selectedCrop.asStateFlow()

    private val _selectedImageUri = MutableStateFlow<Uri?>(null)
    val selectedImageUri: StateFlow<Uri?> = _selectedImageUri.asStateFlow()

    private val _symptomsText = MutableStateFlow("")
    val symptomsText: StateFlow<String> = _symptomsText.asStateFlow()

    private val _validationError = MutableStateFlow<String?>(null)
    val validationError: StateFlow<String?> = _validationError.asStateFlow()

    fun selectCrop(crop: CropType) {
        _selectedCrop.value = crop
        _validationError.value = null
    }

    fun setImageUri(uri: Uri?) {
        _selectedImageUri.value = uri
        _validationError.value = null
    }

    fun setSymptomsText(text: String) {
        _symptomsText.value = text
        _validationError.value = null
    }

    fun appendQuickSymptom(suggestion: String) {
        val current = _symptomsText.value
        _symptomsText.value = if (current.isBlank()) suggestion else "$current, $suggestion"
        _validationError.value = null
    }

    fun analyzeCrop() {
        val symptoms = _symptomsText.value.trim()
        val imageUri = _selectedImageUri.value

        if (symptoms.isEmpty() && imageUri == null) {
            _validationError.value = "Kripya kam se kam fasal ki photo chuniye ya samasya likhiye!"
            return
        }

        _validationError.value = null
        _uiState.value = UiState.Loading

        viewModelScope.launch {
            val result = repository.analyzeCrop(_selectedCrop.value, symptoms, imageUri)
            result.onSuccess { analysis ->
                _uiState.value = UiState.Success(analysis)
            }.onFailure { error ->
                _uiState.value = UiState.Error(
                    error.message ?: "Anjaan truti aayi! Kripya dobara koshish karein."
                )
            }
        }
    }

    fun resetAnalysis() {
        _uiState.value = UiState.Idle
    }
}
