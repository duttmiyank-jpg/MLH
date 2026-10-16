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

        // First attempt: request JSON response with image if present
        var requestBodyJson = buildRequestBody(promptText, base64Image)
        var result = executeApiCall(parsedUrl, requestBodyJson, mediaType)

        // Retry with text-only payload if image payload was rejected with HTTP 400
        if (!result.isSuccess && base64Image != null) {
            val errMsg = result.exceptionOrNull()?.message ?: ""
            if (errMsg.contains("400") || errMsg.contains("inlineData")) {
                Log.d(TAG, "Retrying $targetModelId with text-only JSON payload...")
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
        val safePath = parsedUrl.encodedPath

        Log.d(TAG, "Request Start -> Model: $targetModelId, Path: $safePath")

        return try {
            val requestBody = requestBodyJson.toString().toRequestBody(mediaType)
            val request = Request.Builder()
                .url(parsedUrl)
                .post(requestBody)
                .build()

            val response = okHttpClient.newCall(request).execute()
            val duration = System.currentTimeMillis() - startTime
            val responseBodyString = response.body?.string() ?: ""

            Log.d(TAG, "HTTP Response -> Model: $targetModelId, Code: ${response.code}, Duration: ${duration}ms, BodyLength: ${responseBodyString.length}")

            if (response.isSuccessful && responseBodyString.isNotBlank()) {
                val parsedResult = parseGemmaResponse(responseBodyString)
                if (parsedResult != null) {
                    Result.success(parsedResult)
                } else {
                    Result.failure(
                        Exception("AI response format me truti aayi. Kripya 'Dobara Koshish Karein' button par click karein.")
                    )
                }
            } else {
                val errorMsg = parseErrorMessage(response.code, responseBodyString)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: UnknownHostException) {
            val duration = System.currentTimeMillis() - startTime
            Log.e(TAG, "UnknownHostException after ${duration}ms", e)
            Result.failure(
                Exception("Network Error: Mobile ka Internet/Wi-Fi off hai ya DNS lookup fail hua.")
            )
        } catch (e: SocketTimeoutException) {
            val duration = System.currentTimeMillis() - startTime
            Log.e(TAG, "SocketTimeoutException after ${duration}ms", e)
            Result.failure(
                Exception("Gemma AI Server read timeout ($duration ms). Kripya dobara try karein.")
            )
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            Log.e(TAG, "Exception (${e.javaClass.simpleName}) after ${duration}ms", e)
            Result.failure(
                Exception("Network Error: ${e.localizedMessage ?: e.message}")
            )
        }
    }

    private fun buildPrompt(cropType: CropType, symptoms: String, hasImage: Boolean): String {
        return """
            Aap KrishiVision AI hain, bhartiya kisano ke kheti sahayak.
            Aapko Hamesha natural Hinglish (Hindi text in Roman alphabet) me jawab dena hai.

            KISAN KA INPUT:
            - Fasal: ${cropType.displayNameHinglish}
            - Lakshan/Symptoms: $symptoms
            - Photo Attached: ${if (hasImage) "Haan (image attached)" else "Nahi"}

            OUTPUT FORMAT INSTRUCTIONS:
            1. KEWAL SHUDDH VALID JSON OBJECT RETURN KAREIN.
            2. KOI BHI DRAFT, REASONING, CHAIN-OF-THOUGHT, SELF-CORRECTION, MARKDOWN CODE FENCE (```json ... ```), YA TAGS ([SAMASYA], [KARAN]) MAT LIKHEIN.
            3. Shubh vishleshan aur spasht kheti salah dein.
            4. AGRICULTURAL SAFETY RULES:
               - Bina spasht pramaan ke kisi specific keede ya chemical pesticide ki dose confident hokar na batayein.
               - Agar pramaan kam ho toh spasht uncertainty zaahir karein aur sthaniya krishi kendra (KVK) ya visheshagya se jaanch karayein.

            EXACT JSON SCHEMA TO RETURN:
            {
              "problem": "(1-2 line me pehchani gayi samasya ya uncertainty ka zikr Hinglish me)",
              "possibleCauses": [
                "(Point 1: Sambhavit karan ya lakshan Hinglish me)",
                "(Point 2: Doosra karan)"
              ],
              "safeActions": [
                "(Point 1: Surakshit kheti upay ya jaanch Hinglish me)",
                "(Point 2: Doosra surakshit upay)"
              ],
              "nextStep": "(Kisan ke liye agla surakshit kadam Hinglish me)"
            }
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
                put("temperature", 0.2)
                put("maxOutputTokens", 1024)
                put("response_mime_type", "application/json")
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
                        return parseAndValidateStructuredJson(fullText, jsonResponse)
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Gemma root JSON parse error", e)
            null
        }
    }

    private fun parseAndValidateStructuredJson(text: String, rawJson: String): CropAnalysisResult? {
        return try {
            // Strip markdown code fences if present (e.g. ```json ... ```)
            val cleanedText = text.trim()
                .replace(Regex("^```json\\s*"), "")
                .replace(Regex("^```\\s*"), "")
                .replace(Regex("\\s*```$"), "")
                .trim()

            val json = JSONObject(cleanedText)
            val problem = json.optString("problem", "").trim()
            val nextStep = json.optString("nextStep", "").trim()

            val causesArray = json.optJSONArray("possibleCauses")
            val causesList = mutableListOf<String>()
            if (causesArray != null) {
                for (i in 0 until causesArray.length()) {
                    val cause = causesArray.getString(i).trim()
                    if (cause.isNotEmpty()) causesList.add(cause)
                }
            }

            val actionsArray = json.optJSONArray("safeActions")
            val actionsList = mutableListOf<String>()
            if (actionsArray != null) {
                for (i in 0 until actionsArray.length()) {
                    val action = actionsArray.getString(i).trim()
                    if (action.isNotEmpty()) actionsList.add(action)
                }
            }

            // Validate that fields contain useful text
            if (problem.length < 3 || causesList.isEmpty() || actionsList.isEmpty() || nextStep.length < 3) {
                Log.w(TAG, "Validation failed: Fields empty or insufficient length")
                return null
            }

            // Filter out drafting notes or internal tags if model leaks them
            val containsDraftingNote = { s: String ->
                s.contains("Draft", ignoreCase = true) ||
                        s.contains("Refinement", ignoreCase = true) ||
                        s.contains("Self-Correction", ignoreCase = true) ||
                        s.contains("Chain-of-thought", ignoreCase = true)
            }

            if (containsDraftingNote(problem) || containsDraftingNote(nextStep)) {
                Log.w(TAG, "Validation failed: Internal drafting text leaked into output")
                return null
            }

            CropAnalysisResult(
                detectedIssue = problem,
                possibleCauses = causesList,
                remedies = actionsList,
                nextSteps = nextStep,
                rawResponse = rawJson
            )
        } catch (e: Exception) {
            Log.e(TAG, "JSON parse/validation error: ${e.message}", e)
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
