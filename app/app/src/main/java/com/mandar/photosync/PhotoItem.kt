package com.mandar.photosync

import android.net.Uri

data class PhotoItem(
    val id: Long,
    val name: String,
    val path: String,
    val dateAdded: Long,
    val uri: Uri
)