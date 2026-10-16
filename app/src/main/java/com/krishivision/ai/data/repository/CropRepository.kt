package com.krishivision.ai.data.repository

import android.content.Context
import android.net.Uri
import com.krishivision.ai.data.api.GemmaApiService
import com.krishivision.ai.data.model.CropAnalysisResult
import com.krishivision.ai.data.model.CropType

class CropRepository(context: Context) {
    private val apiService = GemmaApiService(context)

    suspend fun analyzeCrop(
        cropType: CropType,
        symptoms: String,
        imageUri: Uri?
    ): Result<CropAnalysisResult> {
        return apiService.analyzeCrop(cropType, symptoms, imageUri)
    }
}
