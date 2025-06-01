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
import com.simplepdf.reader.databinding.ActivityMainBinding
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
        }
        
        binding.btnPrevious.setOnClickListener {
            showPage(currentPageIndex - 1)
        }
        
        binding.btnNext.setOnClickListener {
            showPage(currentPageIndex + 1)
        }
        
        binding.btnAddFavorite.setOnClickListener {
            currentPdfUri?.let { uri ->
                favoritesManager.addFavorite(uri)
                Toast.makeText(this, "Added to favorites", Toast.LENGTH_SHORT).show()
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
        binding.btnAddFavorite.visibility = if (isLocked) View.GONE else View.VISIBLE
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
        }
        startActivityForResult(intent, PICK_PDF_FILE)
    }
    
    private fun showFavorites() {
        val favorites = favoritesManager.getFavorites()
        if (favorites.isEmpty()) {
            Toast.makeText(this, "No favorites yet", Toast.LENGTH_SHORT).show()
            return
        }
        
        // TODO: Show favorites dialog
        // For now, load the first favorite
        favorites.firstOrNull()?.let { uri ->
            loadPdf(Uri.parse(uri))
        }
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == PICK_PDF_FILE && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
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
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Error loading PDF: ${e.message}", Toast.LENGTH_LONG).show()
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
                        page.width,
                        page.height,
                        android.graphics.Bitmap.Config.ARGB_8888
                    )
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    
                    withContext(Dispatchers.Main) {
                        binding.pdfImageView.setImageBitmap(bitmap)
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
