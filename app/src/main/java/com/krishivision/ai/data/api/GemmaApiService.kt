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
    private val expectedHost = "generativelanguage.googleapis.com"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
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

        // Step 1: Discover verified models available to this API key
        val availableModels = fetchAvailableModels(apiKey)
        if (availableModels.isEmpty()) {
            return@withContext Result.failure(
                Exception("Google AI API se supported models list nahi mila. Kripya API Key aur Internet connection check karein.")
            )
        }

        Log.d(TAG, "Discovered ${availableModels.size} generateContent models for this API key: $availableModels")

        // Filter for Gemma model or pick best available Gemma model from official Google list
        val selectedModel = availableModels.firstOrNull { it.contains("gemma", ignoreCase = true) }
            ?: return@withContext Result.failure(
                Exception("Aapki API key par koi Gemma model available nahi hai. Available models: $availableModels")
            )

        Log.d(TAG, "Selected verified Gemma model: $selectedModel")

        val base64Image = if (imageUri != null) uriToBase64(imageUri) else null
        val promptText = buildPrompt(cropType, symptoms, base64Image != null)
        val mediaType = "application/json; charset=utf-8".toMediaType()

        // Construct exact generateContent endpoint using verified model name
        val endpointUrl = "https://$expectedHost/v1beta/$selectedModel:generateContent?key=$apiKey"
        val parsedUrl = endpointUrl.toHttpUrlOrNull()
            ?: return@withContext Result.failure(Exception("Malformed API URL for $selectedModel"))

        // First attempt with image (if present)
        var requestBodyJson = buildRequestBody(promptText, base64Image)
        var result = executeApiCall(parsedUrl, requestBodyJson, mediaType, selectedModel)

        // If image caused 400 Bad Request (text-only model), retry with text prompt
        if (!result.isSuccess && base64Image != null) {
            val errMessage = result.exceptionOrNull()?.message ?: ""
            if (errMessage.contains("400") || errMessage.contains("inlineData")) {
                Log.d(TAG, "Retrying $selectedModel with text-only payload...")
                requestBodyJson = buildRequestBody(promptText, null)
                result = executeApiCall(parsedUrl, requestBodyJson, mediaType, selectedModel)
            }
        }

        result
    }

    private fun fetchAvailableModels(apiKey: String): List<String> {
        val listUrl = "https://$expectedHost/v1beta/models?key=$apiKey"
        val parsedUrl = listUrl.toHttpUrlOrNull() ?: return emptyList()

        return try {
            val request = Request.Builder().url(parsedUrl).get().build()
            val startTime = System.currentTimeMillis()
            val response = okHttpClient.newCall(request).execute()
            val duration = System.currentTimeMillis() - startTime
            val bodyString = response.body?.string() ?: ""

            Log.d(TAG, "Model List Response Code: ${response.code}, Duration: ${duration}ms")

            if (response.isSuccessful && bodyString.isNotBlank()) {
                val root = JSONObject(bodyString)
                val modelsArray = root.optJSONArray("models") ?: JSONArray()
                val resultList = mutableListOf<String>()

                for (i in 0 until modelsArray.length()) {
                    val modelObj = modelsArray.getJSONObject(i)
                    val modelName = modelObj.optString("name", "") // e.g. "models/gemma-2-9b-it"
                    val methods = modelObj.optJSONArray("supportedGenerationMethods") ?: JSONArray()

                    var supportsGenerateContent = false
                    for (j in 0 until methods.length()) {
                        if (methods.getString(j) == "generateContent") {
                            supportsGenerateContent = true
                            break
                        }
                    }

                    if (supportsGenerateContent && modelName.isNotBlank()) {
                        resultList.add(modelName)
                    }
                }
                resultList
            } else {
                Log.e(TAG, "Failed to fetch models list ($response.code): $bodyString")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception fetching model list: ${e.message}", e)
            emptyList()
        }
    }

    private fun executeApiCall(
        parsedUrl: okhttp3.HttpUrl,
        requestBodyJson: JSONObject,
        mediaType: okhttp3.MediaType,
        modelName: String
    ): Result<CropAnalysisResult> {
        val startTime = System.currentTimeMillis()
        val safePath = parsedUrl.encodedPath

        Log.d(TAG, "GenerateContent Request Start -> Model: $modelName, Path: $safePath")

        return try {
            val requestBody = requestBodyJson.toString().toRequestBody(mediaType)
            val request = Request.Builder()
                .url(parsedUrl)
                .post(requestBody)
                .build()

            val response = okHttpClient.newCall(request).execute()
            val duration = System.currentTimeMillis() - startTime
            val responseBodyString = response.body?.string() ?: ""

            Log.d(TAG, "GenerateContent Response Code: ${response.code}, Duration: ${duration}ms, BodyLength: ${responseBodyString.length}")

            if (response.isSuccessful && responseBodyString.isNotBlank()) {
                val parsedResult = parseGemmaResponse(responseBodyString)
                if (parsedResult != null) {
                    Result.success(parsedResult)
                } else {
                    Result.failure(Exception("Gemma AI ($modelName) response format invalid."))
                }
            } else {
                val errorMsg = parseErrorMessage(response.code, responseBodyString, modelName)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: UnknownHostException) {
            Log.e(TAG, "UnknownHostException for $modelName", e)
            Result.failure(
                Exception("Mobile ka Internet/Wi-Fi off hai ya DNS lookup fail hua! Kripya Internet chalu karein.")
            )
        } catch (e: SocketTimeoutException) {
            Log.e(TAG, "SocketTimeoutException for $modelName", e)
            Result.failure(
                Exception("Model $modelName server timeout. Kripya dobara try karein.")
            )
        } catch (e: Exception) {
            Log.e(TAG, "Exception (${e.javaClass.simpleName}) for $modelName", e)
            Result.failure(
                Exception("Network Error ($modelName): ${e.localizedMessage ?: e.message}")
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

    private fun parseErrorMessage(code: Int, body: String, modelName: String): String {
        return try {
            val root = JSONObject(body)
            val errorObj = root.optJSONObject("error")
            val message = errorObj?.optString("message") ?: body
            "Model $modelName Error ($code): $message"
        } catch (e: Exception) {
            "Model $modelName Error ($code)"
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
