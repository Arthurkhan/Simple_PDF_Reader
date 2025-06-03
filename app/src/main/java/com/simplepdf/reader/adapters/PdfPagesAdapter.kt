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
            photoView.setImageBitmap(bitmap)
            
            // Set minimum zoom to fill width
            photoView.post {
                val screenWidth = photoView.width.toFloat()
                val bitmapWidth = bitmap.width.toFloat()
                val minScale = screenWidth / bitmapWidth
                
                photoView.minimumScale = minScale
                photoView.mediumScale = minScale * 1.5f
                photoView.maximumScale = minScale * 3f
                photoView.setScale(minScale, true)
            }
        }
    }
}
