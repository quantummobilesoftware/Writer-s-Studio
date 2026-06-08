package com.example.util

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GeminiService {
    private const val TAG = "GeminiService"
    private const val MODEL_NAME = "gemini-3.5-flash"

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    suspend fun generateContent(prompt: String, systemInstruction: String? = null): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext Result.failure(Exception("Gemini API key is not configured. Please add GEMINI_API_KEY in the Secrets panel in AI Studio."))
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL_NAME:generateContent?key=$apiKey"

        try {
            val rootJson = JSONObject()
            
            // Build contents
            val contentsArray = JSONArray()
            val contentObj = JSONObject()
            val partsArray = JSONArray()
            val partObj = JSONObject()
            partObj.put("text", prompt)
            partsArray.put(partObj)
            contentObj.put("parts", partsArray)
            contentsArray.put(contentObj)
            rootJson.put("contents", contentsArray)

            // Build systemInstruction if present
            if (systemInstruction != null) {
                val sysContentObj = JSONObject()
                val sysPartsArray = JSONArray()
                val sysPartObj = JSONObject()
                sysPartObj.put("text", systemInstruction)
                sysPartsArray.put(sysPartObj)
                sysContentObj.put("parts", sysPartsArray)
                rootJson.put("systemInstruction", sysContentObj)
            }

            val requestBodyJson = rootJson.toString()
            Log.d(TAG, "Sending request to Gemini: $requestBodyJson")

            val request = Request.Builder()
                .url(url)
                .post(requestBodyJson.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val errBody = response.body?.string() ?: ""
                Log.e(TAG, "Request failed code=${response.code} body=$errBody")
                
                // Extract human-friendly error from json if possible
                val errMsg = try {
                    val errJson = JSONObject(errBody)
                    val errorObj = errJson.getJSONObject("error")
                    errorObj.getString("message")
                } catch (e: Exception) {
                    "HTTP ${response.code}: ${response.message}"
                }
                return@withContext Result.failure(Exception(errMsg))
            }

            val resBody = response.body?.string() ?: ""
            Log.d(TAG, "Received response from Gemini: $resBody")

            val resJson = JSONObject(resBody)
            val candidates = resJson.optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0) {
                return@withContext Result.failure(Exception("Gemini returned empty candidates. Content might be blocked by safety settings or invalid."))
            }

            val candidate = candidates.getJSONObject(0)
            val content = candidate.optJSONObject("content") ?: return@withContext Result.failure(Exception("Response JSON has no 'content' field."))
            val parts = content.optJSONArray("parts") ?: return@withContext Result.failure(Exception("Response JSON content has no 'parts' field."))
            if (parts.length() == 0) {
                return@withContext Result.failure(Exception("Response JSON parts array is empty."))
            }

            val text = parts.getJSONObject(0).optString("text")
            if (text.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("Gemini returned empty text response."))
            }

            Result.success(text)
        } catch (e: Exception) {
            Log.e(TAG, "Exception during generation", e)
            Result.failure(e)
        }
    }
}
