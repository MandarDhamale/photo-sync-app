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

import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private val apiService: PhotoSyncApiService get() = RetrofitInstance.getApi(applicationContext)
    private lateinit var syncNowButton: com.google.android.material.floatingactionbutton.FloatingActionButton
    private lateinit var addPhotosButton: ImageView
    private lateinit var settingsButton: ImageView
    private lateinit var syncProgress: ProgressBar
    private lateinit var toolbar: androidx.appcompat.widget.Toolbar
    
    private val barcodeLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents == null) {
            Toast.makeText(this, "Cancelled pairing", Toast.LENGTH_LONG).show()
        } else {
            try {
                val json = JSONObject(result.contents)
                val ip = json.getString("ip")
                val port = json.getInt("port")
                val token = json.getString("token")
                val prefs = getSharedPreferences("photosync", Context.MODE_PRIVATE)
                prefs.edit()
                    .putString("server_ip", ip)
                    .putInt("server_port", port)
                    .putString("server_token", token)
                    .apply()
                Toast.makeText(this, "Paired successfully with $ip", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Invalid QR code", Toast.LENGTH_LONG).show()
            }
        }
    }
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
        checkPartialAccess()
    }

    private fun initializeViews() {
        syncNowButton = findViewById(R.id.syncNowButton)
        syncProgress = findViewById(R.id.syncProgress)
        addPhotosButton = findViewById(R.id.addPhotosButton)
        settingsButton = findViewById(R.id.settingsButton)
        recyclerView = findViewById(R.id.recyclerView)
        toolbar = findViewById(R.id.toolbar)
    }

    private fun checkPartialAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val hasImages = ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
            val hasPartial = ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED
            
            if (!hasImages && hasPartial) {
                addPhotosButton.visibility = View.VISIBLE
            } else {
                addPhotosButton.visibility = View.GONE
            }
        }
    }

    private fun setupRecyclerView() {
        photoAdapter = PhotoAdapter(photoList)
        recyclerView.apply {
            layoutManager = GridLayoutManager(this@MainActivity, 4) // Changed from 3 to 4
            adapter = photoAdapter
            setHasFixedSize(true)
        }
    }

    private fun setupClickListeners() {
        syncNowButton.setOnClickListener { startSync() }
        settingsButton.setOnClickListener { showSettings() }
        addPhotosButton.setOnClickListener { requestPermissions() }
    }

    private fun loadStatistics() {
        val prefs = getSharedPreferences("photosync", Context.MODE_PRIVATE)
        val syncedCount = prefs.getInt("photos_synced", 0)
        toolbar.subtitle = "$syncedCount photos synced"
    }

    private fun updateStatistics(syncedThisSession: Int) {
        val prefs = getSharedPreferences("photosync", Context.MODE_PRIVATE)
        val newCount = prefs.getInt("photos_synced", 0) + syncedThisSession
        
        prefs.edit()
            .putInt("photos_synced", newCount)
            .putLong("last_sync_time", System.currentTimeMillis() / 1000)
            .apply()

        toolbar.subtitle = "$newCount photos synced"
    }

    private fun startSync() {
        val prefs = getSharedPreferences("photosync", Context.MODE_PRIVATE)
        if (!prefs.contains("server_ip")) {
            Toast.makeText(this, "Please pair with server first (Settings)", Toast.LENGTH_LONG).show()
            showSettings()
            return
        }

        if (!hasStoragePermission()) {
            Toast.makeText(this, "Please grant storage permissions first", Toast.LENGTH_SHORT).show()
            requestPermissions()
            return
        }

        syncProgress.visibility = View.VISIBLE
        syncNowButton.isEnabled = false

        CoroutineScope(Dispatchers.IO).launch {
            try {
                startPhotoSyncService()
                val syncedCount = manuallySyncPhotos()

                withContext(Dispatchers.Main) {
                    syncProgress.visibility = View.GONE
                    syncNowButton.isEnabled = true
                    updateStatistics(syncedCount)
                    Toast.makeText(this@MainActivity, "Sync complete", Toast.LENGTH_SHORT).show()
                    loadGalleryPhotos()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
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

    private suspend fun manuallySyncPhotos(): Int {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.DATE_ADDED
        )

        val prefs = getSharedPreferences("photosync", Context.MODE_PRIVATE)
        val lastSyncTime = prefs.getLong("last_sync_time", 0)
        var syncedThisSession = 0

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

                    val success = uploadPhotoDirectly(path, name)
                    if (success) {
                        syncedThisSession++
                    }

                    if (dateAdded > latestTimestamp) {
                        latestTimestamp = dateAdded
                    }
                }
            }

            prefs.edit().putLong("last_sync_time", latestTimestamp).apply()
        }
        return syncedThisSession
    }

    private suspend fun uploadPhotoDirectly(filePath: String, fileName: String): Boolean {
        try {
            val file = java.io.File(filePath)
            if (file.exists()) {
                val requestBody = file.asRequestBody("image/*".toMediaType())
                val filePart = MultipartBody.Part.createFormData("file", fileName, requestBody)

                val response = apiService.uploadPhoto(filePart)
                if (response.isSuccessful) {
                    Log.d("ManualSync", "Uploaded: $fileName")
                    return true
                } else {
                    Log.e("ManualSync", "Failed: $fileName - ${response.code()}")
                }
            }
        } catch (e: Exception) {
            Log.e("ManualSync", "Error: ${e.message}")
        }
        return false
    }

    private fun hasStoragePermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED ||
                   ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
        } else {
            return ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun showSettings() {
        val options = ScanOptions()
        options.setPrompt("Scan Photo Sync QR Code on your PC")
        options.setBeepEnabled(true)
        options.setOrientationLocked(false)
        barcodeLauncher.launch(options)
    }

    private fun requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            requestPermissions(arrayOf(android.Manifest.permission.READ_MEDIA_IMAGES, android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED), 101)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
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
        if (requestCode == 101) {
            checkPartialAccess()
            if (hasStoragePermission()) {
                loadGalleryPhotos()
            }
        }
    }
}