package com.example.util

import android.content.Context
import android.util.Log
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.json.JSONArray
import java.io.IOException

data class DriveFile(
    val id: String,
    val name: String,
    val modifiedTime: Long
)

object GoogleDriveService {
    private const val TAG = "GoogleDriveService"
    private val client = OkHttpClient()

    // Retrieve OAuth2 token
    suspend fun getAccessToken(context: Context, accountEmail: String): String? = withContext(Dispatchers.IO) {
        try {
            val scopes = "oauth2:https://www.googleapis.com/auth/drive.file https://www.googleapis.com/auth/userinfo.email https://www.googleapis.com/auth/userinfo.profile"
            GoogleAuthUtil.getToken(context, accountEmail, scopes)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching access token for $accountEmail: ${e.message}", e)
            throw e
        }
    }

    // Invalidates token when it expires
    suspend fun invalidateToken(context: Context, token: String) = withContext(Dispatchers.IO) {
        try {
            GoogleAuthUtil.clearToken(context, token)
        } catch (e: Exception) {
            Log.e(TAG, "Error invalidating token: ${e.message}", e)
        }
    }

    // Find or create a remote folder
    suspend fun getOrCreateFolder(token: String, folderName: String): String? = withContext(Dispatchers.IO) {
        val searchUrl = "https://www.googleapis.com/drive/v3/files?q=name='$folderName' and mimeType='application/vnd.google-apps.folder' and trashed=false&fields=files(id)"
        val request = Request.Builder()
            .url(searchUrl)
            .header("Authorization", "Bearer $token")
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: ""
                    val json = JSONObject(bodyStr)
                    val files = json.optJSONArray("files")
                    if (files != null && files.length() > 0) {
                        return@withContext files.getJSONObject(0).getString("id")
                    }
                } else {
                    Log.e(TAG, "Failed searching folder: ${response.code} - ${response.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error looking up folder: ${e.message}", e)
        }

        // Folder not found, create it
        val createUrl = "https://www.googleapis.com/drive/v3/files"
        val payload = JSONObject().apply {
            put("name", folderName)
            put("mimeType", "application/vnd.google-apps.folder")
        }.toString()

        val postRequest = Request.Builder()
            .url(createUrl)
            .header("Authorization", "Bearer $token")
            .post(payload.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()))
            .build()

        try {
            client.newCall(postRequest).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: ""
                    val json = JSONObject(bodyStr)
                    return@withContext json.getString("id")
                } else {
                    Log.e(TAG, "Failed creating folder: ${response.code} - ${response.body?.string()}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating folder: ${e.message}", e)
        }
        null
    }

    // Fetch list of backup files inside the folder
    suspend fun listBackups(token: String, folderId: String): List<DriveFile> = withContext(Dispatchers.IO) {
        val list = mutableListOf<DriveFile>()
        val queryUrl = "https://www.googleapis.com/drive/v3/files?q='$folderId' in parents and trashed=false&fields=files(id,name,modifiedTime)&orderBy=modifiedTime desc"
        val request = Request.Builder()
            .url(queryUrl)
            .header("Authorization", "Bearer $token")
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: ""
                    val json = JSONObject(bodyStr)
                    val files = json.optJSONArray("files")
                    if (files != null) {
                        for (i in 0 until files.length()) {
                            val f = files.getJSONObject(i)
                            val modifiedTimeStr = f.optString("modifiedTime", "")
                            // Simple ISO 8601 parsing fallback or system currentTimeMillis
                            val timeMs = try {
                                java.time.Instant.parse(modifiedTimeStr).toEpochMilli()
                            } catch (exc: Exception) {
                                System.currentTimeMillis()
                            }
                            list.add(
                                DriveFile(
                                    id = f.getString("id"),
                                    name = f.getString("name"),
                                    modifiedTime = timeMs
                                )
                            )
                        }
                    }
                } else {
                    Log.e(TAG, "Failed to list backups: ${response.code} - ${response.body?.string()}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error listing backups: ${e.message}", e)
        }
        list
    }

    // Upload backup file (create or update)
    suspend fun uploadBackup(token: String, folderId: String, fileName: String, fileContent: String): Boolean = withContext(Dispatchers.IO) {
        // Find existing file if any
        var existingFileId: String? = null
        val findUrl = "https://www.googleapis.com/drive/v3/files?q=name='$fileName' and '$folderId' in parents and trashed=false&fields=files(id)"
        val searchRequest = Request.Builder()
            .url(findUrl)
            .header("Authorization", "Bearer $token")
            .get()
            .build()

        try {
            client.newCall(searchRequest).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: ""
                    val json = JSONObject(bodyStr)
                    val files = json.optJSONArray("files")
                    if (files != null && files.length() > 0) {
                        existingFileId = files.getJSONObject(0).getString("id")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if backup file exists: ${e.message}", e)
        }

        if (existingFileId != null) {
            // Update exist file content
            val updateUrl = "https://www.googleapis.com/upload/drive/v3/files/$existingFileId?uploadType=media"
            val updateRequest = Request.Builder()
                .url(updateUrl)
                .header("Authorization", "Bearer $token")
                .patch(fileContent.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()))
                .build()

            try {
                client.newCall(updateRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        Log.i(TAG, "Backup updated successfully on Drive: $existingFileId")
                        return@withContext true
                    } else {
                        Log.e(TAG, "Failed updating file content: ${response.code} - ${response.body?.string()}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating backup: ${e.message}", e)
            }
        } else {
            // Create a new metadata placeholder
            val createUrl = "https://www.googleapis.com/drive/v3/files"
            val payload = JSONObject().apply {
                put("name", fileName)
                put("parents", JSONArray().put(folderId))
            }.toString()

            val createRequest = Request.Builder()
                .url(createUrl)
                .header("Authorization", "Bearer $token")
                .post(payload.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()))
                .build()

            var newFileId: String? = null
            try {
                client.newCall(createRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        val bodyStr = response.body?.string() ?: ""
                        newFileId = JSONObject(bodyStr).getString("id")
                    } else {
                        Log.e(TAG, "Failed creating file metadata: ${response.code} - ${response.body?.string()}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error creating metadata: ${e.message}", e)
            }

            if (newFileId != null) {
                // Upload content to the newly created metadata placeholder
                val mediaUrl = "https://www.googleapis.com/upload/drive/v3/files/$newFileId?uploadType=media"
                val mediaRequest = Request.Builder()
                    .url(mediaUrl)
                    .header("Authorization", "Bearer $token")
                    .patch(fileContent.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()))
                    .build()

                try {
                    client.newCall(mediaRequest).execute().use { response1 ->
                        if (response1.isSuccessful) {
                            Log.i(TAG, "Backup created and content uploaded on Drive: $newFileId")
                            return@withContext true
                        } else {
                            Log.e(TAG, "Failed patching file content: ${response1.code} - ${response1.body?.string()}")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error uploading new backup: ${e.message}", e)
                }
            }
        }
        false
    }

    // Download content of a Google Drive file by ID
    suspend fun downloadBackup(token: String, fileId: String): String? = withContext(Dispatchers.IO) {
        val downloadUrl = "https://www.googleapis.com/drive/v3/files/$fileId?alt=media"
        val request = Request.Builder()
            .url(downloadUrl)
            .header("Authorization", "Bearer $token")
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    return@withContext response.body?.string()
                } else {
                    Log.e(TAG, "Failed to download backup content: ${response.code} - ${response.body?.string()}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading file: ${e.message}", e)
        }
        null
    }
}
