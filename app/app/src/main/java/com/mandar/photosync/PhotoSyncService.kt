package com.mandar.photosync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody

class PhotoSyncService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO)
    private lateinit var contentObserver: ContentObserver
    private val CHANNEL_ID = "photo_sync_channel" // Channel ID for notifications

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel() // ← ADD THIS
        startForegroundService()
        setupMediaObserver()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "PhotoSync Service", // Channel name
                NotificationManager.IMPORTANCE_LOW // Importance level
            ).apply {
                description = "Background photo synchronization"
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundService() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID) // ← USE CHANNEL_ID
            .setContentTitle("PhotoSync")
            .setContentText("Monitoring for new photos...")
            .setSmallIcon(android.R.drawable.ic_menu_upload) // ← USE SYSTEM ICON TEMPORARILY
            .build()

        startForeground(1, notification)
    }

    private fun setupMediaObserver() {
        val handler = Handler(mainLooper)
        contentObserver = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                uri?.let {
                    if (it.toString().contains(MediaStore.Images.Media.EXTERNAL_CONTENT_URI.toString())) {
                        scope.launch {
                            checkForNewPhotos()
                        }
                    }
                }
            }
        }

        contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            contentObserver
        )
    }

    private suspend fun checkForNewPhotos() {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DATA
        )

        val selection = "${MediaStore.Images.Media.DATE_ADDED} > ?"
        val selectionArgs = arrayOf((System.currentTimeMillis() / 1000 - 60).toString()) // Last 60 seconds

        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                // Use safe column index lookup to avoid crashes
                val idIndex = cursor.getColumnIndex(MediaStore.Images.Media._ID)
                val nameIndex = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                val dataIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATA)

                if (idIndex >= 0 && nameIndex >= 0 && dataIndex >= 0) {
                    val id = cursor.getLong(idIndex)
                    val name = cursor.getString(nameIndex)
                    val path = cursor.getString(dataIndex)

                    // Upload the new photo
                    uploadPhoto(path, name)
                }
            }
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

                val response = RetrofitInstance.api.uploadPhoto("", filePart)
                if (response.isSuccessful) {
                    Log.d("PhotoSync", "Uploaded: $fileName")
                } else {
                    Log.e("PhotoSync", "Failed to upload: $fileName - ${response.code()}")
                }
            }
        } catch (e: Exception) {
            Log.e("PhotoSync", "Error uploading $fileName: ${e.message}")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        contentResolver.unregisterContentObserver(contentObserver)
    }
}