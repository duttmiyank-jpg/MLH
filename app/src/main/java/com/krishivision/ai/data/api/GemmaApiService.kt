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
import kotlinx.coroutines.delay
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

        var requestBodyJson = buildRequestBody(promptText, base64Image)
        var result = executeApiCallWithRetry(parsedUrl, requestBodyJson, mediaType)

        // Retry with text-only payload if image payload caused HTTP 500, 400, or server error
        if (!result.isSuccess && base64Image != null) {
            val errMsg = result.exceptionOrNull()?.message ?: ""
            if (errMsg.contains("500") || errMsg.contains("400") || errMsg.contains("503") || errMsg.contains("inlineData")) {
                Log.d(TAG, "Image payload failed ($errMsg). Retrying $targetModelId with text-only payload...")
                requestBodyJson = buildRequestBody(promptText, null)
                result = executeApiCallWithRetry(parsedUrl, requestBodyJson, mediaType)
            }
        }

        result
    }

    private suspend fun executeApiCallWithRetry(
        parsedUrl: okhttp3.HttpUrl,
        requestBodyJson: JSONObject,
        mediaType: okhttp3.MediaType
    ): Result<CropAnalysisResult> {
        val maxAttempts = 3
        var lastError = "Server Error"

        for (attempt in 1..maxAttempts) {
            val startTime = System.currentTimeMillis()
            val safePath = parsedUrl.encodedPath

            Log.d(TAG, "Request Start (Attempt $attempt/$maxAttempts) -> Model: $targetModelId, Path: $safePath")

            try {
                val requestBody = requestBodyJson.toString().toRequestBody(mediaType)
                val request = Request.Builder()
                    .url(parsedUrl)
                    .post(requestBody)
                    .build()

                val response = okHttpClient.newCall(request).execute()
                val duration = System.currentTimeMillis() - startTime
                val responseBodyString = response.body?.string() ?: ""

                Log.d(TAG, "HTTP Response -> Status: ${response.code}, Duration: ${duration}ms, BodyLength: ${responseBodyString.length}")

                if (response.isSuccessful && responseBodyString.isNotBlank()) {
                    val parsedResult = parseGemmaResponse(responseBodyString)
                    if (parsedResult != null) {
                        return Result.success(parsedResult)
                    } else {
                        lastError = "Gemma AI response format unparseable."
                    }
                } else {
                    lastError = parseErrorMessage(response.code, responseBodyString)

                    // Bounded retry with exponential backoff for transient server errors (500, 503, 429)
                    if ((response.code == 500 || response.code == 503 || response.code == 429) && attempt < maxAttempts) {
                        val retryAfterHeader = response.header("Retry-After")
                        val backoffMs = if (retryAfterHeader != null) {
                            (retryAfterHeader.toLongOrNull() ?: 1L).coerceAtMost(5L) * 1000L
                        } else {
                            1000L * attempt // 1s, 2s backoff
                        }

                        Log.w(TAG, "Transient HTTP ${response.code} error on attempt $attempt. Backing off for ${backoffMs}ms before retry...")
                        delay(backoffMs)
                        continue
                    } else {
                        return Result.failure(Exception(lastError))
                    }
                }
            } catch (e: UnknownHostException) {
                val duration = System.currentTimeMillis() - startTime
                Log.e(TAG, "UnknownHostException on attempt $attempt after ${duration}ms", e)
                return Result.failure(
                    Exception("Network Error: Mobile ka Internet/Wi-Fi off hai ya DNS lookup fail hua.")
                )
            } catch (e: SocketTimeoutException) {
                val duration = System.currentTimeMillis() - startTime
                Log.e(TAG, "SocketTimeoutException on attempt $attempt after ${duration}ms", e)
                if (attempt < maxAttempts) {
                    delay(1000L * attempt)
                    continue
                }
                return Result.failure(
                    Exception("Gemma AI Server read timeout ($duration ms). Kripya dobara try karein.")
                )
            } catch (e: Exception) {
                val duration = System.currentTimeMillis() - startTime
                Log.e(TAG, "Exception (${e.javaClass.simpleName}) on attempt $attempt after ${duration}ms", e)
                return Result.failure(
                    Exception("Network Error: ${e.localizedMessage ?: e.message}")
                )
            }
        }

        return Result.failure(Exception(lastError))
    }

    private fun buildPrompt(cropType: CropType, symptoms: String, hasImage: Boolean): String {
        return """
            Aap KrishiVision AI hain, bhartiya kisano ke kheti sahayak.
            Aapko Hamesha natural Hinglish (Hindi text in Roman alphabet) me jawab dena hai.

            KISAN KA INPUT:
            - Fasal: ${cropType.displayNameHinglish}
            - Lakshan/Symptoms: $symptoms
            - Photo Attached: ${if (hasImage) "Haan (image attached)" else "Nahi"}

            Kripya niche diye gaye format me HI Hinglish me jawab dein:

            [SAMASYA]
            (Pehchani gayi sambhavit samasya ya rog ka 1-2 line me Hinglish me vivran)

            [KARAN]
            - (Point 1: Sambhavit karan ya lakshan Hinglish me)
            - (Point 2: Doosra karan)

            [UPAY]
            - (Point 1: Surakshit kheti upay ya jaanch Hinglish me)
            - (Point 2: Doosra surakshit upay)

            [AGLA_KADAM]
            (Kisan ke liye agla surakshit kadam Hinglish me)
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
                        return parseAndCleanTextResponse(fullText, jsonResponse)
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Gemma root JSON parse error", e)
            null
        }
    }

    private fun parseAndCleanTextResponse(text: String, rawJson: String): CropAnalysisResult {
        val cleanedText = text.trim()
            .replace(Regex("^```[a-zA-Z]*\\s*"), "")
            .replace(Regex("\\s*```$"), "")
            .trim()

        // 1. Try JSON parsing if response happens to be JSON
        if (cleanedText.startsWith("{") && cleanedText.endsWith("}")) {
            try {
                val json = JSONObject(cleanedText)
                val problem = json.optString("problem", "").trim()
                val nextStep = json.optString("nextStep", "").trim()

                val causesList = mutableListOf<String>()
                json.optJSONArray("possibleCauses")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val s = arr.getString(i).trim()
                        if (s.isNotEmpty()) causesList.add(s)
                    }
                }

                val actionsList = mutableListOf<String>()
                json.optJSONArray("safeActions")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val s = arr.getString(i).trim()
                        if (s.isNotEmpty()) actionsList.add(s)
                    }
                }

                if (problem.isNotBlank()) {
                    return CropAnalysisResult(
                        detectedIssue = problem,
                        possibleCauses = if (causesList.isNotEmpty()) causesList else listOf("Fasal ki dekhbhal aur mitti ki jaanch zaroori hai."),
                        remedies = if (actionsList.isNotEmpty()) actionsList else listOf("Sahi pani aur surakshit keetnashak ka istemal karein."),
                        nextSteps = if (nextStep.isNotBlank()) nextStep else "Sthaniya krishi kendra se sampark karein.",
                        rawResponse = rawJson
                    )
                }
            } catch (e: Exception) {
                // Fallthrough to text parsing
            }
        }

        // 2. Try Tag Extraction ([SAMASYA], [KARAN], [UPAY], [AGLA_KADAM])
        var issue = ""
        val causes = mutableListOf<String>()
        val remedies = mutableListOf<String>()
        var nextSteps = ""

        val samasyaMatch = Regex("\\[SAMASYA]([\\s\\S]*?)(?=\\[KARAN]|\$)").find(cleanedText)
        if (samasyaMatch != null) {
            issue = samasyaMatch.groupValues[1].trim()
        }

        val karanMatch = Regex("\\[KARAN]([\\s\\S]*?)(?=\\[UPAY]|\$)").find(cleanedText)
        if (karanMatch != null) {
            val lines = karanMatch.groupValues[1].trim().lines()
            for (line in lines) {
                val cleaned = line.replace(Regex("^[-*•\\d.]+\\s*"), "").trim()
                if (cleaned.isNotEmpty()) causes.add(cleaned)
            }
        }

        val upayMatch = Regex("\\[UPAY]([\\s\\S]*?)(?=\\[AGLA_KADAM]|\$)").find(cleanedText)
        if (upayMatch != null) {
            val lines = upayMatch.groupValues[1].trim().lines()
            for (line in lines) {
                val cleaned = line.replace(Regex("^[-*•\\d.]+\\s*"), "").trim()
                if (cleaned.isNotEmpty()) remedies.add(cleaned)
            }
        }

        val aglaKadamMatch = Regex("\\[AGLA_KADAM]([\\s\\S]*?)\$").find(cleanedText)
        if (aglaKadamMatch != null) {
            nextSteps = aglaKadamMatch.groupValues[1].trim()
        }

        if (issue.isNotBlank()) {
            return CropAnalysisResult(
                detectedIssue = issue,
                possibleCauses = if (causes.isNotEmpty()) causes else listOf("Fasal ki dekhbhal aur poodhe ki jaanch karein."),
                remedies = if (remedies.isNotEmpty()) remedies else listOf("Sahi samay par pani aur zaroori urvarak dein."),
                nextSteps = if (nextSteps.isNotBlank()) nextSteps else "Sthaniya krishi kendra se sampark karein.",
                rawResponse = rawJson
            )
        }

        // 3. Robust Fallback: Display plain generated text directly
        return CropAnalysisResult(
            detectedIssue = cleanedText,
            possibleCauses = listOf("Kisan ke dwara diye gaye vivran ke aadhar par vishleshan poora hua."),
            remedies = listOf("Surakshit kheti upay apnayein aur poodhon ki jaanch karein."),
            nextSteps = "Sthaniya krishi visheshagya (KVK) se salah lein.",
            rawResponse = rawJson
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
