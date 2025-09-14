package com.mandar.photosync

// data class that matches the expected JSON from the server
data class ApiResponse(
    val message: String,
    val fileId: String? //
)