package com.simplepdf.reader.data.repository

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.simplepdf.reader.domain.model.PdfDocument
import com.simplepdf.reader.domain.repository.PdfRepository
import com.simplepdf.reader.utils.FavoritesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of PdfRepository using SharedPreferences for persistence
 */
@Singleton
class PdfRepositoryImpl @Inject constructor(
    private val context: Context,
    private val favoritesManager: FavoritesManager
) : PdfRepository {
    
    private val recentPdfsCache = MutableStateFlow<List<PdfDocument>>(emptyList())
    private val favoritesCache = MutableStateFlow<List<PdfDocument>>(emptyList())
    
    init {
        loadFavoritesIntoCache()
    }
    
    override suspend fun loadPdf(uri: Uri): Result<PdfDocument> = withContext(Dispatchers.IO) {
        try {
            val documentFile = DocumentFile.fromSingleUri(context, uri)
                ?: return@withContext Result.failure(Exception("Cannot access file"))
            
            val title = documentFile.name ?: "Unknown PDF"
            val fileSize = documentFile.length()
            
            // For now, we'll create the document without page count
            // Page count will be determined when the PDF is actually rendered
            val document = PdfDocument(
                uri = uri,
                title = title,
                fileSize = fileSize,
                isFavorite = favoritesManager.isFavorite(uri)
            )
            
            // Add to recent PDFs
            updateRecentPdfs(document)
            
            Result.success(document)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override fun getFavorites(): Flow<List<PdfDocument>> = favoritesCache.asStateFlow()
    
    override suspend fun addToFavorites(document: PdfDocument) = withContext(Dispatchers.IO) {
        favoritesManager.addFavorite(document.uri)
        val updatedDocument = document.copy(isFavorite = true)
        
        // Update caches
        val currentFavorites = favoritesCache.value.toMutableList()
        if (!currentFavorites.any { it.uri == document.uri }) {
            currentFavorites.add(updatedDocument)
            favoritesCache.value = currentFavorites
        }
        
        // Update in recent PDFs if present
        val recentList = recentPdfsCache.value.toMutableList()
        val index = recentList.indexOfFirst { it.uri == document.uri }
        if (index != -1) {
            recentList[index] = updatedDocument
            recentPdfsCache.value = recentList
        }
    }
    
    override suspend fun removeFromFavorites(uri: Uri) = withContext(Dispatchers.IO) {
        favoritesManager.removeFavorite(uri)
        
        // Update caches
        favoritesCache.value = favoritesCache.value.filter { it.uri != uri }
        
        // Update in recent PDFs if present
        val recentList = recentPdfsCache.value.toMutableList()
        val index = recentList.indexOfFirst { it.uri == uri }
        if (index != -1) {
            recentList[index] = recentList[index].copy(isFavorite = false)
            recentPdfsCache.value = recentList
        }
    }
    
    override suspend fun isFavorite(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        favoritesManager.isFavorite(uri)
    }
    
    override suspend fun updateLastPage(uri: Uri, page: Int) = withContext(Dispatchers.IO) {
        // Update in both caches
        recentPdfsCache.value = recentPdfsCache.value.map { doc ->
            if (doc.uri == uri) doc.copy(currentPage = page) else doc
        }
        favoritesCache.value = favoritesCache.value.map { doc ->
            if (doc.uri == uri) doc.copy(currentPage = page) else doc
        }
        
        // In a full implementation, we'd persist this to SharedPreferences
        // For now, it's only in memory
    }
    
    override fun getRecentPdfs(limit: Int): Flow<List<PdfDocument>> = flow {
        emit(recentPdfsCache.value.take(limit))
    }
    
    override suspend fun clearAllData() = withContext(Dispatchers.IO) {
        // Clear SharedPreferences
        context.getSharedPreferences("pdf_favorites", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
        
        // Clear caches
        recentPdfsCache.value = emptyList()
        favoritesCache.value = emptyList()
    }
    
    private fun loadFavoritesIntoCache() {
        val favoriteUris = favoritesManager.getFavorites()
        val favorites = favoriteUris.mapNotNull { uriString ->
            try {
                val uri = Uri.parse(uriString)
                val documentFile = DocumentFile.fromSingleUri(context, uri)
                documentFile?.let {
                    PdfDocument(
                        uri = uri,
                        title = it.name ?: "Unknown PDF",
                        fileSize = it.length(),
                        isFavorite = true
                    )
                }
            } catch (e: Exception) {
                null
            }
        }
        favoritesCache.value = favorites
    }
    
    private fun updateRecentPdfs(document: PdfDocument) {
        val currentList = recentPdfsCache.value.toMutableList()
        // Remove if already exists
        currentList.removeAll { it.uri == document.uri }
        // Add to front
        currentList.add(0, document)
        // Keep only last 20 items
        recentPdfsCache.value = currentList.take(20)
    }
}
