package com.simplepdf.reader.domain.model

/**
 * Represents the different states of the PDF viewer
 */
sealed class PdfViewerState {
    object Idle : PdfViewerState()
    object Loading : PdfViewerState()
    data class Loaded(
        val document: PdfDocument,
        val currentPage: Int = 0,
        val zoomLevel: Float = 1.0f,
        val isLocked: Boolean = false,
        val isImmersiveMode: Boolean = false
    ) : PdfViewerState()
    data class Error(val message: String, val throwable: Throwable? = null) : PdfViewerState()
}

/**
 * Events that can occur in the PDF viewer
 */
sealed class PdfViewerEvent {
    object OpenFilePicker : PdfViewerEvent()
    object ShowFavorites : PdfViewerEvent()
    object ToggleLock : PdfViewerEvent()
    object ToggleImmersiveMode : PdfViewerEvent()
    data class ShowError(val message: String) : PdfViewerEvent()
    data class NavigateToPage(val page: Int) : PdfViewerEvent()
}
