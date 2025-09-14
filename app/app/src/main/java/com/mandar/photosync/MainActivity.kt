package com.mandar.photosync

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private val apiService = RetrofitInstance.api
    private lateinit var syncNowButton: Button
    private lateinit var refreshButton: Button
    private lateinit var statusText: TextView
    private lateinit var statusIcon: ImageView
    private lateinit var syncProgress: ProgressBar
    private lateinit var lastSyncText: TextView
    private lateinit var totalPhotosText: TextView // Changed from photosCountText
    private lateinit var lastPhotoText: TextView
    private lateinit var settingsButton: Button // Added this
    private lateinit var recyclerView: RecyclerView

    private lateinit var photoAdapter: PhotoAdapter
    private val photoList = mutableListOf<PhotoItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializeViews()
        setupRecyclerView()
        setupClickListeners()
        loadGalleryPhotos()
        loadStatistics()
        requestPermissions()
    }

    private fun initializeViews() {
        syncNowButton = findViewById(R.id.syncNowButton)
        refreshButton = findViewById(R.id.refreshButton)
        statusText = findViewById(R.id.statusText)
        statusIcon = findViewById(R.id.statusIcon)
        syncProgress = findViewById(R.id.syncProgress)
        lastSyncText = findViewById(R.id.lastSyncText)
        totalPhotosText = findViewById(R.id.photosCountText) // This matches XML
        lastPhotoText = findViewById(R.id.lastPhotoText) // This matches XML
        settingsButton = findViewById(R.id.settingsButton) // This matches XML
        recyclerView = findViewById(R.id.recyclerView)
    }

    private fun setupRecyclerView() {
        photoAdapter = PhotoAdapter(photoList)
        recyclerView.apply {
            layoutManager = GridLayoutManager(this@MainActivity, 3)
            adapter = photoAdapter
            setHasFixedSize(true)
        }
    }

    private fun setupClickListeners() {
        syncNowButton.setOnClickListener { startSync() }
        refreshButton.setOnClickListener { loadGalleryPhotos() }
        settingsButton.setOnClickListener { showSettings() } // Now using the correct variable
    }

    private fun loadStatistics() {
        val prefs = getSharedPreferences("photosync", Context.MODE_PRIVATE)
        val syncedCount = prefs.getInt("photos_synced", 0)
        val lastPhoto = prefs.getString("last_photo", "--")

        totalPhotosText.text = syncedCount.toString() // Fixed reference
        lastPhotoText.text = lastPhoto // Fixed reference

        val lastSyncTime = prefs.getLong("last_sync_time", 0)
        if (lastSyncTime > 0) {
            val dateFormat = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault())
            val date = Date(lastSyncTime * 1000)
            lastSyncText.text = "Last sync: ${dateFormat.format(date)}"
        }
    }

    private fun updateStatistics() {
        val prefs = getSharedPreferences("photosync", Context.MODE_PRIVATE)
        val newCount = prefs.getInt("photos_synced", 0) + 1
        val lastPhoto = "Photo_${System.currentTimeMillis()}.jpg"

        prefs.edit()
            .putInt("photos_synced", newCount)
            .putString("last_photo", lastPhoto)
            .putLong("last_sync_time", System.currentTimeMillis() / 1000)
            .apply()

        totalPhotosText.text = newCount.toString() // Fixed reference
        lastPhotoText.text = lastPhoto // Fixed reference

        val dateFormat = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault())
        lastSyncText.text = "Last sync: ${dateFormat.format(Date())}"
    }

    private fun startSync() {
        if (!hasStoragePermission()) {
            Toast.makeText(this, "Please grant storage permissions first", Toast.LENGTH_SHORT).show()
            requestPermissions()
            return
        }

        statusText.text = "Syncing photos..."
        statusIcon.setImageResource(android.R.drawable.ic_popup_sync)
        syncProgress.visibility = View.VISIBLE
        syncNowButton.isEnabled = false

        CoroutineScope(Dispatchers.IO).launch {
            try {
                startPhotoSyncService()
                manuallySyncPhotos()

                withContext(Dispatchers.Main) {
                    statusText.text = "Sync complete"
                    statusIcon.setImageResource(android.R.drawable.ic_menu_upload)
                    syncProgress.visibility = View.GONE
                    syncNowButton.isEnabled = true
                    updateStatistics()
                    loadGalleryPhotos()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    statusText.text = "Sync failed"
                    statusIcon.setImageResource(android.R.drawable.ic_dialog_alert)
                    syncProgress.visibility = View.GONE
                    syncNowButton.isEnabled = true
                    Toast.makeText(this@MainActivity, "Sync failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun loadGalleryPhotos() {
        if (!hasStoragePermission()) {
            Toast.makeText(this, "Storage permission required", Toast.LENGTH_SHORT).show()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val photos = loadPhotosFromGallery()
                withContext(Dispatchers.Main) {
                    photoList.clear()
                    photoList.addAll(photos)
                    photoAdapter.notifyDataSetChanged()
                    totalPhotosText.text = "${photos.size} photos" // Fixed reference
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error loading photos: ${e.message}")
            }
        }
    }

    private fun loadPhotosFromGallery(): List<PhotoItem> {
        val photos = mutableListOf<PhotoItem>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.DATE_ADDED
        )

        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndex(MediaStore.Images.Media._ID)
            val nameIndex = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
            val dataIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATA)
            val dateIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATE_ADDED)

            while (cursor.moveToNext()) {
                if (idIndex >= 0 && nameIndex >= 0 && dataIndex >= 0 && dateIndex >= 0) {
                    val id = cursor.getLong(idIndex)
                    val name = cursor.getString(nameIndex)
                    val path = cursor.getString(dataIndex)
                    val dateAdded = cursor.getLong(dateIndex)
                    val uri = Uri.withAppendedPath(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id.toString()
                    )

                    photos.add(PhotoItem(id, name, path, dateAdded, uri))
                }
            }
        }
        return photos
    }

    private suspend fun manuallySyncPhotos() {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.DATE_ADDED
        )

        val prefs = getSharedPreferences("photosync", Context.MODE_PRIVATE)
        val lastSyncTime = prefs.getLong("last_sync_time", 0)

        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            "${MediaStore.Images.Media.DATE_ADDED} > ?",
            arrayOf(lastSyncTime.toString()),
            null
        )?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
            val dataIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATA)
            val dateIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATE_ADDED)

            var latestTimestamp = lastSyncTime

            while (cursor.moveToNext()) {
                if (nameIndex >= 0 && dataIndex >= 0 && dateIndex >= 0) {
                    val name = cursor.getString(nameIndex)
                    val path = cursor.getString(dataIndex)
                    val dateAdded = cursor.getLong(dateIndex)

                    uploadPhotoDirectly(path, name)

                    if (dateAdded > latestTimestamp) {
                        latestTimestamp = dateAdded
                    }
                }
            }

            prefs.edit().putLong("last_sync_time", latestTimestamp).apply()
        }
    }

    private suspend fun uploadPhotoDirectly(filePath: String, fileName: String) {
        try {
            val file = java.io.File(filePath)
            if (file.exists()) {
                val requestBody = file.asRequestBody("image/*".toMediaType())
                val filePart = MultipartBody.Part.createFormData("file", fileName, requestBody)

                val response = apiService.uploadPhoto("", filePart)
                if (response.isSuccessful) {
                    Log.d("ManualSync", "Uploaded: $fileName")
                } else {
                    Log.e("ManualSync", "Failed: $fileName - ${response.code()}")
                }
            }
        } catch (e: Exception) {
            Log.e("ManualSync", "Error: ${e.message}")
        }
    }

    private fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun showSettings() {
        Toast.makeText(this, "Settings will be implemented soon", Toast.LENGTH_SHORT).show()
    }

    private fun requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.READ_MEDIA_IMAGES), 101)
        } else {
            requestPermissions(arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE), 101)
        }
    }

    private fun startPhotoSyncService() {
        val serviceIntent = Intent(this, PhotoSyncService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(serviceIntent)
        else startService(serviceIntent)
    }

    private fun schedulePeriodicSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .setRequiresCharging(true)
            .build()

        val syncWork = PeriodicWorkRequestBuilder<PhotoSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "photo_sync_work",
            ExistingPeriodicWorkPolicy.KEEP,
            syncWork
        )
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Permissions granted!", Toast.LENGTH_SHORT).show()
            loadGalleryPhotos()
        }
    }
}