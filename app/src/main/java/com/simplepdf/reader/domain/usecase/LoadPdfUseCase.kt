package com.simplepdf.reader.domain.usecase

import android.net.Uri
import com.simplepdf.reader.domain.model.PdfDocument
import com.simplepdf.reader.domain.repository.PdfRepository
import javax.inject.Inject

/**
 * Use case for loading a PDF document
 */
class LoadPdfUseCase @Inject constructor(
    private val repository: PdfRepository
) {
    suspend operator fun invoke(uri: Uri): Result<PdfDocument> {
        return repository.loadPdf(uri)
    }
}
