package com.simplepdf.reader

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.github.barteksc.pdfviewer.link.LinkHandler
import com.github.barteksc.pdfviewer.listener.OnErrorListener
import com.github.barteksc.pdfviewer.listener.OnLoadCompleteListener
import com.github.barteksc.pdfviewer.listener.OnPageErrorListener
import com.github.barteksc.pdfviewer.model.LinkTapEvent
import com.simplepdf.reader.databinding.ActivityMainBinding
import com.simplepdf.reader.dialogs.FavoritesDialog
import com.simplepdf.reader.dialogs.TestPdfsDialog
import com.simplepdf.reader.utils.FavoritesManager
import com.simplepdf.reader.utils.LockScreenManager
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var favoritesManager: FavoritesManager
    private lateinit var lockScreenManager: LockScreenManager
    
    private var currentPdfUri: Uri? = null
    private var isAssetPdf = false
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
        
        // Set fullscreen and hide system UI
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        
        // Keep screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        setupUI()
        checkInitialIntent()
        
        // Enable immersive mode
        hideSystemUI()
    }
    
    private fun hideSystemUI() {
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                )
    }
    
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemUI()
        }
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
        
        binding.fabTestPdfs.setOnClickListener {
            showTestPdfs()
            toggleMenu()
        }
        
        binding.fabLock.setOnClickListener {
            lockScreenManager.enableLockMode()
            toggleMenu()
            updateLockUI(true)
            Toast.makeText(this, "Lock mode enabled. Double tap home to exit.", Toast.LENGTH_LONG).show()
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
        binding.fabTestPdfs.visibility = if (isVisible) View.GONE else View.VISIBLE
        binding.fabLock.visibility = if (isVisible) View.GONE else View.VISIBLE
        
        binding.fabMenu.animate().rotation(if (isVisible) 0f else 45f)
    }
    
    private fun updateLockUI(isLocked: Boolean) {
        binding.fabMenu.visibility = if (isLocked) View.GONE else View.VISIBLE
        binding.btnAddFavorite.visibility = if (isLocked || currentPdfUri == null || isAssetPdf) View.GONE else View.VISIBLE
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
    
    private fun showTestPdfs() {
        val dialog = TestPdfsDialog.newInstance()
        dialog.setOnPdfSelectedListener { assetPath ->
            loadPdfFromAssets(assetPath)
        }
        dialog.show(supportFragmentManager, "test_pdfs")
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
                isAssetPdf = false
                
                // Show loading progress
                binding.loadingProgress.visibility = View.VISIBLE
                binding.emptyView.visibility = View.GONE
                
                // Load PDF with interactive link support
                binding.pdfView.fromUri(uri)
                    .defaultPage(0)
                    .enableSwipe(true)
                    .swipeHorizontal(false)
                    .enableAnnotationRendering(true)
                    .enableAntialiasing(true)
                    .spacing(0)
                    .autoSpacing(true)
                    .pageSnap(false)
                    .pageFling(false)
                    .linkHandler(object : LinkHandler {
                        override fun handleLinkEvent(event: LinkTapEvent) {
                            val link = event.link
                            val uri = link.uri
                            
                            if (uri != null && uri.isNotEmpty()) {
                                try {
                                    // Handle external links
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
                                    if (intent.resolveActivity(packageManager) != null) {
                                        startActivity(intent)
                                    } else {
                                        Toast.makeText(this@MainActivity, "No app available to open this link", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(this@MainActivity, "Error opening link: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            } else if (link.destPageIdx != null) {
                                // Handle internal PDF page links
                                binding.pdfView.jumpTo(link.destPageIdx)
                            }
                        }
                    })
                    .onLoad(OnLoadCompleteListener {
                        // Hide loading and show PDF
                        binding.loadingProgress.visibility = View.GONE
                        binding.pdfView.visibility = View.VISIBLE
                        binding.fabMenu.visibility = View.GONE
                        binding.btnAddFavorite.visibility = View.GONE
                    })
                    .onError(OnErrorListener { throwable ->
                        binding.loadingProgress.visibility = View.GONE
                        binding.emptyView.visibility = View.VISIBLE
                        Toast.makeText(this@MainActivity, "Error loading PDF: ${throwable.message}", Toast.LENGTH_LONG).show()
                        throwable.printStackTrace()
                    })
                    .onPageError(OnPageErrorListener { page, throwable ->
                        Toast.makeText(this@MainActivity, "Error on page $page: ${throwable.message}", Toast.LENGTH_SHORT).show()
                    })
                    .load()
                
            } catch (e: Exception) {
                binding.loadingProgress.visibility = View.GONE
                binding.emptyView.visibility = View.VISIBLE
                Toast.makeText(this@MainActivity, "Error loading PDF: ${e.message}", Toast.LENGTH_LONG).show()
                e.printStackTrace()
            }
        }
    }
    
    private fun loadPdfFromAssets(assetPath: String) {
        lifecycleScope.launch {
            try {
                isAssetPdf = true
                currentPdfUri = null
                
                // Show loading progress
                binding.loadingProgress.visibility = View.VISIBLE
                binding.emptyView.visibility = View.GONE
                
                // Copy asset to temp file
                val tempFile = File(cacheDir, "temp_${assetPath.substringAfterLast('/')}")
                assets.open(assetPath).use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
                
                // Load PDF from temp file with interactive link support
                binding.pdfView.fromFile(tempFile)
                    .defaultPage(0)
                    .enableSwipe(true)
                    .swipeHorizontal(false)
                    .enableAnnotationRendering(true)
                    .enableAntialiasing(true)
                    .spacing(0)
                    .autoSpacing(true)
                    .pageSnap(false)
                    .pageFling(false)
                    .linkHandler(object : LinkHandler {
                        override fun handleLinkEvent(event: LinkTapEvent) {
                            val link = event.link
                            val uri = link.uri
                            
                            if (uri != null && uri.isNotEmpty()) {
                                try {
                                    // Handle external links
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
                                    if (intent.resolveActivity(packageManager) != null) {
                                        startActivity(intent)
                                    } else {
                                        Toast.makeText(this@MainActivity, "No app available to open this link", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(this@MainActivity, "Error opening link: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            } else if (link.destPageIdx != null) {
                                // Handle internal PDF page links
                                binding.pdfView.jumpTo(link.destPageIdx)
                            }
                        }
                    })
                    .onLoad(OnLoadCompleteListener {
                        // Hide loading and show PDF
                        binding.loadingProgress.visibility = View.GONE
                        binding.pdfView.visibility = View.VISIBLE
                        binding.fabMenu.visibility = View.GONE
                        binding.btnAddFavorite.visibility = View.GONE
                        Toast.makeText(this@MainActivity, "Loaded test PDF: ${assetPath.substringAfterLast('/')}", Toast.LENGTH_SHORT).show()
                    })
                    .onError(OnErrorListener { throwable ->
                        binding.loadingProgress.visibility = View.GONE
                        binding.emptyView.visibility = View.VISIBLE
                        Toast.makeText(this@MainActivity, "Error loading test PDF: ${throwable.message}", Toast.LENGTH_LONG).show()
                        throwable.printStackTrace()
                    })
                    .load()
                
            } catch (e: Exception) {
                binding.loadingProgress.visibility = View.GONE
                binding.emptyView.visibility = View.VISIBLE
                Toast.makeText(this@MainActivity, "Error loading test PDF: ${e.message}", Toast.LENGTH_LONG).show()
                e.printStackTrace()
            }
        }
    }
    
    override fun onBackPressed() {
        if (lockScreenManager.isLocked()) {
            // In lock mode, do nothing
            return
        }
        
        // If PDF is loaded, close it and show the menu
        if (binding.pdfView.visibility == View.VISIBLE) {
            closePdf()
        } else {
            super.onBackPressed()
        }
    }
    
    private fun closePdf() {
        // Reset PDF view
        binding.pdfView.recycle()
        
        // Reset UI
        binding.pdfView.visibility = View.GONE
        binding.emptyView.visibility = View.VISIBLE
        binding.fabMenu.visibility = View.VISIBLE
        binding.btnAddFavorite.visibility = View.GONE
        
        // Reset variables
        currentPdfUri = null
        isAssetPdf = false
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
        binding.pdfView.recycle()
        
        // Clean up temp files
        cacheDir.listFiles()?.forEach { file ->
            if (file.name.startsWith("temp_") && file.name.endsWith(".pdf")) {
                file.delete()
            }
        }
    }
}
