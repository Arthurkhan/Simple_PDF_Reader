package com.simplepdf.reader

import android.app.Application
import com.simplepdf.reader.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

/**
 * Application class for initializing Koin dependency injection
 */
class SimplePdfReaderApp : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize Koin
        startKoin {
            // Log Koin into Android logger
            androidLogger(Level.ERROR)
            // Reference Android context
            androidContext(this@SimplePdfReaderApp)
            // Load modules
            modules(appModule)
        }
    }
}
