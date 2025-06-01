package com.simplepdf.reader.dialogs

import android.app.Dialog
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.simplepdf.reader.R
import com.simplepdf.reader.utils.FavoritesManager

class FavoritesDialog : DialogFragment() {
    
    private lateinit var favoritesManager: FavoritesManager
    private var onFavoriteSelectedListener: ((Uri) -> Unit)? = null
    
    companion object {
        fun newInstance(): FavoritesDialog {
            return FavoritesDialog()
        }
    }
    
    fun setOnFavoriteSelectedListener(listener: (Uri) -> Unit) {
        onFavoriteSelectedListener = listener
    }
    
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        favoritesManager = FavoritesManager(requireContext())
        val favorites = favoritesManager.getFavorites().toList()
        
        if (favorites.isEmpty()) {
            return AlertDialog.Builder(requireContext())
                .setTitle("Favorites")
                .setMessage("No favorites yet. Add PDFs to favorites for quick access.")
                .setPositiveButton("OK", null)
                .create()
        }
        
        val favoriteNames = favorites.map { uri ->
            Uri.parse(uri).lastPathSegment ?: "Unknown PDF"
        }.toTypedArray()
        
        return AlertDialog.Builder(requireContext())
            .setTitle("Select Favorite PDF")
            .setItems(favoriteNames) { _, which ->
                val selectedUri = Uri.parse(favorites[which])
                onFavoriteSelectedListener?.invoke(selectedUri)
            }
            .setNegativeButton("Cancel", null)
            .create()
    }
}
