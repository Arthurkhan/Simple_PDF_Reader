package com.simplepdf.reader.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import com.github.barteksc.pdfviewer.PDFView
import com.github.barteksc.pdfviewer.PdfiumCore
import com.shockwave.pdfium.PdfDocument as PdfiumDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Implementation of PdfRenderer using the AndroidPdfViewer library.
 * This is a temporary implementation that will be replaced with a lightweight alternative.
 */
class AndroidPdfViewerRenderer(
    private val context: Context
) : PdfRenderer {
    
    private var pdfiumCore: PdfiumCore? = null
    private var pdfiumDocument: PdfiumDocument? = null
    private var currentUri: Uri? = null
    private var pageCount: Int = 0
    
    init {
        pdfiumCore = PdfiumCore(context)
    }
    
    override suspend fun openPdf(uri: Uri): PdfDocument = withContext(Dispatchers.IO) {
        try {
            // Close any previously opened document
            close()
            
            val parcelFileDescriptor = context.contentResolver.openFileDescriptor(uri, "r")
                ?: throw IllegalArgumentException("Cannot open PDF file")
            
            pdfiumDocument = pdfiumCore?.newDocument(parcelFileDescriptor)
                ?: throw IllegalStateException("PDFium core not initialized")
            
            currentUri = uri
            pageCount = pdfiumCore?.getPageCount(pdfiumDocument) ?: 0
            
            // Get the first page dimensions as default
            pdfiumCore?.openPage(pdfiumDocument, 0)
            val pageWidth = pdfiumCore?.getPageWidth(pdfiumDocument, 0) ?: 0
            val pageHeight = pdfiumCore?.getPageHeight(pdfiumDocument, 0) ?: 0
            
            PdfDocument(
                uri = uri,
                title = uri.lastPathSegment ?: "PDF Document",
                pageCount = pageCount,
                pageWidth = pageWidth,
                pageHeight = pageHeight
            )
        } catch (e: Exception) {
            close()
            throw e
        }
    }
    
    override suspend fun renderPage(pageIndex: Int, width: Int, height: Int): Bitmap = 
        withContext(Dispatchers.IO) {
            if (pdfiumDocument == null || pdfiumCore == null) {
                throw IllegalStateException("No PDF document is open")
            }
            
            if (pageIndex < 0 || pageIndex >= pageCount) {
                throw IndexOutOfBoundsException("Page index $pageIndex is out of bounds")
            }
            
            // Open the page
            pdfiumCore?.openPage(pdfiumDocument, pageIndex)
            
            // Create bitmap
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            
            // Render the page
            pdfiumCore?.renderPageBitmap(
                pdfiumDocument,
                bitmap,
                pageIndex,
                0, 0,
                width, height,
                true // annotations
            )
            
            bitmap
        }
    
    override fun getPageCount(): Int = pageCount
    
    override fun close() {
        pdfiumDocument?.let { doc ->
            pdfiumCore?.closeDocument(doc)
        }
        pdfiumDocument = null
        currentUri = null
        pageCount = 0
    }
    
    protected fun finalize() {
        close()
    }
}
