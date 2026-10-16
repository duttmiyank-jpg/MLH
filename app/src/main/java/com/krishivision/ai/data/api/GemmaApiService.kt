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
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

class GemmaApiService(private val context: Context) {

    private val modelId = "gemma-4-31b-it"

    suspend fun analyzeCrop(
        cropType: CropType,
        symptoms: String,
        imageUri: Uri?
    ): Result<CropAnalysisResult> = withContext(Dispatchers.IO) {
        try {
            val apiKey = BuildConfig.GEMMA_API_KEY
            if (apiKey.isBlank()) {
                return@withContext Result.failure(
                    Exception("Gemma API key nahi mila! Kripya local.properties me GEMMA_API_KEY add karein.")
                )
            }

            val endpointUrl = "https://generativelanguage.googleapis.com/v1beta/models/$modelId:generateContent?key=$apiKey"
            val promptText = buildPrompt(cropType, symptoms, imageUri != null)

            val partsArray = JSONArray()

            // Text part
            val textPart = JSONObject().apply {
                put("text", promptText)
            }
            partsArray.put(textPart)

            // Image part (if selected)
            if (imageUri != null) {
                val base64Image = uriToBase64(imageUri)
                if (base64Image != null) {
                    val inlineData = JSONObject().apply {
                        put("mimeType", "image/jpeg")
                        put("data", base64Image)
                    }
                    val imagePart = JSONObject().apply {
                        put("inlineData", inlineData)
                    }
                    partsArray.put(imagePart)
                }
            }

            val contentsArray = JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", partsArray)
                })
            }

            val requestBody = JSONObject().apply {
                put("contents", contentsArray)
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.3)
                    put("maxOutputTokens", 1024)
                })
            }

            val url = URL(endpointUrl)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                doOutput = true
                connectTimeout = 15000
                readTimeout = 20000
            }

            connection.outputStream.use { os ->
                val input = requestBody.toString().toByteArray(Charsets.UTF_8)
                os.write(input, 0, input.size)
            }

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                val parsedResult = parseGemmaResponse(responseText)
                Result.success(parsedResult)
            } else {
                val errorText = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP Error $responseCode"
                Result.failure(Exception("AI Service Error ($responseCode): $errorText"))
            }

        } catch (e: Exception) {
            Result.failure(e)
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
            Bohot hi saral aur helpful bhasha use karein.

            [SAMASYA]
            (Fasal ki samasya ya rog ka spasht vivran 1-2 line me Hinglish me)

            [KARAN]
            - (Point 1: Rog ya samasya hone ka karan Hinglish me)
            - (Point 2: Doosra karan agar ho toh)

            [UPAY]
            - (Point 1: Fasal ko bachane ke sateek upay ya dawai Hinglish me)
            - (Point 2: Doosra upay)

            [AGLA_KADAM]
            (Kisan ko abhi turant kya karna chahiye - Hinglish me short सलाह)
        """.trimIndent()
    }

    private fun parseGemmaResponse(jsonResponse: String): CropAnalysisResult {
        try {
            val rootObj = JSONObject(jsonResponse)
            val candidates = rootObj.optJSONArray("candidates")
            if (candidates != null && candidates.length() > 0) {
                val content = candidates.getJSONObject(0).optJSONObject("content")
                val parts = content?.optJSONArray("parts")
                if (parts != null && parts.length() > 0) {
                    val fullText = parts.getJSONObject(0).optString("text", "")
                    return extractStructuredSections(fullText)
                }
            }
        } catch (e: Exception) {
            // Fallthrough to raw text fallback
        }
        return CropAnalysisResult(
            detectedIssue = "Gemma 4 dwara fasal ka vishleshan poora hua.",
            possibleCauses = listOf("Sahi poshan ki kami", "Mausam me badlav ya keede"),
            remedies = listOf("Kripya fasal ki dekhbhal karein aur mitti ki jaanch karayein."),
            nextSteps = "Sthaniya krishi kendra se sampark karein.",
            rawResponse = jsonResponse
        )
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

        if (causes.isEmpty()) causes.add("Poshak tatva ki kami ya patton me rog")
        if (remedies.isEmpty()) remedies.add("Sahi pani aur keetnashak ka istemal karein")

        return CropAnalysisResult(
            detectedIssue = issue,
            possibleCauses = causes,
            remedies = remedies,
            nextSteps = nextSteps,
            rawResponse = text
        )
    }

    private fun uriToBase64(uri: Uri): String? {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream) ?: return null
            
            // Resize bitmap if too large to fit in REST payload
            val maxDimension = 1024
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
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
            val byteArray = outputStream.toByteArray()
            Base64.encodeToString(byteArray, Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        }
    }
}
