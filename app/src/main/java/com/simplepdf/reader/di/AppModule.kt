package com.simplepdf.reader.di

import com.simplepdf.reader.data.repository.PdfRepositoryImpl
import com.simplepdf.reader.domain.repository.PdfRepository
import com.simplepdf.reader.domain.usecase.LoadPdfUseCase
import com.simplepdf.reader.domain.usecase.ManageFavoritesUseCase
import com.simplepdf.reader.presentation.viewmodel.MainViewModel
import com.simplepdf.reader.utils.FavoritesManager
import com.simplepdf.reader.utils.LockScreenManager
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin module definitions for dependency injection
 */
val appModule = module {
    // Utils
    single { FavoritesManager(androidContext()) }
    single { LockScreenManager(androidContext()) }
    
    // Repository
    single<PdfRepository> { PdfRepositoryImpl(androidContext(), get()) }
    
    // Use Cases
    factory { LoadPdfUseCase(get()) }
    factory { ManageFavoritesUseCase(get()) }
    
    // ViewModels
    viewModel { MainViewModel(get(), get()) }
}
