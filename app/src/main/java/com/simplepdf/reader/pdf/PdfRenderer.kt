package com.simplepdf.reader.pdf

import android.graphics.Bitmap
import android.net.Uri

/**
 * Abstraction layer for PDF rendering to allow easy swapping of PDF libraries.
 * This interface will help us migrate from AndroidPdfViewer to a lightweight alternative.
 */
interface PdfRenderer {
    /**
     * Opens a PDF document from the given URI.
     * @param uri The URI of the PDF document
     * @return PdfDocument containing information about the opened PDF
     * @throws Exception if the PDF cannot be opened
     */
    suspend fun openPdf(uri: Uri): PdfDocument
    
    /**
     * Renders a specific page of the PDF as a Bitmap.
     * @param pageIndex The index of the page to render (0-based)
     * @param width The desired width of the rendered bitmap
     * @param height The desired height of the rendered bitmap
     * @return Bitmap of the rendered page
     * @throws Exception if the page cannot be rendered
     */
    suspend fun renderPage(pageIndex: Int, width: Int, height: Int): Bitmap
    
    /**
     * Gets the total number of pages in the currently opened PDF.
     * @return The page count
     */
    fun getPageCount(): Int
    
    /**
     * Closes the PDF document and releases all resources.
     */
    fun close()
}

/**
 * Data class representing a PDF document.
 */
data class PdfDocument(
    val uri: Uri,
    val title: String,
    val pageCount: Int,
    val pageWidth: Int,
    val pageHeight: Int
)
