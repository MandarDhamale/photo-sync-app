package com.mandar.photosync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import android.provider.MediaStore
import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody

class PhotoSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    // Add the API service instance
    private val apiService = RetrofitInstance.api

    override suspend fun doWork(): Result {
        return try {
            checkAndUploadNewPhotos()
            Result.success()
        } catch (e: Exception) {
            Log.e("PhotoSyncWorker", "Worker failed: ${e.message}")
            Result.failure()
        }
    }

    private suspend fun checkAndUploadNewPhotos() {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.DATE_ADDED  // Added DATE_ADDED to projection
        )

        // Get last sync time from SharedPreferences
        val prefs = applicationContext.getSharedPreferences("photosync", Context.MODE_PRIVATE)
        val lastSyncTime = prefs.getLong("last_sync_time", 0)

        val selection = "${MediaStore.Images.Media.DATE_ADDED} > ?"
        val selectionArgs = arrayOf(lastSyncTime.toString())

        applicationContext.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            "${MediaStore.Images.Media.DATE_ADDED} ASC"
        )?.use { cursor ->
            // Use safe column index lookup
            val idIndex = cursor.getColumnIndex(MediaStore.Images.Media._ID)
            val nameIndex = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
            val dataIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATA)
            val dateIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATE_ADDED)

            var latestTimestamp = lastSyncTime

            while (cursor.moveToNext()) {
                if (nameIndex >= 0 && dataIndex >= 0 && dateIndex >= 0) {
                    val name = cursor.getString(nameIndex)
                    val path = cursor.getString(dataIndex)
                    val dateAdded = cursor.getLong(dateIndex)

                    // Upload each new photo
                    uploadPhoto(path, name)

                    // Track the latest timestamp
                    if (dateAdded > latestTimestamp) {
                        latestTimestamp = dateAdded
                    }
                }
            }

            // Update last sync time to the most recent photo's timestamp
            prefs.edit().putLong("last_sync_time", latestTimestamp).apply()
            Log.d("PhotoSyncWorker", "Sync completed. New last sync time: $latestTimestamp")
        }
    }

    private suspend fun uploadPhoto(filePath: String, fileName: String) {
        try {
            val file = java.io.File(filePath)
            if (file.exists()) {
                val requestBody = file.asRequestBody("image/*".toMediaType())
                val filePart = MultipartBody.Part.createFormData(
                    "file",
                    fileName,
                    requestBody
                )

                // Use the apiService instance instead of RetrofitInstance.api directly
                val response = apiService.uploadPhoto("", filePart)
                if (response.isSuccessful) {
                    Log.d("PhotoSyncWorker", "Uploaded: $fileName")
                    // You could update UI here via LiveData or Broadcast if needed
                } else {
                    Log.e("PhotoSyncWorker", "Failed to upload: $fileName - Code: ${response.code()}")
                }
            } else {
                Log.e("PhotoSyncWorker", "File does not exist: $filePath")
            }
        } catch (e: Exception) {
            Log.e("PhotoSyncWorker", "Error uploading $fileName: ${e.message}")
        }
    }
}