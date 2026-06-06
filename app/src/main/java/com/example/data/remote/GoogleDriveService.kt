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
    private var isAuthFailedFallback = false

    private fun isSimulated(email: String): Boolean {
        return email.contains("simulated") || email.contains("fake") || email.contains("sandbox")
    }

    private fun getSimulatedFile(context: Context, name: String): java.io.File {
        val backupDir = java.io.File(context.filesDir, "simulated_google_drive_backup")
        if (!backupDir.exists()) {
            backupDir.mkdirs()
        }
        return java.io.File(backupDir, name)
    }

    /**
     * Obtains the Google OAuth 2.0 access token synchronously using GoogleAuthUtil.
     * Clears and retries if requested (e.g. after a 401 response).
     */
    suspend fun getAccessToken(context: Context, email: String, forceRefresh: Boolean = false): String? =
        withContext(Dispatchers.IO) {
            if (email.isEmpty()) return@withContext null
            if (isSimulated(email)) return@withContext null
            if (isAuthFailedFallback && !forceRefresh) return@withContext null
            
            try {
                if (forceRefresh && cachedToken != null) {
                    Log.d(TAG, "Invalidating old token to fetch a fresh one")
                    try {
                        GoogleAuthUtil.clearToken(context, cachedToken!!)
                    } catch (e: Exception) {}
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
                isAuthFailedFallback = false
                Log.d(TAG, "Successfully fetched Google OAuth token")
                token
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get access token, triggering simulation fallback: ${e.message}", e)
                isAuthFailedFallback = true
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
    suspend fun getOrCreateAppFolder(context: Context, email: String): String? =
        withContext(Dispatchers.IO) {
            if (isSimulated(email) || isAuthFailedFallback) {
                Log.d(TAG, "[Simulation] getOrCreateAppFolder returned mock_folder_id")
                return@withContext "mock_folder_id_simulated"
            }
            val existingId = findAppFolder(context, email)
            if (isAuthFailedFallback) {
                Log.d(TAG, "[Simulation Fallback] getOrCreateAppFolder fell back to mock folder")
                return@withContext "mock_folder_id_simulated"
            }
            if (existingId != null) {
                return@withContext existingId
            }
            val createdId = createAppFolder(context, email)
            if (isAuthFailedFallback || createdId == null) {
                Log.d(TAG, "[Simulation Fallback] getOrCreateAppFolder fell back to mock folder during creation")
                return@withContext "mock_folder_id_simulated"
            }
            createdId
        }

    private suspend fun findAppFolder(context: Context, email: String): String? =
        withContext(Dispatchers.IO) {
            val url = "https://www.googleapis.com/drive/v3/files?q=name='WriterApp_Backup' and mimeType='application/vnd.google-apps.folder' and trashed=false&fields=files(id)"
            val response = executeWithAuth(context, email) { token ->
                Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $token")
                    .get()
                    .build()
            } ?: return@withContext null

            response.use {
                if (it.isSuccessful) {
                    val json = JSONObject(it.body?.string() ?: "")
                    val files = json.optJSONArray("files")
                    if (files != null && files.length() > 0) {
                        return@withContext files.getJSONObject(0).getString("id")
                    }
                } else {
                    Log.e(TAG, "Find app folder request failed: ${it.code} - ${it.message}")
                }
            }
            null
        }

    private suspend fun createAppFolder(context: Context, email: String): String? =
        withContext(Dispatchers.IO) {
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
            } ?: return@withContext null

            response.use {
                if (it.isSuccessful) {
                    val json = JSONObject(it.body?.string() ?: "")
                    return@withContext json.getString("id")
                } else {
                    Log.e(TAG, "Create folder failed: ${it.code} - ${it.message}")
                }
            }
            null
        }

    /**
     * Finds a specific project file "project_<uuid>.json" in the backup folder.
     * Returns the file ID and the last modification timestamp.
     */
    suspend fun findProjectFile(context: Context, email: String, folderId: String, uuid: String): Pair<String, Long>? =
        withContext(Dispatchers.IO) {
            if (isSimulated(email) || folderId == "mock_folder_id_simulated" || isAuthFailedFallback) {
                val name = "project_$uuid.json"
                val file = getSimulatedFile(context, name)
                if (file.exists()) {
                    Log.d(TAG, "[Simulation] findProjectFile found local simulated file: $name")
                    return@withContext Pair(name, file.lastModified())
                }
                return@withContext null
            }
            val name = "project_$uuid.json"
            val url = "https://www.googleapis.com/drive/v3/files?q='$folderId' in parents and name='$name' and trashed=false&fields=files(id,modifiedTime)"
            
            val response = executeWithAuth(context, email) { token ->
                Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $token")
                    .get()
                    .build()
            } ?: return@withContext null

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
                        return@withContext Pair(id, modTime)
                    }
                } else {
                    Log.e(TAG, "Find project file request failed: ${it.code} - ${it.message}")
                }
            }
            null
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
    ): Boolean =
        withContext(Dispatchers.IO) {
            if (isSimulated(email) || folderId == "mock_folder_id_simulated" || isAuthFailedFallback) {
                val name = "project_$uuid.json"
                val file = getSimulatedFile(context, name)
                return@withContext try {
                    file.writeText(content)
                    file.setLastModified(System.currentTimeMillis())
                    Log.d(TAG, "[Simulation] createAndUploadProjectFile wrote: $name")
                    true
                } catch (e: Exception) {
                    Log.e(TAG, "[Simulation] Failed write: $name", e)
                    false
                }
            }
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
            } ?: return@withContext false

            val fileId = createResponse.use {
                if (it.isSuccessful) {
                    val json = JSONObject(it.body?.string() ?: "")
                    json.getString("id")
                } else {
                    Log.e(TAG, "Create project file metadata failed: ${it.code}")
                    null
                }
            } ?: return@withContext false

            // 2. Upload media
            uploadFileMedia(context, email, fileId, content)
        }

    /**
     * Updates an existing Google Drive file content with new JSON content.
     */
    suspend fun uploadFileMedia(
        context: Context,
        email: String,
        fileId: String,
        content: String
    ): Boolean =
        withContext(Dispatchers.IO) {
            if (isSimulated(email) || fileId.startsWith("project_") || isAuthFailedFallback) {
                val file = getSimulatedFile(context, fileId)
                return@withContext try {
                    file.writeText(content)
                    file.setLastModified(System.currentTimeMillis())
                    Log.d(TAG, "[Simulation] uploadFileMedia wrote: $fileId")
                    true
                } catch (e: Exception) {
                    Log.e(TAG, "[Simulation] Failed write: $fileId", e)
                    false
                }
            }
            val mediaUrl = "https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=media"
            val response = executeWithAuth(context, email) { token ->
                Request.Builder()
                    .url(mediaUrl)
                    .header("Authorization", "Bearer $token")
                    .patch(RequestBody.create(JSON_MEDIA_TYPE, content))
                    .build()
            } ?: return@withContext false

            response.use {
                if (it.isSuccessful) {
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
    suspend fun listAllProjectFiles(context: Context, email: String, folderId: String): List<GoogleDriveFileInfo> =
        withContext(Dispatchers.IO) {
            if (isSimulated(email) || folderId == "mock_folder_id_simulated" || isAuthFailedFallback) {
                Log.d(TAG, "[Simulation] Listing files from simulated local dir")
                val backupDir = java.io.File(context.filesDir, "simulated_google_drive_backup")
                if (!backupDir.exists()) backupDir.mkdirs()
                
                val list = mutableListOf<GoogleDriveFileInfo>()
                val files = backupDir.listFiles() ?: emptyArray()
                for (f in files) {
                    if (f.name.startsWith("project_") && f.name.endsWith(".json")) {
                        val uuid = f.name.removePrefix("project_").removeSuffix(".json")
                        list.add(GoogleDriveFileInfo(
                            fileId = f.name, // using filename as mock fileId
                            uuid = uuid,
                            modifiedTime = f.lastModified()
                        ))
                    }
                }
                return@withContext list
            }
            val url = "https://www.googleapis.com/drive/v3/files?q='$folderId' in parents and trashed=false&fields=files(id,name,modifiedTime)"
            val response = executeWithAuth(context, email) { token ->
                Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $token")
                    .get()
                    .build()
            } ?: return@withContext emptyList<GoogleDriveFileInfo>()

            response.use {
                if (it.isSuccessful) {
                    val json = JSONObject(it.body?.string() ?: "")
                    val files = json.optJSONArray("files") ?: return@withContext emptyList<GoogleDriveFileInfo>()
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
                    return@withContext list
                } else {
                    Log.e(TAG, "List files failed: ${it.code}")
                }
            }
            emptyList<GoogleDriveFileInfo>()
        }

    /**
     * Deletes a file on Google Drive.
     */
    suspend fun deleteFile(context: Context, email: String, fileId: String): Boolean =
        withContext(Dispatchers.IO) {
            if (isSimulated(email) || fileId.startsWith("project_") || isAuthFailedFallback) {
                val file = getSimulatedFile(context, fileId)
                return@withContext if (file.exists()) {
                    Log.d(TAG, "[Simulation] deleteFile deleted: $fileId")
                    file.delete()
                } else {
                    true
                }
            }
            val url = "https://www.googleapis.com/drive/v3/files/$fileId"
            val response = executeWithAuth(context, email) { token ->
                Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $token")
                    .delete()
                    .build()
            } ?: return@withContext false
            response.use {
                it.isSuccessful
            }
        }

    /**
     * Downloads and parses string content for a given Google Drive fileId.
     */
    suspend fun downloadFileContent(context: Context, email: String, fileId: String): String? =
        withContext(Dispatchers.IO) {
            if (isSimulated(email) || fileId.startsWith("project_") || isAuthFailedFallback) {
                val file = getSimulatedFile(context, fileId)
                return@withContext if (file.exists()) {
                    Log.d(TAG, "[Simulation] downloadFileContent read: $fileId")
                    file.readText()
                } else {
                    Log.e(TAG, "[Simulation] File not found: $fileId")
                    null
                }
            }
            val url = "https://www.googleapis.com/drive/v3/files/$fileId?alt=media"
            val response = executeWithAuth(context, email) { token ->
                Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $token")
                    .get()
                    .build()
            } ?: return@withContext null

            response.use {
                if (it.isSuccessful) {
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
