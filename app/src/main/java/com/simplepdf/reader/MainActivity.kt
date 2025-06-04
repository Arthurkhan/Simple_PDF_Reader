package com.simplepdf.reader

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowInsetsController
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.github.barteksc.pdfviewer.listener.OnErrorListener
import com.github.barteksc.pdfviewer.listener.OnLoadCompleteListener
import com.github.barteksc.pdfviewer.listener.OnPageErrorListener
import com.simplepdf.reader.databinding.ActivityMainBinding
import com.simplepdf.reader.dialogs.FavoritesDialog
import com.simplepdf.reader.dialogs.TestPdfsDialog
import com.simplepdf.reader.utils.FavoritesManager
import com.simplepdf.reader.utils.LockScreenManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {
    
    // Using lazy initialization for better performance
    private val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }
    private val favoritesManager by lazy { FavoritesManager(this) }
    private val lockScreenManager by lazy { LockScreenManager(this) }
    
    private var currentPdfUri: Uri? = null
    private var isAssetPdf = false
    private var lastHomePress = 0L
    
    companion object {
        private const val TAG = "PDFReader"
        private const val DOUBLE_TAP_DELAY = 500L
    }
    
    // Modern Activity Result API
    private val pdfPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        Log.d(TAG, "PDF picker result: $uri")
        uri?.let {
            try {
                // Take persistable permission
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                contentResolver.takePersistableUriPermission(it, takeFlags)
                Log.d(TAG, "Took persistable permission for URI: $it")
                loadPdf(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error taking persistable permission", e)
                Toast.makeText(this, "Error accessing file: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")
        
        setContentView(binding.root)
        
        // Modern fullscreen approach
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        // Keep screen on
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        // Add lifecycle observer for better UI management
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                hideSystemUI()
            }
            
            override fun onDestroy(owner: LifecycleOwner) {
                // Clean up resources
                binding.pdfView.recycle()
                cleanupTempFiles()
            }
        })
        
        setupUI()
        setupBackPressHandler()
        checkInitialIntent()
        
        // Enable immersive mode
        hideSystemUI()
        
        // Update UI state
        updateUIForNoContent()
    }
    
    private fun updateUIForNoContent() {
        binding.emptyView.visibility = View.VISIBLE
        binding.pdfView.visibility = View.GONE
        binding.fabMenu.visibility = View.VISIBLE
        binding.btnAddFavorite.visibility = View.GONE
    }
    
    private fun updateUIForContent() {
        binding.emptyView.visibility = View.GONE
        binding.pdfView.visibility = View.VISIBLE
        
        // Update favorite button
        currentPdfUri?.let { uri ->
            if (!isAssetPdf) {
                binding.btnAddFavorite.visibility = View.VISIBLE
                binding.btnAddFavorite.setImageResource(
                    if (favoritesManager.isFavorite(uri)) R.drawable.ic_star 
                    else R.drawable.ic_star_border
                )
            }
        }
    }
    
    private fun hideSystemUI() {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.apply {
            // Hide system bars
            hide(WindowInsetsCompat.Type.systemBars())
            // Set behavior for system bars
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
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
    
    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (lockScreenManager.isLocked()) {
                    // In lock mode, do nothing
                    return
                }
                
                // If PDF is loaded, close it and show the menu
                if (binding.pdfView.visibility == View.VISIBLE) {
                    closePdf()
                } else {
                    // If no PDF is loaded, finish the activity
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
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
            Log.d(TAG, "Initial intent URI: $uri")
            loadPdf(uri)
        }
    }
    
    private fun openFilePicker() {
        Log.d(TAG, "Opening file picker")
        pdfPickerLauncher.launch(arrayOf("application/pdf"))
    }
    
    private fun showFavorites() {
        val dialog = FavoritesDialog.newInstance()
        dialog.setOnFavoriteSelectedListener { uri ->
            Log.d(TAG, "Selected favorite: $uri")
            loadPdf(uri)
        }
        dialog.show(supportFragmentManager, "favorites")
    }
    
    private fun showTestPdfs() {
        val dialog = TestPdfsDialog.newInstance()
        dialog.setOnPdfSelectedListener { assetPath ->
            Log.d(TAG, "Selected test PDF: $assetPath")
            loadPdfFromAssets(assetPath)
        }
        dialog.show(supportFragmentManager, "test_pdfs")
    }
    
    private fun loadPdf(uri: Uri) {
        Log.d(TAG, "Loading PDF from URI: $uri")
        
        // Use repeatOnLifecycle for better lifecycle handling
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                try {
                    currentPdfUri = uri
                    isAssetPdf = false
                    
                    // Show loading progress
                    withContext(Dispatchers.Main) {
                        binding.loadingProgress.visibility = View.VISIBLE
                        binding.emptyView.visibility = View.GONE
                    }
                    
                    Log.d(TAG, "Starting PDF load...")
                    
                    // Simplified PDF loading configuration
                    withContext(Dispatchers.Main) {
                        binding.pdfView.fromUri(uri)
                            .defaultPage(0)
                            .enableSwipe(true)
                            .swipeHorizontal(false)
                            .enableAnnotationRendering(false) // Simplified - disable annotations
                            .spacing(0)
                            .onLoad(OnLoadCompleteListener { nbPages ->
                                Log.d(TAG, "PDF loaded successfully. Pages: $nbPages")
                                // Hide loading and show PDF
                                binding.loadingProgress.visibility = View.GONE
                                updateUIForContent()
                                Toast.makeText(this@MainActivity, "PDF loaded successfully ($nbPages pages)", Toast.LENGTH_SHORT).show()
                            })
                            .onError(OnErrorListener { throwable ->
                                Log.e(TAG, "Error loading PDF: ${throwable.message}", throwable)
                                binding.loadingProgress.visibility = View.GONE
                                updateUIForNoContent()
                                
                                val errorMsg = when {
                                    throwable.message?.contains("Permission", ignoreCase = true) == true -> 
                                        "Permission denied. Please grant storage permission."
                                    throwable.message?.contains("FileNotFound", ignoreCase = true) == true || 
                                    throwable.message?.contains("No such file", ignoreCase = true) == true -> 
                                        "PDF file not found. The file may have been moved or deleted."
                                    throwable.message?.contains("IOException", ignoreCase = true) == true -> 
                                        "Error reading PDF file. The file may be corrupted."
                                    else -> 
                                        "Error loading PDF: ${throwable.javaClass.simpleName} - ${throwable.message}"
                                }
                                
                                Toast.makeText(this@MainActivity, errorMsg, Toast.LENGTH_LONG).show()
                            })
                            .onPageError(OnPageErrorListener { page, throwable ->
                                Log.e(TAG, "Error on page $page: ${throwable.message}", throwable)
                            })
                            .load()
                    }
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Exception in loadPdf: ${e.message}", e)
                    withContext(Dispatchers.Main) {
                        binding.loadingProgress.visibility = View.GONE
                        updateUIForNoContent()
                        Toast.makeText(this@MainActivity, "Error loading PDF: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }
    
    private fun loadPdfFromAssets(assetPath: String) {
        Log.d(TAG, "Loading PDF from assets: $assetPath")
        
        lifecycleScope.launch {
            try {
                isAssetPdf = true
                currentPdfUri = null
                
                // Show loading progress
                withContext(Dispatchers.Main) {
                    binding.loadingProgress.visibility = View.VISIBLE
                    binding.emptyView.visibility = View.GONE
                }
                
                // Copy asset to temp file in IO context
                val tempFile = withContext(Dispatchers.IO) {
                    val file = File(cacheDir, "temp_${assetPath.substringAfterLast('/')}")
                    Log.d(TAG, "Copying asset to temp file: ${file.absolutePath}")
                    
                    assets.open(assetPath).use { input ->
                        FileOutputStream(file).use { output ->
                            input.copyTo(output)
                        }
                    }
                    file
                }
                
                Log.d(TAG, "Asset copied, loading PDF from: ${tempFile.absolutePath}")
                
                // Simplified PDF loading from file
                withContext(Dispatchers.Main) {
                    binding.pdfView.fromFile(tempFile)
                        .defaultPage(0)
                        .enableSwipe(true)
                        .swipeHorizontal(false)
                        .enableAnnotationRendering(false) // Simplified
                        .spacing(0)
                        .onLoad(OnLoadCompleteListener { nbPages ->
                            Log.d(TAG, "Test PDF loaded successfully. Pages: $nbPages")
                            binding.loadingProgress.visibility = View.GONE
                            updateUIForContent()
                            Toast.makeText(this@MainActivity, "Loaded: ${assetPath.substringAfterLast('/')} ($nbPages pages)", Toast.LENGTH_SHORT).show()
                        })
                        .onError(OnErrorListener { throwable ->
                            Log.e(TAG, "Error loading test PDF: ${throwable.message}", throwable)
                            binding.loadingProgress.visibility = View.GONE
                            updateUIForNoContent()
                            Toast.makeText(this@MainActivity, "Error: ${throwable.message}", Toast.LENGTH_LONG).show()
                        })
                        .load()
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Exception in loadPdfFromAssets: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    binding.loadingProgress.visibility = View.GONE
                    updateUIForNoContent()
                    Toast.makeText(this@MainActivity, "Error loading test PDF: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    private fun closePdf() {
        Log.d(TAG, "Closing PDF")
        
        // Reset PDF view
        binding.pdfView.recycle()
        
        // Reset UI
        updateUIForNoContent()
        
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
    
    private fun cleanupTempFiles() {
        cacheDir.listFiles()?.forEach { file ->
            if (file.name.startsWith("temp_") && file.name.endsWith(".pdf")) {
                file.delete()
            }
        }
    }
}
