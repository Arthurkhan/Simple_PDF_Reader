# Simple PDF Reader - Critical Improvements & Code Examples

## Priority Improvements for Immediate Implementation

### 1. Memory Management & Performance

#### Current Issue
The app loads PDFs at 2x resolution without proper memory management, causing potential OOM errors.

#### Improved Implementation

```kotlin
// utils/PdfMemoryManager.kt
class PdfMemoryManager(private val context: Context) {
    
    private val memoryCache = LruCache<Int, Bitmap>(calculateCacheSize())
    
    private fun calculateCacheSize(): Int {
        val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        return maxMemory / 8 // Use 1/8th of available memory for cache
    }
    
    fun getCachedPage(pageIndex: Int): Bitmap? = memoryCache.get(pageIndex)
    
    fun cachePage(pageIndex: Int, bitmap: Bitmap) {
        memoryCache.put(pageIndex, bitmap)
    }
    
    fun clearCache() {
        memoryCache.evictAll()
    }
    
    fun trimMemory(level: Int) {
        when (level) {
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> {
                memoryCache.trimToSize(memoryCache.size() / 2)
            }
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> {
                memoryCache.evictAll()
            }
        }
    }
}

// In MainActivity
override fun onTrimMemory(level: Int) {
    super.onTrimMemory(level)
    pdfMemoryManager.trimMemory(level)
}
```

### 2. Proper Error Handling with Sealed Classes

```kotlin
// domain/model/PdfResult.kt
sealed class PdfResult {
    data class Success(val pdfData: PdfData) : PdfResult()
    sealed class Error : PdfResult() {
        data class FileNotFound(val uri: Uri) : Error()
        data class PermissionDenied(val uri: Uri) : Error()
        data class CorruptedFile(val uri: Uri, val exception: Exception) : Error()
        data class OutOfMemory(val requiredMemory: Long) : Error()
        data object Unknown : Error()
    }
    data object Loading : PdfResult()
}

// Usage in ViewModel
class PdfViewModel : ViewModel() {
    private val _pdfState = MutableStateFlow<PdfResult>(PdfResult.Loading)
    val pdfState: StateFlow<PdfResult> = _pdfState.asStateFlow()
    
    fun loadPdf(uri: Uri) {
        viewModelScope.launch {
            _pdfState.value = PdfResult.Loading
            
            _pdfState.value = withContext(Dispatchers.IO) {
                try {
                    // Check permissions first
                    if (!hasUriPermission(uri)) {
                        return@withContext PdfResult.Error.PermissionDenied(uri)
                    }
                    
                    // Try to open PDF
                    val pdfData = pdfRepository.loadPdf(uri)
                    PdfResult.Success(pdfData)
                    
                } catch (e: FileNotFoundException) {
                    PdfResult.Error.FileNotFound(uri)
                } catch (e: OutOfMemoryError) {
                    val requiredMemory = estimateRequiredMemory(uri)
                    PdfResult.Error.OutOfMemory(requiredMemory)
                } catch (e: Exception) {
                    if (isPdfCorrupted(e)) {
                        PdfResult.Error.CorruptedFile(uri, e)
                    } else {
                        PdfResult.Error.Unknown
                    }
                }
            }
        }
    }
}
```

### 3. Secure Favorites Storage

```kotlin
// data/local/SecureFavoritesManager.kt
class SecureFavoritesManager(context: Context) {
    
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    
    private val securePrefs = EncryptedSharedPreferences.create(
        context,
        "secure_favorites",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    
    fun getFavorites(): List<FavoriteItem> {
        val json = securePrefs.getString(KEY_FAVORITES, null) ?: return emptyList()
        return try {
            Json.decodeFromString(json)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing favorites", e)
            emptyList()
        }
    }
    
    fun saveFavorites(favorites: List<FavoriteItem>) {
        val json = Json.encodeToString(favorites)
        securePrefs.edit {
            putString(KEY_FAVORITES, json)
        }
    }
    
    @Serializable
    data class FavoriteItem(
        val uri: String,
        val displayName: String,
        val addedDate: Long,
        val lastOpenedDate: Long? = null,
        val lastPage: Int = 0
    )
}
```

### 4. Improved Lock Mode with PIN

```kotlin
// utils/EnhancedLockManager.kt
class EnhancedLockManager(private val context: Context) {
    
    private val securePrefs = // ... encrypted preferences
    
    fun setupPin(pin: String) {
        val salt = generateSalt()
        val hashedPin = hashPin(pin, salt)
        
        securePrefs.edit {
            putString(KEY_PIN_HASH, hashedPin)
            putString(KEY_PIN_SALT, salt)
        }
    }
    
    fun verifyPin(inputPin: String): Boolean {
        val storedHash = securePrefs.getString(KEY_PIN_HASH, null) ?: return false
        val salt = securePrefs.getString(KEY_PIN_SALT, null) ?: return false
        
        val inputHash = hashPin(inputPin, salt)
        return inputHash == storedHash
    }
    
    private fun hashPin(pin: String, salt: String): String {
        val keySpec = PBEKeySpec(pin.toCharArray(), salt.toByteArray(), 10000, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hash = factory.generateSecret(keySpec).encoded
        return Base64.encodeToString(hash, Base64.NO_WRAP)
    }
    
    private fun generateSalt(): String {
        val random = SecureRandom()
        val salt = ByteArray(32)
        random.nextBytes(salt)
        return Base64.encodeToString(salt, Base64.NO_WRAP)
    }
}
```

### 5. Accessibility Improvements

```kotlin
// In MainActivity
private fun setupAccessibility() {
    // Page navigation accessibility
    binding.pdfView.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
    
    // Announce page changes
    binding.pdfView.setOnPageChangeListener { page, pageCount ->
        val announcement = getString(R.string.page_announcement, page + 1, pageCount)
        binding.pdfView.announceForAccessibility(announcement)
    }
    
    // Favorite button accessibility
    binding.btnAddFavorite.contentDescription = if (isFavorite) {
        getString(R.string.remove_from_favorites)
    } else {
        getString(R.string.add_to_favorites)
    }
    
    // Lock mode accessibility
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        binding.root.isScreenReaderFocusable = !isLocked
    }
}

// Custom PDF View with better accessibility
class AccessiblePdfView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : PDFView(context, attrs) {
    
    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        
        info.className = "PDF Document"
        info.isScrollable = true
        
        // Add custom actions
        info.addAction(AccessibilityNodeInfo.AccessibilityAction(
            R.id.action_next_page,
            context.getString(R.string.next_page)
        ))
        info.addAction(AccessibilityNodeInfo.AccessibilityAction(
            R.id.action_previous_page,
            context.getString(R.string.previous_page)
        ))
    }
    
    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
        return when (action) {
            R.id.action_next_page -> {
                jumpTo(currentPage + 1)
                true
            }
            R.id.action_previous_page -> {
                jumpTo(currentPage - 1)
                true
            }
            else -> super.performAccessibilityAction(action, arguments)
        }
    }
}
```

### 6. Performance Monitoring

```kotlin
// utils/PerformanceMonitor.kt
object PerformanceMonitor {
    
    private val metrics = mutableMapOf<String, Long>()
    
    inline fun <T> measureTime(label: String, block: () -> T): T {
        val start = SystemClock.elapsedRealtime()
        return try {
            block()
        } finally {
            val duration = SystemClock.elapsedRealtime() - start
            metrics[label] = duration
            Log.d("Performance", "$label took ${duration}ms")
        }
    }
    
    fun trackPdfLoad(uri: Uri, pageCount: Int, loadTime: Long) {
        Log.d("Performance", "PDF loaded: pages=$pageCount, time=${loadTime}ms")
        
        // Track slow loads
        if (loadTime > 2000) {
            Log.w("Performance", "Slow PDF load detected: ${uri.lastPathSegment}")
        }
    }
    
    fun getMetrics(): Map<String, Long> = metrics.toMap()
}

// Usage
PerformanceMonitor.measureTime("pdf_load") {
    pdfView.fromUri(uri).load()
}
```

### 7. Better State Management

```kotlin
// presentation/state/PdfViewState.kt
data class PdfViewState(
    val isLoading: Boolean = false,
    val currentPdf: PdfDocument? = null,
    val currentPage: Int = 0,
    val totalPages: Int = 0,
    val zoom: Float = 1.0f,
    val error: PdfError? = null,
    val isLocked: Boolean = false,
    val isFavorite: Boolean = false,
    val recentPages: List<Int> = emptyList()
) {
    val canNavigateBack: Boolean
        get() = currentPage > 0
        
    val canNavigateForward: Boolean
        get() = currentPage < totalPages - 1
        
    val progress: Float
        get() = if (totalPages > 0) (currentPage + 1).toFloat() / totalPages else 0f
}

// ViewModel with StateFlow
class PdfViewModel : ViewModel() {
    private val _state = MutableStateFlow(PdfViewState())
    val state: StateFlow<PdfViewState> = _state.asStateFlow()
    
    fun updatePage(newPage: Int) {
        _state.update { currentState ->
            currentState.copy(
                currentPage = newPage,
                recentPages = (currentState.recentPages + newPage).takeLast(10)
            )
        }
    }
}
```

### 8. Kotlin Coroutines Best Practices

```kotlin
// Use SupervisorScope for independent operations
class PdfRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    suspend fun preloadPages(uri: Uri, startPage: Int) = withContext(scope.coroutineContext) {
        // Preload pages independently
        val jobs = (-2..2).map { offset ->
            async {
                try {
                    loadPage(uri, startPage + offset)
                } catch (e: Exception) {
                    null // Don't crash if one page fails
                }
            }
        }
        jobs.awaitAll()
    }
    
    fun cleanup() {
        scope.cancel()
    }
}
```

### 9. Testing Examples

```kotlin
// Test ViewModel
@Test
fun `loading PDF updates state correctly`() = runTest {
    val viewModel = PdfViewModel()
    val testUri = Uri.parse("content://test.pdf")
    
    // Collect states
    val states = mutableListOf<PdfViewState>()
    val job = launch {
        viewModel.state.toList(states)
    }
    
    // Load PDF
    viewModel.loadPdf(testUri)
    
    // Verify states
    assertTrue(states[0].isLoading)
    assertFalse(states[1].isLoading)
    assertNotNull(states[1].currentPdf)
    
    job.cancel()
}
```

### 10. Migration Helper

```kotlin
// For safe migration from old favorites format
object FavoritesMigration {
    fun migrateIfNeeded(context: Context) {
        val oldPrefs = context.getSharedPreferences("favorites", Context.MODE_PRIVATE)
        val newManager = SecureFavoritesManager(context)
        
        if (oldPrefs.contains("favorites_list") && !hasMigrated(context)) {
            val oldFavorites = oldPrefs.getStringSet("favorites_list", emptySet())
            
            val newFavorites = oldFavorites?.mapNotNull { uriString ->
                try {
                    val uri = Uri.parse(uriString)
                    SecureFavoritesManager.FavoriteItem(
                        uri = uriString,
                        displayName = uri.lastPathSegment ?: "Unknown",
                        addedDate = System.currentTimeMillis()
                    )
                } catch (e: Exception) {
                    null
                }
            } ?: emptyList()
            
            newManager.saveFavorites(newFavorites)
            markMigrated(context)
            
            // Clear old data
            oldPrefs.edit().clear().apply()
        }
    }
}
```

These improvements focus on:
- Memory efficiency
- Error handling
- Security
- Accessibility
- Performance
- State management
- Testing

Each can be implemented incrementally without breaking existing functionality.