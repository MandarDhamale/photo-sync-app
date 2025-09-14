package com.mandar.photosync

import okhttp3.MediaType
import okhttp3.RequestBody
import java.io.File

fun File.asRequestBody(mediaType: MediaType): RequestBody {
    return RequestBody.create(mediaType, this)
}