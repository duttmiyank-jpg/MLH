package com.krishivision.ai.data.api

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.krishivision.ai.BuildConfig
import com.krishivision.ai.data.model.CropAnalysisResult
import com.krishivision.ai.data.model.CropType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

class GemmaApiService(private val context: Context) {

    // Strictly target Gemma 4 model ID
    private val modelId = "gemma-4-31b-it"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun analyzeCrop(
        cropType: CropType,
        symptoms: String,
        imageUri: Uri?
    ): Result<CropAnalysisResult> = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMMA_API_KEY
        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                Exception("Gemma API Key local.properties me nahi mila! Kripya GEMMA_API_KEY set karein.")
            )
        }

        val base64Image = if (imageUri != null) uriToBase64(imageUri) else null
        val promptText = buildPrompt(cropType, symptoms, base64Image != null)
        val requestBodyJson = buildRequestBody(promptText, base64Image)
        val mediaType = "application/json; charset=utf-8".toMediaType()

        val endpointUrl = "https://generativelanguage.googleapis.com/v1beta/models/$modelId:generateContent?key=$apiKey"

        try {
            val requestBody = requestBodyJson.toString().toRequestBody(mediaType)
            val request = Request.Builder()
                .url(endpointUrl)
                .post(requestBody)
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBodyString = response.body?.string() ?: ""

            if (response.isSuccessful && responseBodyString.isNotBlank()) {
                val parsedResult = parseGemmaResponse(responseBodyString)
                if (parsedResult != null) {
                    Result.success(parsedResult)
                } else {
                    Result.failure(Exception("Gemma 4 AI response ko parse nahi kiya ja saka."))
                }
            } else {
                val errorMsg = parseErrorMessage(response.code, responseBodyString)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Network error ($modelId): ${e.localizedMessage ?: e.message}"))
        }
    }

    private fun buildPrompt(cropType: CropType, symptoms: String, hasImage: Boolean): String {
        return """
            Aap ek expert krishi (agricultural) AI sahayak hain.
            Kisan ne apni fasal ka vivran diya hai:
            - Fasal: ${cropType.displayNameHinglish}
            - Kehna/Symptoms: $symptoms
            - Photo di gayi hai: ${if (hasImage) "Haan (image attached)" else "Nahi"}

            Kripya niche diye gaye format me HI Hinglish (Hindi text in Roman alphabet) me jawab dein.
            Bohot hi saral aur helpful bhasha me kisan ko salah dein.

            [SAMASYA]
            (Fasal ki samasya ya rog ka spasht vivran 1-2 line me Hinglish me)

            [KARAN]
            - (Point 1: Rog ya samasya hone ka karan Hinglish me)
            - (Point 2: Doosra karan agar ho toh)

            [UPAY]
            - (Point 1: Fasal ko bachane ke sateek upay ya dawai Hinglish me)
            - (Point 2: Doosra upay)

            [AGLA_KADAM]
            (Kisan ko abhi turant kya karna chahiye - Hinglish me short salah)
        """.trimIndent()
    }

    private fun buildRequestBody(promptText: String, base64Image: String?): JSONObject {
        val partsArray = JSONArray()

        // Text part
        partsArray.put(JSONObject().apply {
            put("text", promptText)
        })

        // Image inline data part (if present)
        if (base64Image != null) {
            val inlineData = JSONObject().apply {
                put("mimeType", "image/jpeg")
                put("data", base64Image)
            }
            partsArray.put(JSONObject().apply {
                put("inlineData", inlineData)
            })
        }

        val contentsArray = JSONArray().apply {
            put(JSONObject().apply {
                put("parts", partsArray)
            })
        }

        return JSONObject().apply {
            put("contents", contentsArray)
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.3)
                put("maxOutputTokens", 1024)
            })
        }
    }

    private fun parseGemmaResponse(jsonResponse: String): CropAnalysisResult? {
        return try {
            val rootObj = JSONObject(jsonResponse)
            val candidates = rootObj.optJSONArray("candidates")
            if (candidates != null && candidates.length() > 0) {
                val content = candidates.getJSONObject(0).optJSONObject("content")
                val parts = content?.optJSONArray("parts")
                if (parts != null && parts.length() > 0) {
                    val fullText = parts.getJSONObject(0).optString("text", "")
                    if (fullText.isNotBlank()) {
                        return extractStructuredSections(fullText)
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun extractStructuredSections(text: String): CropAnalysisResult {
        var issue = "Fasal me samasya pehchani gayi."
        val causes = mutableListOf<String>()
        val remedies = mutableListOf<String>()
        var nextSteps = "Kripya krishi visheshagya se salah lein."

        val samasyaMatch = Regex("\\[SAMASYA]([\\s\\S]*?)(?=\\[KARAN]|\$)").find(text)
        if (samasyaMatch != null) {
            val content = samasyaMatch.groupValues[1].trim()
            if (content.isNotEmpty()) issue = content
        }

        val karanMatch = Regex("\\[KARAN]([\\s\\S]*?)(?=\\[UPAY]|\$)").find(text)
        if (karanMatch != null) {
            val lines = karanMatch.groupValues[1].trim().lines()
            for (line in lines) {
                val cleaned = line.replace(Regex("^[-*•\\d.]+\\s*"), "").trim()
                if (cleaned.isNotEmpty()) causes.add(cleaned)
            }
        }

        val upayMatch = Regex("\\[UPAY]([\\s\\S]*?)(?=\\[AGLA_KADAM]|\$)").find(text)
        if (upayMatch != null) {
            val lines = upayMatch.groupValues[1].trim().lines()
            for (line in lines) {
                val cleaned = line.replace(Regex("^[-*•\\d.]+\\s*"), "").trim()
                if (cleaned.isNotEmpty()) remedies.add(cleaned)
            }
        }

        val aglaKadamMatch = Regex("\\[AGLA_KADAM]([\\s\\S]*?)\$").find(text)
        if (aglaKadamMatch != null) {
            val content = aglaKadamMatch.groupValues[1].trim()
            if (content.isNotEmpty()) nextSteps = content
        }

        if (causes.isEmpty() && remedies.isEmpty()) {
            issue = text.take(300)
            causes.add("Poshak tatva ki kami ya patton me rog")
            remedies.add("Sahi pani aur keetnashak ka istemal karein")
        }

        return CropAnalysisResult(
            detectedIssue = issue,
            possibleCauses = causes,
            remedies = remedies,
            nextSteps = nextSteps,
            rawResponse = text
        )
    }

    private fun parseErrorMessage(code: Int, body: String): String {
        return try {
            val root = JSONObject(body)
            val errorObj = root.optJSONObject("error")
            val message = errorObj?.optString("message") ?: body
            "Model $modelId Error ($code): $message"
        } catch (e: Exception) {
            "Model $modelId Error ($code)"
        }
    }

    private fun uriToBase64(uri: Uri): String? {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream) ?: return null

            val maxDimension = 512
            val width = originalBitmap.width
            val height = originalBitmap.height
            val scale = (maxDimension.toFloat() / Math.max(width, height)).coerceAtMost(1.0f)

            val scaledBitmap = Bitmap.createScaledBitmap(
                originalBitmap,
                (width * scale).toInt(),
                (height * scale).toInt(),
                true
            )

            val outputStream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
            val byteArray = outputStream.toByteArray()
            Base64.encodeToString(byteArray, Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        }
    }
}
