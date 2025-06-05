package com.simplepdf.reader.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer as AndroidPdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Native implementation of PdfRenderer using Android's built-in PdfRenderer API.
 * This replaces the heavy AndroidPdfViewer library, reducing APK size significantly.
 * 
 * Note: Android's PdfRenderer has some limitations:
 * - No support for annotations
 * - No support for forms
 * - Basic rendering only
 * 
 * For our use case (simple PDF viewing), this is sufficient.
 */
class NativePdfRenderer(
    private val context: Context
) : PdfRenderer {
    
    private var fileDescriptor: ParcelFileDescriptor? = null
    private var androidRenderer: AndroidPdfRenderer? = null
    private var currentPage: AndroidPdfRenderer.Page? = null
    private var document: PdfDocument? = null
    
    override suspend fun openPdf(uri: Uri): PdfDocument = withContext(Dispatchers.IO) {
        try {
            // Close any previously opened document
            close()
            
            // Open the PDF file
            fileDescriptor = context.contentResolver.openFileDescriptor(uri, "r")
                ?: throw IOException("Cannot open PDF file: $uri")
            
            // Create the renderer
            androidRenderer = AndroidPdfRenderer(fileDescriptor!!)
            
            val pageCount = androidRenderer?.pageCount ?: 0
            if (pageCount == 0) {
                throw IOException("PDF has no pages")
            }
            
            // Get first page dimensions for the document info
            val firstPage = androidRenderer?.openPage(0)
            val pageWidth = firstPage?.width ?: 0
            val pageHeight = firstPage?.height ?: 0
            firstPage?.close()
            
            // Create and store the document info
            document = PdfDocument(
                uri = uri,
                title = uri.lastPathSegment ?: "PDF Document",
                pageCount = pageCount,
                pageWidth = pageWidth,
                pageHeight = pageHeight
            )
            
            document!!
        } catch (e: Exception) {
            close()
            throw IOException("Failed to open PDF: ${e.message}", e)
        }
    }
    
    override suspend fun renderPage(pageIndex: Int, width: Int, height: Int): Bitmap = 
        withContext(Dispatchers.IO) {
            val renderer = androidRenderer 
                ?: throw IllegalStateException("No PDF document is open")
            
            if (pageIndex < 0 || pageIndex >= renderer.pageCount) {
                throw IndexOutOfBoundsException(
                    "Page index $pageIndex is out of bounds (0..${renderer.pageCount - 1})"
                )
            }
            
            try {
                // Close any currently open page
                currentPage?.close()
                
                // Open the requested page
                currentPage = renderer.openPage(pageIndex)
                
                // Create bitmap with the requested dimensions
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                
                // Render the page
                currentPage?.render(
                    bitmap,
                    null, // destination clip (null = entire bitmap)
                    null, // transformation matrix (null = fill entire bitmap)
                    AndroidPdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                )
                
                // Close the page after rendering
                currentPage?.close()
                currentPage = null
                
                bitmap
            } catch (e: Exception) {
                currentPage?.close()
                currentPage = null
                throw IOException("Failed to render page $pageIndex: ${e.message}", e)
            }
        }
    
    override fun getPageCount(): Int {
        return androidRenderer?.pageCount ?: 0
    }
    
    override fun close() {
        try {
            // Close current page if open
            currentPage?.close()
            currentPage = null
            
            // Close the renderer
            androidRenderer?.close()
            androidRenderer = null
            
            // Close the file descriptor
            fileDescriptor?.close()
            fileDescriptor = null
            
            // Clear document reference
            document = null
        } catch (e: Exception) {
            // Log but don't throw - cleanup should not fail
            e.printStackTrace()
        }
    }
    
    /**
     * Helper function to calculate the optimal bitmap size while maintaining aspect ratio
     */
    fun calculateOptimalSize(pageWidth: Int, pageHeight: Int, maxWidth: Int, maxHeight: Int): Pair<Int, Int> {
        val pageRatio = pageWidth.toFloat() / pageHeight.toFloat()
        val maxRatio = maxWidth.toFloat() / maxHeight.toFloat()
        
        return if (pageRatio > maxRatio) {
            // Page is wider than max bounds - fit to width
            val scaledWidth = maxWidth
            val scaledHeight = (maxWidth / pageRatio).toInt()
            Pair(scaledWidth, scaledHeight)
        } else {
            // Page is taller than max bounds - fit to height
            val scaledHeight = maxHeight
            val scaledWidth = (maxHeight * pageRatio).toInt()
            Pair(scaledWidth, scaledHeight)
        }
    }
}
