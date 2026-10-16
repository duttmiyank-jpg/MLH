package com.krishivision.ai.data.api

import android.content.Context
import android.net.Uri
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
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

class GemmaApiService(private val context: Context) {

    private val TAG = "GemmaApiService"
    private val targetModelId = "gemma-4-26b-a4b-it"
    private val expectedHost = "generativelanguage.googleapis.com"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
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

        // Construct minimal diagnostic request payload as specified
        val requestBodyJson = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", "Reply with OK in Hinglish.")
                        })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.2)
                put("maxOutputTokens", 128)
            })
        }

        val startTime = System.currentTimeMillis()
        val safePath = parsedUrl.encodedPath // Path without API key parameter

        Log.d(TAG, "Request Start -> Model: $targetModelId, Host: ${parsedUrl.host}, Path: $safePath")

        try {
            val requestBody = requestBodyJson.toString().toRequestBody(mediaType)
            val request = Request.Builder()
                .url(parsedUrl)
                .post(requestBody)
                .build()

            val response = okHttpClient.newCall(request).execute()
            val duration = System.currentTimeMillis() - startTime
            val responseBodyString = response.body?.string() ?: ""

            // Safe logging of HTTP status code and response body (NEVER logging the API key)
            Log.d(TAG, "HTTP Response -> Model: $targetModelId, Status: ${response.code}, Duration: ${duration}ms, Body: $responseBodyString")

            if (response.isSuccessful && responseBodyString.isNotBlank()) {
                val parsedResult = parseGemmaResponse(responseBodyString)
                if (parsedResult != null) {
                    Result.success(parsedResult)
                } else {
                    Result.failure(
                        Exception("Model $targetModelId responded with HTTP 200 OK, but response payload was unparseable: $responseBodyString")
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
                Exception("Network Error: Mobile ka Internet/Wi-Fi off hai ya DNS lookup fail hua ($targetModelId).")
            )
        } catch (e: SocketTimeoutException) {
            val duration = System.currentTimeMillis() - startTime
            Log.e(TAG, "SocketTimeoutException (Client Read Timeout) after ${duration}ms for $targetModelId", e)
            Result.failure(
                Exception("Client-side Read Timeout ($duration ms): Model $targetModelId ne $duration ms me response nahi diya.")
            )
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            Log.e(TAG, "Exception (${e.javaClass.simpleName}) after ${duration}ms for $targetModelId", e)
            Result.failure(
                Exception("Client Exception (${e.javaClass.simpleName}): ${e.localizedMessage ?: e.message}")
            )
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
                        return CropAnalysisResult(
                            detectedIssue = "Gemma AI Response Received: $fullText",
                            possibleCauses = listOf("Model: $targetModelId test successful."),
                            remedies = listOf("Status: OK in Hinglish."),
                            nextSteps = "Minimal diagnostic test passed.",
                            rawResponse = jsonResponse
                        )
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
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
}
