package com.simplepdf.reader.domain.model

import android.net.Uri

/**
 * Domain model representing a PDF document
 */
data class PdfDocument(
    val uri: Uri,
    val title: String,
    val pageCount: Int = 0,
    val currentPage: Int = 0,
    val lastAccessTime: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    val fileSize: Long = 0L
)
