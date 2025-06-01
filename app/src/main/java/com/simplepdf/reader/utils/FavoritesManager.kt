package com.simplepdf.reader.utils

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri

class FavoritesManager(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    companion object {
        private const val PREFS_NAME = "pdf_favorites"
        private const val KEY_FAVORITES = "favorites_list"
    }
    
    fun addFavorite(uri: Uri) {
        val favorites = getFavorites().toMutableSet()
        favorites.add(uri.toString())
        saveFavorites(favorites)
    }
    
    fun removeFavorite(uri: Uri) {
        val favorites = getFavorites().toMutableSet()
        favorites.remove(uri.toString())
        saveFavorites(favorites)
    }
    
    fun getFavorites(): Set<String> {
        return prefs.getStringSet(KEY_FAVORITES, emptySet()) ?: emptySet()
    }
    
    fun isFavorite(uri: Uri): Boolean {
        return getFavorites().contains(uri.toString())
    }
    
    private fun saveFavorites(favorites: Set<String>) {
        prefs.edit().putStringSet(KEY_FAVORITES, favorites).apply()
    }
}
