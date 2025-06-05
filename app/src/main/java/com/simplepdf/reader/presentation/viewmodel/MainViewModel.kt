package com.simplepdf.reader.presentation.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplepdf.reader.domain.model.PdfDocument
import com.simplepdf.reader.domain.model.PdfViewerEvent
import com.simplepdf.reader.domain.model.PdfViewerState
import com.simplepdf.reader.domain.usecase.LoadPdfUseCase
import com.simplepdf.reader.domain.usecase.ManageFavoritesUseCase
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the main PDF viewer screen
 */
class MainViewModel @Inject constructor(
    private val loadPdfUseCase: LoadPdfUseCase,
    private val manageFavoritesUseCase: ManageFavoritesUseCase
) : ViewModel() {
    
    // State management using StateFlow
    private val _uiState = MutableStateFlow<PdfViewerState>(PdfViewerState.Idle)
    val uiState: StateFlow<PdfViewerState> = _uiState.asStateFlow()
    
    // Events using SharedFlow for one-time events
    private val _events = MutableSharedFlow<PdfViewerEvent>()
    val events: SharedFlow<PdfViewerEvent> = _events.asSharedFlow()
    
    // Favorites flow
    val favorites = manageFavoritesUseCase.getFavorites()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    /**
     * Load a PDF from the given URI
     */
    fun loadPdf(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = PdfViewerState.Loading
            
            loadPdfUseCase(uri)
                .onSuccess { document ->
                    _uiState.value = PdfViewerState.Loaded(
                        document = document,
                        currentPage = document.currentPage,
                        zoomLevel = 1.0f,
                        isLocked = false,
                        isImmersiveMode = false
                    )
                }
                .onFailure { error ->
                    _uiState.value = PdfViewerState.Error(
                        message = error.localizedMessage ?: "Failed to load PDF",
                        throwable = error
                    )
                    _events.emit(PdfViewerEvent.ShowError(
                        error.localizedMessage ?: "Failed to load PDF"
                    ))
                }
        }
    }
    
    /**
     * Update the current page
     */
    fun updateCurrentPage(page: Int) {
        val currentState = _uiState.value
        if (currentState is PdfViewerState.Loaded) {
            _uiState.value = currentState.copy(currentPage = page)
            
            // Save the current page for future reference
            viewModelScope.launch {
                // This will be implemented in the repository later
            }
        }
    }
    
    /**
     * Update zoom level
     */
    fun updateZoomLevel(zoom: Float) {
        val currentState = _uiState.value
        if (currentState is PdfViewerState.Loaded) {
            _uiState.value = currentState.copy(zoomLevel = zoom)
        }
    }
    
    /**
     * Toggle lock mode
     */
    fun toggleLockMode() {
        val currentState = _uiState.value
        if (currentState is PdfViewerState.Loaded) {
            val newLockState = !currentState.isLocked
            _uiState.value = currentState.copy(isLocked = newLockState)
            viewModelScope.launch {
                _events.emit(PdfViewerEvent.ToggleLock)
            }
        }
    }
    
    /**
     * Toggle immersive mode
     */
    fun toggleImmersiveMode() {
        val currentState = _uiState.value
        if (currentState is PdfViewerState.Loaded) {
            val newImmersiveState = !currentState.isImmersiveMode
            _uiState.value = currentState.copy(isImmersiveMode = newImmersiveState)
            viewModelScope.launch {
                _events.emit(PdfViewerEvent.ToggleImmersiveMode)
            }
        }
    }
    
    /**
     * Toggle favorite status for current PDF
     */
    fun toggleFavorite() {
        val currentState = _uiState.value
        if (currentState is PdfViewerState.Loaded) {
            viewModelScope.launch {
                manageFavoritesUseCase.toggleFavorite(currentState.document)
                // Update the state with new favorite status
                val updatedDocument = currentState.document.copy(
                    isFavorite = !currentState.document.isFavorite
                )
                _uiState.value = currentState.copy(document = updatedDocument)
            }
        }
    }
    
    /**
     * Show favorites dialog
     */
    fun showFavorites() {
        viewModelScope.launch {
            _events.emit(PdfViewerEvent.ShowFavorites)
        }
    }
    
    /**
     * Open file picker
     */
    fun openFilePicker() {
        viewModelScope.launch {
            _events.emit(PdfViewerEvent.OpenFilePicker)
        }
    }
    
    /**
     * Navigate to a specific page
     */
    fun navigateToPage(page: Int) {
        viewModelScope.launch {
            _events.emit(PdfViewerEvent.NavigateToPage(page))
        }
    }
    
    /**
     * Clear error state
     */
    fun clearError() {
        _uiState.value = PdfViewerState.Idle
    }
}
