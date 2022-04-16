package com.rjhartsoftware.fragmentsapp

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment

/**
 * A simple [Fragment] subclass.
 */
class PopupFragment : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(activity)
        builder.setTitle("Popup Fragment")
        builder.setMessage("This is a popup Fragment")
        builder.setPositiveButton("OK", null)
        return builder.create()
    }

    companion object {
        const val TAG = "_popup"
    }
}