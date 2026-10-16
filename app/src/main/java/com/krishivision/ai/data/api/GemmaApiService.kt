package com.krishivision.ai.data.api

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.krishivision.ai.BuildConfig
import com.krishivision.ai.data.model.CropAnalysisResult
import com.krishivision.ai.data.model.CropType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

class GemmaApiService(private val context: Context) {

    private val TAG = "GemmaApiService"
    private val targetModelId = "gemma-4-26b-a4b-it"
    private val expectedHost = "generativelanguage.googleapis.com"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun analyzeCrop(
        cropType: CropType,
        symptoms: String,
        imageUri: Uri?
    ): Result<CropAnalysisResult> = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMMA_API_KEY.trim()
        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                Exception("Gemma API Key local.properties me nahi mila! Kripya GEMMA_API_KEY set karein.")
            )
        }

        val endpointUrl = "https://$expectedHost/v1beta/models/$targetModelId:generateContent?key=$apiKey"
        val parsedUrl = endpointUrl.toHttpUrlOrNull()
            ?: return@withContext Result.failure(Exception("Malformed API URL for $targetModelId"))

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val base64Image = if (imageUri != null) uriToBase64(imageUri) else null
        val promptText = buildPrompt(cropType, symptoms, base64Image != null)

        // First attempt with image inlineData if present
        var requestBodyJson = buildRequestBody(promptText, base64Image)
        var result = executeApiCall(parsedUrl, requestBodyJson, mediaType)

        // Retry with text-only payload if image inlineData was rejected with HTTP 400
        if (!result.isSuccess && base64Image != null) {
            val errMsg = result.exceptionOrNull()?.message ?: ""
            if (errMsg.contains("400") || errMsg.contains("inlineData")) {
                Log.d(TAG, "Retrying $targetModelId with text-only payload...")
                requestBodyJson = buildRequestBody(promptText, null)
                result = executeApiCall(parsedUrl, requestBodyJson, mediaType)
            }
        }

        result
    }

    private fun executeApiCall(
        parsedUrl: okhttp3.HttpUrl,
        requestBodyJson: JSONObject,
        mediaType: okhttp3.MediaType
    ): Result<CropAnalysisResult> {
        val startTime = System.currentTimeMillis()
        val safePath = parsedUrl.encodedPath // Path without API key parameter

        Log.d(TAG, "Request Start -> Model: $targetModelId, Host: ${parsedUrl.host}, Path: $safePath")

        return try {
            val requestBody = requestBodyJson.toString().toRequestBody(mediaType)
            val request = Request.Builder()
                .url(parsedUrl)
                .post(requestBody)
                .build()

            val response = okHttpClient.newCall(request).execute()
            val duration = System.currentTimeMillis() - startTime
            val responseBodyString = response.body?.string() ?: ""

            // Safe logging of HTTP status code and response body length (NEVER logging the API key)
            Log.d(TAG, "HTTP Response -> Model: $targetModelId, Status: ${response.code}, Duration: ${duration}ms, BodyLength: ${responseBodyString.length}")

            if (response.isSuccessful && responseBodyString.isNotBlank()) {
                val parsedResult = parseGemmaResponse(responseBodyString)
                if (parsedResult != null) {
                    Result.success(parsedResult)
                } else {
                    Result.failure(
                        Exception("Model $targetModelId HTTP 200 OK diya par response parsing nahi ho saki.")
                    )
                }
            } else {
                val errorMsg = parseErrorMessage(response.code, responseBodyString)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: UnknownHostException) {
            val duration = System.currentTimeMillis() - startTime
            Log.e(TAG, "UnknownHostException after ${duration}ms for $targetModelId", e)
            Result.failure(
                Exception("Network Error: Mobile ka Internet/Wi-Fi off hai ya DNS lookup fail hua.")
            )
        } catch (e: SocketTimeoutException) {
            val duration = System.currentTimeMillis() - startTime
            Log.e(TAG, "SocketTimeoutException after ${duration}ms for $targetModelId", e)
            Result.failure(
                Exception("Gemma AI Server read timeout ($duration ms). Kripya dobara try karein.")
            )
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            Log.e(TAG, "Exception (${e.javaClass.simpleName}) after ${duration}ms for $targetModelId", e)
            Result.failure(
                Exception("Network Error: ${e.localizedMessage ?: e.message}")
            )
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
            Bohot hi saral, spasht aur helpful bhasha me kisan ko salah dein.
            Agar jaankari kam lage toh spasht batayein aur surakshit kheti ke upay batayein.

            [SAMASYA]
            (Pehchani gayi sambhavit samasya ya rog ka 1-2 line me Hinglish me vivran)

            [KARAN]
            - (Point 1: Sambhavit karan ya keede/bimari ka prabhav Hinglish me)
            - (Point 2: Doosra karan agar ho)

            [UPAY]
            - (Point 1: Bachav aur kheti ke sateek upay Hinglish me)
            - (Point 2: Doosra surakshit upay ya dawai)

            [AGLA_KADAM]
            (Kisan ko abhi turant kya karna chahiye - Hinglish me short salah)
        """.trimIndent()
    }

    private fun buildRequestBody(promptText: String, base64Image: String?): JSONObject {
        val partsArray = JSONArray()

        partsArray.put(JSONObject().apply {
            put("text", promptText)
        })

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
            causes.add("Poshak tatva ki kami ya keede/patton me rog")
            remedies.add("Sahi pani aur surakshit keetnashak ka istemal karein")
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
            when (code) {
                429 -> "HTTP 429 Too Many Requests: Rate limit ya quota limit cross ho gayi ($targetModelId). Details: $message"
                500 -> "HTTP 500 Internal Server Error: Google AI Server internal failure ($targetModelId). Details: $message"
                503 -> "HTTP 503 Service Unavailable: Google AI Server overloaded hai ($targetModelId). Details: $message"
                404 -> "HTTP 404 Not Found: Model $targetModelId endpoint v1beta par available nahi hai ($targetModelId). Details: $message"
                400 -> "HTTP 400 Bad Request: Request payload invalid hai ($targetModelId). Details: $message"
                else -> "HTTP $code Server Error ($targetModelId): $message"
            }
        } catch (e: Exception) {
            "HTTP $code Error ($targetModelId): $body"
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
