package com.simplepdf.reader.domain.repository

import android.net.Uri
import com.simplepdf.reader.domain.model.PdfDocument
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for PDF operations
 */
interface PdfRepository {
    /**
     * Load a PDF document from URI
     */
    suspend fun loadPdf(uri: Uri): Result<PdfDocument>
    
    /**
     * Get all favorite PDFs
     */
    fun getFavorites(): Flow<List<PdfDocument>>
    
    /**
     * Add a PDF to favorites
     */
    suspend fun addToFavorites(document: PdfDocument)
    
    /**
     * Remove a PDF from favorites
     */
    suspend fun removeFromFavorites(uri: Uri)
    
    /**
     * Check if a PDF is in favorites
     */
    suspend fun isFavorite(uri: Uri): Boolean
    
    /**
     * Update the last accessed page for a PDF
     */
    suspend fun updateLastPage(uri: Uri, page: Int)
    
    /**
     * Get recent PDFs
     */
    fun getRecentPdfs(limit: Int = 10): Flow<List<PdfDocument>>
    
    /**
     * Clear all data
     */
    suspend fun clearAllData()
}
