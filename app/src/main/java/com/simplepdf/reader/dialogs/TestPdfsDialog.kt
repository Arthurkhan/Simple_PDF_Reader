package com.simplepdf.reader.dialogs

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.simplepdf.reader.R

class TestPdfsDialog : DialogFragment() {

    private var onPdfSelectedListener: ((String) -> Unit)? = null

    companion object {
        fun newInstance(): TestPdfsDialog {
            return TestPdfsDialog()
        }
    }

    fun setOnPdfSelectedListener(listener: (String) -> Unit) {
        onPdfSelectedListener = listener
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.dialog_test_pdfs, null)

        val container = view.findViewById<LinearLayout>(R.id.testPdfsContainer)
        val emptyView = view.findViewById<TextView>(R.id.emptyView)

        // Get list of PDFs from assets/pdfs folder
        val pdfFiles = try {
            context.assets.list("pdfs")?.filter { it.endsWith(".pdf", ignoreCase = true) } ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }

        if (pdfFiles.isEmpty()) {
            emptyView.visibility = View.VISIBLE
            container.visibility = View.GONE
        } else {
            emptyView.visibility = View.GONE
            container.visibility = View.VISIBLE

            // Add each PDF as a clickable item
            pdfFiles.forEach { pdfName ->
                val itemView = inflater.inflate(R.layout.item_test_pdf, container, false)
                val nameText = itemView.findViewById<TextView>(R.id.pdfName)
                
                nameText.text = pdfName.removeSuffix(".pdf")
                
                itemView.setOnClickListener {
                    onPdfSelectedListener?.invoke("pdfs/$pdfName")
                    dismiss()
                }
                
                container.addView(itemView)
            }
        }

        return MaterialAlertDialogBuilder(context)
            .setTitle("Test PDFs")
            .setView(view)
            .setNegativeButton("Cancel") { _, _ ->
                dismiss()
            }
            .create()
    }
}
