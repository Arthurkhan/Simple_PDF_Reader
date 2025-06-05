package com.simplepdf.reader.ui

import android.content.Context
import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.simplepdf.reader.databinding.ItemPdfPageBinding
import com.simplepdf.reader.pdf.NativePdfRenderer
import com.simplepdf.reader.pdf.PdfRenderer
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

/**
 * RecyclerView adapter for displaying PDF pages.
 * Implements efficient page rendering with caching and recycling.
 */
class PdfPageAdapter(
    private val context: Context,
    private val pdfRenderer: PdfRenderer,
    private val pageCount: Int,
    private val onPageError: ((Int, Throwable) -> Unit)?
) : RecyclerView.Adapter<PdfPageAdapter.PageViewHolder>() {
    
    private val renderScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val pageCache = ConcurrentHashMap<Int, Bitmap>()
    private val renderingJobs = ConcurrentHashMap<Int, Job>()
    
    // Maximum cache size (in pages)
    private val maxCacheSize = 5
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val binding = ItemPdfPageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PageViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        holder.bind(position)
    }
    
    override fun getItemCount(): Int = pageCount
    
    override fun onViewRecycled(holder: PageViewHolder) {
        super.onViewRecycled(holder)
        holder.cancelRendering()
    }
    
    fun cleanup() {
        renderScope.cancel()
        pageCache.clear()
        renderingJobs.clear()
    }
    
    inner class PageViewHolder(
        private val binding: ItemPdfPageBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        private var currentJob: Job? = null
        private var currentPage: Int = -1
        
        fun bind(pageIndex: Int) {
            currentPage = pageIndex
            
            // Show loading state
            binding.apply {
                pageImageView.setImageBitmap(null)
                pageNumberTextView.text = "Page ${pageIndex + 1}"
                loadingProgressBar.visibility = android.view.View.VISIBLE
            }
            
            // Check cache first
            val cachedBitmap = pageCache[pageIndex]
            if (cachedBitmap != null) {
                displayBitmap(cachedBitmap)
                return
            }
            
            // Cancel any existing rendering job for this holder
            currentJob?.cancel()
            
            // Start new rendering job
            currentJob = renderScope.launch {
                try {
                    // Check if already being rendered
                    renderingJobs[pageIndex]?.join()
                    
                    // Check cache again after waiting
                    val bitmap = pageCache[pageIndex]
                    if (bitmap != null) {
                        withContext(Dispatchers.Main) {
                            if (currentPage == pageIndex) {
                                displayBitmap(bitmap)
                            }
                        }
                        return@launch
                    }
                    
                    // Start rendering
                    val renderJob = launch {
                        val screenWidth = binding.root.width.coerceAtLeast(
                            context.resources.displayMetrics.widthPixels
                        )
                        val pageHeight = (screenWidth * 1.414).toInt() // A4 aspect ratio
                        
                        val renderedBitmap = pdfRenderer.renderPage(
                            pageIndex,
                            screenWidth,
                            pageHeight
                        )
                        
                        // Cache the bitmap
                        pageCache[pageIndex] = renderedBitmap
                        
                        // Manage cache size
                        if (pageCache.size > maxCacheSize) {
                            evictOldestPages()
                        }
                        
                        withContext(Dispatchers.Main) {
                            if (currentPage == pageIndex) {
                                displayBitmap(renderedBitmap)
                            }
                        }
                    }
                    
                    renderingJobs[pageIndex] = renderJob
                    renderJob.join()
                    renderingJobs.remove(pageIndex)
                    
                } catch (e: CancellationException) {
                    // Job was cancelled, ignore
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        if (currentPage == pageIndex) {
                            binding.loadingProgressBar.visibility = android.view.View.GONE
                            onPageError?.invoke(pageIndex, e)
                        }
                    }
                }
            }
        }
        
        fun cancelRendering() {
            currentJob?.cancel()
            currentJob = null
        }
        
        private fun displayBitmap(bitmap: Bitmap) {
            binding.apply {
                pageImageView.setImageBitmap(bitmap)
                loadingProgressBar.visibility = android.view.View.GONE
            }
        }
        
        private fun evictOldestPages() {
            // Simple eviction: remove pages that are furthest from current position
            val currentPos = layoutPosition
            val sortedPages = pageCache.keys.sortedBy { kotlin.math.abs(it - currentPos) }
            
            // Remove the furthest pages
            sortedPages.takeLast(pageCache.size - maxCacheSize).forEach {
                pageCache.remove(it)?.recycle()
            }
        }
    }
}

/**
 * Item decoration to add spacing between PDF pages
 */
class PageSpacingDecoration(context: Context) : RecyclerView.ItemDecoration() {
    private val spacing = (8 * context.resources.displayMetrics.density).toInt()
    
    override fun getItemOffsets(
        outRect: android.graphics.Rect,
        view: android.view.View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        outRect.top = spacing
        outRect.bottom = spacing
        outRect.left = spacing
        outRect.right = spacing
    }
}
