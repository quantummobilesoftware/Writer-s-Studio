package com.example.util

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

object ImageStorageUtil {

    /**
     * Copies a user-selected image URI into the app's internal files directory
     * so it is encapsulated inside the offline writer project workspace.
     */
    fun copyImageToProject(context: Context, imageUri: Uri): String? {
        val resolver = context.contentResolver
        val type = resolver.getType(imageUri) ?: "image/png"
        val extension = when {
            type.contains("jpeg") || type.contains("jpg") -> "jpg"
            type.contains("webp") -> "webp"
            else -> "png"
        }
        
        val filename = "img_${UUID.randomUUID()}.$extension"
        val targetFile = File(context.filesDir, filename)
        
        return try {
            val inputStream: InputStream? = resolver.openInputStream(imageUri)
            if (inputStream != null) {
                val outputStream = FileOutputStream(targetFile)
                val buffer = ByteArray(4 * 1024)
                var read: Int
                while (inputStream.read(buffer).also { read = it } != -1) {
                    outputStream.write(buffer, 0, read)
                }
                outputStream.flush()
                outputStream.close()
                inputStream.close()
                targetFile.absolutePath
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
