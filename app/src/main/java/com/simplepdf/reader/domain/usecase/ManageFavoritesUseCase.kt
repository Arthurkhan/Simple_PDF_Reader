package com.simplepdf.reader.domain.usecase

import android.net.Uri
import com.simplepdf.reader.domain.model.PdfDocument
import com.simplepdf.reader.domain.repository.PdfRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use case for managing favorite PDFs
 */
class ManageFavoritesUseCase @Inject constructor(
    private val repository: PdfRepository
) {
    fun getFavorites(): Flow<List<PdfDocument>> = repository.getFavorites()
    
    suspend fun addToFavorites(document: PdfDocument) {
        repository.addToFavorites(document)
    }
    
    suspend fun removeFromFavorites(uri: Uri) {
        repository.removeFromFavorites(uri)
    }
    
    suspend fun toggleFavorite(document: PdfDocument) {
        if (repository.isFavorite(document.uri)) {
            repository.removeFromFavorites(document.uri)
        } else {
            repository.addToFavorites(document)
        }
    }
    
    suspend fun isFavorite(uri: Uri): Boolean = repository.isFavorite(uri)
}
