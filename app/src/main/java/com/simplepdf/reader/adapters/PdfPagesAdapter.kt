package com.simplepdf.reader.adapters

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.github.chrisbanes.photoview.PhotoView
import com.simplepdf.reader.databinding.ItemPdfPageBinding

class PdfPagesAdapter : RecyclerView.Adapter<PdfPagesAdapter.PageViewHolder>() {
    
    private val pages = mutableListOf<Bitmap>()
    
    fun setPages(newPages: List<Bitmap>) {
        pages.clear()
        pages.addAll(newPages)
        notifyDataSetChanged()
    }
    
    fun addPage(bitmap: Bitmap) {
        pages.add(bitmap)
        notifyItemInserted(pages.size - 1)
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val binding = ItemPdfPageBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        // Remove any default item spacing
        binding.root.layoutParams = RecyclerView.LayoutParams(
            RecyclerView.LayoutParams.MATCH_PARENT,
            RecyclerView.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(0, 0, 0, 0)
        }
        return PageViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        holder.bind(pages[position])
    }
    
    override fun getItemCount(): Int = pages.size
    
    fun clear() {
        pages.forEach { it.recycle() }
        pages.clear()
        notifyDataSetChanged()
    }
    
    inner class PageViewHolder(private val binding: ItemPdfPageBinding) : 
        RecyclerView.ViewHolder(binding.root) {
        
        fun bind(bitmap: Bitmap) {
            val photoView = binding.pageImageView as PhotoView
            
            // First set the bitmap
            photoView.setImageBitmap(bitmap)
            
            // Calculate proper zoom levels after the view is laid out
            photoView.post {
                // Get the actual displayed dimensions
                val displayWidth = photoView.width.toFloat()
                val displayHeight = photoView.height.toFloat()
                
                // Get the drawable's intrinsic dimensions (which match bitmap size)
                val intrinsicWidth = photoView.drawable?.intrinsicWidth?.toFloat() ?: bitmap.width.toFloat()
                val intrinsicHeight = photoView.drawable?.intrinsicHeight?.toFloat() ?: bitmap.height.toFloat()
                
                // Calculate the scale that PhotoView uses internally for fitCenter
                val widthScale = displayWidth / intrinsicWidth
                val heightScale = displayHeight / intrinsicHeight
                val baseScale = minOf(widthScale, heightScale)
                
                // Set zoom levels relative to PhotoView's internal scale
                // For fit-to-width, we want the scale that makes width match exactly
                val fitWidthScale = widthScale / baseScale
                
                photoView.minimumScale = fitWidthScale
                photoView.mediumScale = fitWidthScale * 1.5f
                photoView.maximumScale = fitWidthScale * 3f
                
                // Set initial scale to fit width
                photoView.setScale(fitWidthScale, false)
            }
        }
    }
}
