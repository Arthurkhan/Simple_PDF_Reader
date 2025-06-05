package com.simplepdf.reader

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.github.barteksc.pdfviewer.listener.OnErrorListener
import com.github.barteksc.pdfviewer.listener.OnLoadCompleteListener
import com.github.barteksc.pdfviewer.listener.OnPageErrorListener
import com.simplepdf.reader.databinding.ActivityMainBinding
import com.simplepdf.reader.dialogs.FavoritesDialog
import com.simplepdf.reader.dialogs.TestPdfsDialog
import com.simplepdf.reader.domain.model.PdfViewerEvent
import com.simplepdf.reader.domain.model.PdfViewerState
import com.simplepdf.reader.presentation.viewmodel.MainViewModel
import com.simplepdf.reader.utils.LockScreenManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var lockScreenManager: LockScreenManager
    
    // Inject ViewModel using Koin
    private val viewModel: MainViewModel by viewModel()
    
    private var lastHomePress = 0L
    private var isAssetPdf = false
    
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
                viewModel.loadPdf(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error taking persistable permission", e)
                Toast.makeText(this, "Error accessing file: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Initialize managers
        lockScreenManager = LockScreenManager(this)
        
        // Modern fullscreen approach
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        // Keep screen on
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        setupUI()
        setupBackPressHandler()
        observeViewModel()
        checkInitialIntent()
        
        // Enable immersive mode
        hideSystemUI()
    }
    
    private fun observeViewModel() {
        // Observe UI state
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    handleUiState(state)
                }
            }
        }
        
        // Observe events
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    handleEvent(event)
                }
            }
        }
        
        // Observe favorites
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.favorites.collect { favorites ->
                    // Update UI if needed based on favorites
                }
            }
        }
    }
    
    private fun handleUiState(state: PdfViewerState) {
        when (state) {
            is PdfViewerState.Idle -> {
                updateUIForNoContent()
            }
            is PdfViewerState.Loading -> {
                binding.loadingProgress.visibility = View.VISIBLE
                binding.emptyView.visibility = View.GONE
            }
            is PdfViewerState.Loaded -> {
                binding.loadingProgress.visibility = View.GONE
                updateUIForContent(state)
                loadPdfIntoView(state.document.uri)
                updateLockUI(state.isLocked)
                if (state.isImmersiveMode) {
                    hideSystemUI()
                }
            }
            is PdfViewerState.Error -> {
                binding.loadingProgress.visibility = View.GONE
                updateUIForNoContent()
                Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun handleEvent(event: PdfViewerEvent) {
        when (event) {
            is PdfViewerEvent.OpenFilePicker -> {
                openFilePicker()
            }
            is PdfViewerEvent.ShowFavorites -> {
                showFavorites()
            }
            is PdfViewerEvent.ToggleLock -> {
                val state = viewModel.uiState.value
                if (state is PdfViewerState.Loaded && state.isLocked) {
                    lockScreenManager.enableLockMode()
                    Toast.makeText(this, "Lock mode enabled. Double tap home to exit.", Toast.LENGTH_LONG).show()
                } else {
                    lockScreenManager.disableLockMode()
                }
            }
            is PdfViewerEvent.ToggleImmersiveMode -> {
                hideSystemUI()
            }
            is PdfViewerEvent.ShowError -> {
                Toast.makeText(this, event.message, Toast.LENGTH_LONG).show()
            }
            is PdfViewerEvent.NavigateToPage -> {
                binding.pdfView.jumpTo(event.page)
            }
        }
    }
    
    private fun updateUIForNoContent() {
        binding.emptyView.visibility = View.VISIBLE
        binding.pdfView.visibility = View.GONE
        binding.fabMenu.visibility = View.VISIBLE
        binding.btnAddFavorite.visibility = View.GONE
    }
    
    private fun updateUIForContent(state: PdfViewerState.Loaded) {
        binding.emptyView.visibility = View.GONE
        binding.pdfView.visibility = View.VISIBLE
        
        // Update favorite button
        if (!isAssetPdf) {
            binding.btnAddFavorite.visibility = View.VISIBLE
            binding.btnAddFavorite.setImageResource(
                if (state.document.isFavorite) R.drawable.ic_star 
                else R.drawable.ic_star_border
            )
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
            val state = viewModel.uiState.value
            if (state is PdfViewerState.Loaded && state.isImmersiveMode) {
                hideSystemUI()
            }
        }
    }
    
    private fun setupUI() {
        binding.fabMenu.setOnClickListener {
            toggleMenu()
        }
        
        binding.fabOpenFile.setOnClickListener {
            viewModel.openFilePicker()
            toggleMenu()
        }
        
        binding.fabFavorites.setOnClickListener {
            viewModel.showFavorites()
            toggleMenu()
        }
        
        binding.fabTestPdfs.setOnClickListener {
            showTestPdfs()
            toggleMenu()
        }
        
        binding.fabLock.setOnClickListener {
            viewModel.toggleLockMode()
            toggleMenu()
        }
        
        binding.btnAddFavorite.setOnClickListener {
            viewModel.toggleFavorite()
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
        val state = viewModel.uiState.value
        binding.btnAddFavorite.visibility = when {
            isLocked -> View.GONE
            isAssetPdf -> View.GONE
            state is PdfViewerState.Loaded -> View.VISIBLE
            else -> View.GONE
        }
    }
    
    private fun checkInitialIntent() {
        intent?.data?.let { uri ->
            Log.d(TAG, "Initial intent URI: $uri")
            viewModel.loadPdf(uri)
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
            viewModel.loadPdf(uri)
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
    
    private fun loadPdfIntoView(uri: Uri) {
        isAssetPdf = false
        
        lifecycleScope.launch {
            try {
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
                            Toast.makeText(this@MainActivity, "PDF loaded successfully ($nbPages pages)", Toast.LENGTH_SHORT).show()
                        })
                        .onError(OnErrorListener { throwable ->
                            Log.e(TAG, "Error loading PDF: ${throwable.message}", throwable)
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
                            viewModel.clearError()
                        })
                        .onPageError(OnPageErrorListener { page, throwable ->
                            Log.e(TAG, "Error on page $page: ${throwable.message}", throwable)
                        })
                        .load()
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Exception in loadPdfIntoView: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Error loading PDF: ${e.message}", Toast.LENGTH_LONG).show()
                    viewModel.clearError()
                }
            }
        }
    }
    
    private fun loadPdfFromAssets(assetPath: String) {
        Log.d(TAG, "Loading PDF from assets: $assetPath")
        
        lifecycleScope.launch {
            try {
                isAssetPdf = true
                
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
                            binding.emptyView.visibility = View.GONE
                            binding.pdfView.visibility = View.VISIBLE
                            binding.btnAddFavorite.visibility = View.GONE // Hide for asset PDFs
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
        
        // Reset view model state
        viewModel.clearError()
        
        // Reset variables
        isAssetPdf = false
    }
    
    override fun onUserLeaveHint() {
        if (lockScreenManager.isLocked()) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastHomePress < DOUBLE_TAP_DELAY) {
                // Double tap detected, unlock
                viewModel.toggleLockMode()
                Toast.makeText(this, "Lock mode disabled", Toast.LENGTH_SHORT).show()
            } else {
                lastHomePress = currentTime
                Toast.makeText(this, "Press home again to unlock", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy")
        binding.pdfView.recycle()
        
        // Clean up temp files
        cacheDir.listFiles()?.forEach { file ->
            if (file.name.startsWith("temp_") && file.name.endsWith(".pdf")) {
                file.delete()
            }
        }
    }
}
