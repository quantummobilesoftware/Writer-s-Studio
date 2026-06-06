package com.example.data.remote

import android.content.Context
import android.util.Log
import com.google.android.gms.auth.GoogleAuthUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

object GoogleDriveService {
    private const val TAG = "GoogleDriveService"
    private val client = OkHttpClient()
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaTypeOrNull()

    // In-memory cache for the accessor token
    private var cachedToken: String? = null

    /**
     * Obtains the Google OAuth 2.0 access token synchronously using GoogleAuthUtil.
     * Clears and retries if requested (e.g. after a 401 response).
     */
    suspend fun getAccessToken(context: Context, email: String, forceRefresh: Boolean = false): String? =
        withContext(Dispatchers.IO) {
            if (email.isEmpty()) return@withContext null
            
            try {
                if (forceRefresh && cachedToken != null) {
                    Log.d(TAG, "Invalidating old token to fetch a fresh one")
                    GoogleAuthUtil.clearToken(context, cachedToken!!)
                    cachedToken = null
                }
                
                if (cachedToken != null) {
                    return@withContext cachedToken
                }

                val scope = "oauth2:https://www.googleapis.com/auth/drive.file " +
                        "https://www.googleapis.com/auth/userinfo.profile " +
                        "https://www.googleapis.com/auth/userinfo.email"
                
                val token = GoogleAuthUtil.getToken(context, email, scope)
                cachedToken = token
                Log.d(TAG, "Successfully fetched Google OAuth token")
                token
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get access token: ${e.message}", e)
                null
            }
        }

    /**
     * Executes an authenticated request. It automatically retries once if hits 401 unauthorized.
     */
    private suspend fun executeWithAuth(
        context: Context,
        email: String,
        requestBuilder: (String) -> Request
    ): okhttp3.Response? {
        var token = getAccessToken(context, email, forceRefresh = false) ?: return null
        var request = requestBuilder(token)
        var response = withContext(Dispatchers.IO) {
            try { client.newCall(request).execute() } catch (e: IOException) { null }
        }

        if (response != null && response.code == 401) {
            Log.w(TAG, "Hit 401 client error. Invalidate token and retry.")
            response.close()
            token = getAccessToken(context, email, forceRefresh = true) ?: return null
            request = requestBuilder(token)
            response = withContext(Dispatchers.IO) {
                try { client.newCall(request).execute() } catch (e: IOException) { null }
            }
        }
        return response
    }

    /**
     * Finds or creates the app backup folder "WriterApp_Backup" in Google Drive.
     * Returns the folder's fileId.
     */
    suspend fun getOrCreateAppFolder(context: Context, email: String): String? {
        val existingId = findAppFolder(context, email)
        if (existingId != null) {
            return existingId
        }
        return createAppFolder(context, email)
    }

    private suspend fun findAppFolder(context: Context, email: String): String? {
        val url = "https://www.googleapis.com/drive/v3/files?q=name='WriterApp_Backup' and mimeType='application/vnd.google-apps.folder' and trashed=false&fields=files(id)"
        val response = executeWithAuth(context, email) { token ->
            Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .get()
                .build()
        } ?: return null

        response.use {
            if (it.isSuccessful) {
                val json = JSONObject(it.body?.string() ?: "")
                val files = json.optJSONArray("files")
                if (files != null && files.length() > 0) {
                    return files.getJSONObject(0).getString("id")
                }
            } else {
                Log.e(TAG, "Find app folder request failed: ${it.code} - ${it.message}")
            }
        }
        return null
    }

    private suspend fun createAppFolder(context: Context, email: String): String? {
        val url = "https://www.googleapis.com/drive/v3/files"
        val bodyContent = JSONObject().apply {
            put("name", "WriterApp_Backup")
            put("mimeType", "application/vnd.google-apps.folder")
        }.toString()

        val response = executeWithAuth(context, email) { token ->
            Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .post(RequestBody.create(JSON_MEDIA_TYPE, bodyContent))
                .build()
        } ?: return null

        response.use {
            if (it.isSuccessful) {
                val json = JSONObject(it.body?.string() ?: "")
                return json.getString("id")
            } else {
                Log.e(TAG, "Create folder failed: ${it.code} - ${it.message}")
            }
        }
        return null
    }

    /**
     * Finds a specific project file "project_<uuid>.json" in the backup folder.
     * Returns the file ID and the last modification timestamp.
     */
    suspend fun findProjectFile(context: Context, email: String, folderId: String, uuid: String): Pair<String, Long>? {
        val name = "project_$uuid.json"
        val url = "https://www.googleapis.com/drive/v3/files?q='$folderId' in parents and name='$name' and trashed=false&fields=files(id,modifiedTime)"
        
        val response = executeWithAuth(context, email) { token ->
            Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .get()
                .build()
        } ?: return null

        response.use {
            if (it.isSuccessful) {
                val json = JSONObject(it.body?.string() ?: "")
                val files = json.optJSONArray("files")
                if (files != null && files.length() > 0) {
                    val fileObj = files.getJSONObject(0)
                    val id = fileObj.getString("id")
                    // Example modifiedTime: "2026-06-06T15:53:35.000Z"
                    val modTimeString = fileObj.optString("modifiedTime")
                    val modTime = try {
                        val format = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
                        format.timeZone = java.util.TimeZone.getTimeZone("UTC")
                        format.parse(modTimeString)?.time ?: 0L
                    } catch (e: Exception) {
                        0L
                    }
                    return Pair(id, modTime)
                }
            } else {
                Log.e(TAG, "Find project file request failed: ${it.code} - ${it.message}")
            }
        }
        return null
    }

    /**
     * Creates metadata on Google Drive, then uploads raw media.
     */
    suspend fun createAndUploadProjectFile(
        context: Context,
        email: String,
        folderId: String,
        uuid: String,
        content: String
    ): Boolean {
        val name = "project_$uuid.json"
        // 1. Create file metadata
        val metadataUrl = "https://www.googleapis.com/drive/v3/files"
        val metadataBody = JSONObject().apply {
            put("name", name)
            put("parents", JSONArray().put(folderId))
        }.toString()

        val createResponse = executeWithAuth(context, email) { token ->
            Request.Builder()
                .url(metadataUrl)
                .header("Authorization", "Bearer $token")
                .post(RequestBody.create(JSON_MEDIA_TYPE, metadataBody))
                .build()
        } ?: return false

        val fileId = createResponse.use {
            if (it.isSuccessful) {
                val json = JSONObject(it.body?.string() ?: "")
                json.getString("id")
            } else {
                Log.e(TAG, "Create project file metadata failed: ${it.code}")
                null
            }
        } ?: return false

        // 2. Upload media
        return uploadFileMedia(context, email, fileId, content)
    }

    /**
     * Updates an existing Google Drive file content with new JSON content.
     */
    suspend fun uploadFileMedia(
        context: Context,
        email: String,
        fileId: String,
        content: String
    ): Boolean {
        val mediaUrl = "https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=media"
        val response = executeWithAuth(context, email) { token ->
            Request.Builder()
                .url(mediaUrl)
                .header("Authorization", "Bearer $token")
                .patch(RequestBody.create(JSON_MEDIA_TYPE, content))
                .build()
        } ?: return false

        response.use {
            return if (it.isSuccessful) {
                Log.d(TAG, "File content uploaded successfully for fileId: $fileId")
                true
            } else {
                Log.e(TAG, "Upload file content failed: ${it.code} - ${it.message}")
                false
            }
        }
    }

    /**
     * Lists all file metadata inside the application's unique backup folder.
     */
    suspend fun listAllProjectFiles(context: Context, email: String, folderId: String): List<GoogleDriveFileInfo> {
        val url = "https://www.googleapis.com/drive/v3/files?q='$folderId' in parents and trashed=false&fields=files(id,name,modifiedTime)"
        val response = executeWithAuth(context, email) { token ->
            Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .get()
                .build()
        } ?: return emptyList()

        response.use {
            if (it.isSuccessful) {
                val json = JSONObject(it.body?.string() ?: "")
                val files = json.optJSONArray("files") ?: return emptyList()
                val list = mutableListOf<GoogleDriveFileInfo>()
                for (i in 0 until files.length()) {
                    val f = files.getJSONObject(i)
                    val name = f.getString("name")
                    if (name.startsWith("project_") && name.endsWith(".json")) {
                        val id = f.getString("id")
                        val modTimeString = f.optString("modifiedTime")
                        val modTime = try {
                            val format = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
                            format.timeZone = java.util.TimeZone.getTimeZone("UTC")
                            format.parse(modTimeString)?.time ?: 0L
                        } catch (e: Exception) {
                            0L
                        }
                        
                        // Extract UUID from name (project_<uuid>.json)
                        val uuid = name.removePrefix("project_").removeSuffix(".json")
                        list.add(GoogleDriveFileInfo(id, uuid, modTime))
                    }
                }
                return list
            } else {
                Log.e(TAG, "List files failed: ${it.code}")
            }
        }
        return emptyList()
    }

    /**
     * Deletes a file on Google Drive.
     */
    suspend fun deleteFile(context: Context, email: String, fileId: String): Boolean {
        val url = "https://www.googleapis.com/drive/v3/files/$fileId"
        val response = executeWithAuth(context, email) { token ->
            Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .delete()
                .build()
        } ?: return false
        response.use {
            return it.isSuccessful
        }
    }

    /**
     * Downloads and parses string content for a given Google Drive fileId.
     */
    suspend fun downloadFileContent(context: Context, email: String, fileId: String): String? {
        val url = "https://www.googleapis.com/drive/v3/files/$fileId?alt=media"
        val response = executeWithAuth(context, email) { token ->
            Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .get()
                .build()
        } ?: return null

        response.use {
            return if (it.isSuccessful) {
                it.body?.string()
            } else {
                Log.e(TAG, "Download file content failed: ${it.code}")
                null
            }
        }
    }
}

data class GoogleDriveFileInfo(
    val fileId: String,
    val uuid: String,
    val modifiedTime: Long
)
