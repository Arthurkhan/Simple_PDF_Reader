package com.simplepdf.reader

import android.app.Activity
import android.content.Intent
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.github.chrisbanes.photoview.PhotoView
import com.simplepdf.reader.databinding.ActivityMainBinding
import com.simplepdf.reader.dialogs.FavoritesDialog
import com.simplepdf.reader.utils.FavoritesManager
import com.simplepdf.reader.utils.LockScreenManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var favoritesManager: FavoritesManager
    private lateinit var lockScreenManager: LockScreenManager
    
    private var pdfRenderer: PdfRenderer? = null
    private var currentPage: PdfRenderer.Page? = null
    private var currentPageIndex = 0
    private var pageCount = 0
    private var currentPdfUri: Uri? = null
    private var lastHomePress = 0L
    
    companion object {
        private const val PICK_PDF_FILE = 1001
        private const val DOUBLE_TAP_DELAY = 500L
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Initialize managers
        favoritesManager = FavoritesManager(this)
        lockScreenManager = LockScreenManager(this)
        
        // Set fullscreen
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        
        // Keep screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        setupUI()
        checkInitialIntent()
    }
    
    private fun setupUI() {
        binding.fabMenu.setOnClickListener {
            toggleMenu()
        }
        
        binding.fabOpenFile.setOnClickListener {
            openFilePicker()
            toggleMenu()
        }
        
        binding.fabFavorites.setOnClickListener {
            showFavorites()
            toggleMenu()
        }
        
        binding.fabLock.setOnClickListener {
            lockScreenManager.enableLockMode()
            toggleMenu()
            updateLockUI(true)
            Toast.makeText(this, "Lock mode enabled. Double tap home to exit.", Toast.LENGTH_LONG).show()
        }
        
        binding.btnPrevious.setOnClickListener {
            showPage(currentPageIndex - 1)
        }
        
        binding.btnNext.setOnClickListener {
            showPage(currentPageIndex + 1)
        }
        
        binding.btnAddFavorite.setOnClickListener {
            currentPdfUri?.let { uri ->
                if (favoritesManager.isFavorite(uri)) {
                    favoritesManager.removeFavorite(uri)
                    binding.btnAddFavorite.setImageResource(R.drawable.ic_star_border)
                    Toast.makeText(this, "Removed from favorites", Toast.LENGTH_SHORT).show()
                } else {
                    favoritesManager.addFavorite(uri)
                    binding.btnAddFavorite.setImageResource(R.drawable.ic_star)
                    Toast.makeText(this, "Added to favorites", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun toggleMenu() {
        val isVisible = binding.fabOpenFile.visibility == View.VISIBLE
        
        binding.fabOpenFile.visibility = if (isVisible) View.GONE else View.VISIBLE
        binding.fabFavorites.visibility = if (isVisible) View.GONE else View.VISIBLE
        binding.fabLock.visibility = if (isVisible) View.GONE else View.VISIBLE
        
        binding.fabMenu.animate().rotation(if (isVisible) 0f else 45f)
    }
    
    private fun updateLockUI(isLocked: Boolean) {
        binding.fabMenu.visibility = if (isLocked) View.GONE else View.VISIBLE
        binding.btnAddFavorite.visibility = if (isLocked || currentPdfUri == null) View.GONE else View.VISIBLE
    }
    
    private fun checkInitialIntent() {
        intent?.data?.let { uri ->
            loadPdf(uri)
        }
    }
    
    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/pdf"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, PICK_PDF_FILE)
    }
    
    private fun showFavorites() {
        val dialog = FavoritesDialog.newInstance()
        dialog.setOnFavoriteSelectedListener { uri ->
            loadPdf(uri)
        }
        dialog.show(supportFragmentManager, "favorites")
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == PICK_PDF_FILE && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                // Take persistable permission
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                contentResolver.takePersistableUriPermission(uri, takeFlags)
                loadPdf(uri)
            }
        }
    }
    
    private fun loadPdf(uri: Uri) {
        lifecycleScope.launch {
            try {
                currentPdfUri = uri
                openPdfRenderer(uri)
                showPage(0)
                binding.pdfContainer.visibility = View.VISIBLE
                binding.emptyView.visibility = View.GONE
                binding.btnAddFavorite.visibility = View.VISIBLE
                
                // Update favorite icon
                if (favoritesManager.isFavorite(uri)) {
                    binding.btnAddFavorite.setImageResource(R.drawable.ic_star)
                } else {
                    binding.btnAddFavorite.setImageResource(R.drawable.ic_star_border)
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Error loading PDF: ${e.message}", Toast.LENGTH_LONG).show()
                e.printStackTrace()
            }
        }
    }
    
    private suspend fun openPdfRenderer(uri: Uri) = withContext(Dispatchers.IO) {
        pdfRenderer?.close()
        currentPage?.close()
        
        val descriptor = contentResolver.openFileDescriptor(uri, "r")
        descriptor?.let {
            pdfRenderer = PdfRenderer(it)
            pageCount = pdfRenderer?.pageCount ?: 0
        }
    }
    
    private fun showPage(index: Int) {
        if (index < 0 || index >= pageCount) return
        
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                currentPage?.close()
                currentPage = pdfRenderer?.openPage(index)
                
                currentPage?.let { page ->
                    val bitmap = android.graphics.Bitmap.createBitmap(
                        page.width * 2,  // Higher resolution
                        page.height * 2,
                        android.graphics.Bitmap.Config.ARGB_8888
                    )
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    
                    withContext(Dispatchers.Main) {
                        val photoView = binding.pdfImageView as PhotoView
                        photoView.setImageBitmap(bitmap)
                        
                        // Calculate minimum zoom to fill screen
                        photoView.post {
                            val screenWidth = photoView.width.toFloat()
                            val screenHeight = photoView.height.toFloat()
                            val bitmapWidth = bitmap.width.toFloat()
                            val bitmapHeight = bitmap.height.toFloat()
                            
                            // Calculate scale factors for width and height
                            val scaleX = screenWidth / bitmapWidth
                            val scaleY = screenHeight / bitmapHeight
                            
                            // Use the larger scale to ensure the PDF fills the entire screen
                            val minScale = maxOf(scaleX, scaleY)
                            
                            // Set minimum, medium, and maximum scale
                            photoView.minimumScale = minScale
                            photoView.mediumScale = minScale * 1.5f
                            photoView.maximumScale = minScale * 3f
                            
                            // Set initial scale to fill screen
                            photoView.setScale(minScale, true)
                        }
                        
                        currentPageIndex = index
                        updateNavigationButtons()
                        binding.pageInfo.text = "Page ${index + 1} of $pageCount"
                    }
                }
            }
        }
    }
    
    private fun updateNavigationButtons() {
        binding.btnPrevious.isEnabled = currentPageIndex > 0
        binding.btnNext.isEnabled = currentPageIndex < pageCount - 1
        
        // Hide navigation for single page PDFs
        val showNavigation = pageCount > 1
        binding.btnPrevious.visibility = if (showNavigation) View.VISIBLE else View.INVISIBLE
        binding.btnNext.visibility = if (showNavigation) View.VISIBLE else View.INVISIBLE
    }
    
    override fun onBackPressed() {
        if (lockScreenManager.isLocked()) {
            // In lock mode, do nothing
            return
        }
        super.onBackPressed()
    }
    
    override fun onUserLeaveHint() {
        if (lockScreenManager.isLocked()) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastHomePress < DOUBLE_TAP_DELAY) {
                // Double tap detected, unlock
                lockScreenManager.disableLockMode()
                updateLockUI(false)
                Toast.makeText(this, "Lock mode disabled", Toast.LENGTH_SHORT).show()
            } else {
                lastHomePress = currentTime
                Toast.makeText(this, "Press home again to unlock", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        currentPage?.close()
        pdfRenderer?.close()
    }
}
