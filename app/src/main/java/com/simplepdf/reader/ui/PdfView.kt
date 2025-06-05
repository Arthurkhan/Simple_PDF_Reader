package com.simplepdf.reader.ui

import android.content.Context
import android.net.Uri
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.simplepdf.reader.databinding.ViewPdfBinding
import com.simplepdf.reader.pdf.NativePdfRenderer
import com.simplepdf.reader.pdf.PdfRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Custom PDF view that replaces the heavy AndroidPdfViewer library.
 * Uses RecyclerView for efficient page rendering and memory management.
 */
class PdfView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    
    private val binding: ViewPdfBinding
    private var pdfRenderer: PdfRenderer? = null
    private var adapter: PdfPageAdapter? = null
    
    // Callbacks
    private var onLoadCompleteListener: ((Int) -> Unit)? = null
    private var onErrorListener: ((Throwable) -> Unit)? = null
    private var onPageErrorListener: ((Int, Throwable) -> Unit)? = null
    
    init {
        binding = ViewPdfBinding.inflate(LayoutInflater.from(context), this, true)
        setupRecyclerView()
    }
    
    private fun setupRecyclerView() {
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            setHasFixedSize(true)
            
            // Add spacing between pages
            addItemDecoration(PageSpacingDecoration(context))
        }
    }
    
    /**
     * Load PDF from URI
     */
    fun fromUri(uri: Uri): Configurator {
        return Configurator(uri, null)
    }
    
    /**
     * Load PDF from File
     */
    fun fromFile(file: File): Configurator {
        return Configurator(null, file)
    }
    
    /**
     * Recycle and clean up resources
     */
    fun recycle() {
        adapter?.cleanup()
        adapter = null
        pdfRenderer?.close()
        pdfRenderer = null
    }
    
    /**
     * Configuration builder for PDF loading
     */
    inner class Configurator(
        private val uri: Uri?,
        private val file: File?
    ) {
        private var defaultPage = 0
        private var enableSwipe = true
        private var swipeHorizontal = false
        private var enableAnnotationRendering = false
        private var spacing = 0
        
        fun defaultPage(page: Int): Configurator {
            defaultPage = page
            return this
        }
        
        fun enableSwipe(enable: Boolean): Configurator {
            enableSwipe = enable
            return this
        }
        
        fun swipeHorizontal(horizontal: Boolean): Configurator {
            swipeHorizontal = horizontal
            if (horizontal) {
                binding.recyclerView.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            }
            return this
        }
        
        fun enableAnnotationRendering(enable: Boolean): Configurator {
            enableAnnotationRendering = enable
            return this
        }
        
        fun spacing(spacing: Int): Configurator {
            this.spacing = spacing
            return this
        }
        
        fun onLoad(listener: (Int) -> Unit): Configurator {
            onLoadCompleteListener = listener
            return this
        }
        
        fun onError(listener: (Throwable) -> Unit): Configurator {
            onErrorListener = listener
            return this
        }
        
        fun onPageError(listener: (Int, Throwable) -> Unit): Configurator {
            onPageErrorListener = listener
            return this
        }
        
        fun load() {
            val lifecycleScope = (context as? androidx.appcompat.app.AppCompatActivity)?.lifecycleScope
                ?: (context as? androidx.fragment.app.Fragment)?.lifecycleScope
                ?: throw IllegalStateException("Context must be AppCompatActivity or Fragment")
            
            lifecycleScope.launch {
                try {
                    // Create renderer
                    pdfRenderer = NativePdfRenderer(context)
                    
                    // Open PDF
                    val pdfUri = when {
                        uri != null -> uri
                        file != null -> Uri.fromFile(file)
                        else -> throw IllegalArgumentException("No PDF source provided")
                    }
                    
                    val document = pdfRenderer?.openPdf(pdfUri)
                        ?: throw IllegalStateException("Failed to open PDF")
                    
                    // Create and set adapter
                    withContext(Dispatchers.Main) {
                        adapter = PdfPageAdapter(
                            context = context,
                            pdfRenderer = pdfRenderer!!,
                            pageCount = document.pageCount,
                            onPageError = onPageErrorListener
                        )
                        
                        binding.recyclerView.adapter = adapter
                        
                        // Scroll to default page
                        if (defaultPage > 0 && defaultPage < document.pageCount) {
                            binding.recyclerView.scrollToPosition(defaultPage)
                        }
                        
                        // Call success callback
                        onLoadCompleteListener?.invoke(document.pageCount)
                    }
                    
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        onErrorListener?.invoke(e)
                    }
                }
            }
        }
    }
}
