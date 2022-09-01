package com.rjhartsoftware.utilities.popup

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.Patterns
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.rjhartsoftware.utilities.R
import com.rjhartsoftware.utilities.cs
import com.rjhartsoftware.utilities.google.app
import java.util.*
import kotlin.collections.HashMap

class RjhsFragmentTranslation : DialogFragment(), DialogInterface.OnClickListener {
    private val mTextChanged: TextWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
            checkText(dialog as AlertDialog?)
        }

        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
            checkText(dialog as AlertDialog?)
        }

        override fun afterTextChanged(s: Editable) {
            checkText(dialog as AlertDialog?)
        }
    }

    private fun checkText(dialog: AlertDialog?) {
        if (dialog == null) {
            return
        }
        val email = dialog.findViewById<TextView>(R.id.rjhs_translate_email_input)
        val language = dialog.findViewById<TextView>(R.id.rjhs_translate_language_input)
        if (email == null || language == null) {
            return
        }
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).isEnabled =
            Patterns.EMAIL_ADDRESS.matcher(email.text).matches() &&
                    !TextUtils.isEmpty(language.text)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        requireActivity().let { activity ->
            val builder = AlertDialog.Builder(activity)
            val inflater = activity.layoutInflater
            @SuppressLint("InflateParams") val v =
                inflater.inflate(R.layout.rjhs_internal_layout_fragment_translation, null)
            val email = v.findViewById<EditText>(R.id.rjhs_translate_email_input)
            email.addTextChangedListener(mTextChanged)
            val language = v.findViewById<EditText>(R.id.rjhs_translate_language_input)
            language.addTextChangedListener(mTextChanged)
            language.setText(Locale.getDefault().displayLanguage)
            val message = v.findViewById<TextView>(R.id.rjhs_translate_message)
            message.text = cs(R.string.rjhs_internal_str_translation_offer_help_message)
            builder.setPositiveButton(R.string.rjhs_internal_str_translation_offer_help_ok, this)
            builder.setNegativeButton(R.string.rjhs_str_cancel, null)
            builder.setView(v)
            builder.setTitle(R.string.rjhs_internal_str_translation_offer_help_title)
            val dialog = builder.create()
            dialog.setOnShowListener { dialog -> checkText(dialog as AlertDialog) }
            return dialog
        }
    }

    override fun onClick(dialog: DialogInterface, which: Int) {
        if (which == DialogInterface.BUTTON_POSITIVE) {
            val ad = dialog as AlertDialog
            val details = HashMap<String, Any>()
            details[TRANSLATION_PARAM_LANGUAGE] =
                (ad.findViewById<View>(R.id.rjhs_translate_language_input) as TextView).text.toString()
            details[TRANSLATION_PARAM_EMAIL] =
                (ad.findViewById<View>(R.id.rjhs_translate_email_input) as TextView).text.toString()
            details[TRANSLATION_PARAM_APP] =
                (app.packageName)
            details[TRANSLATION_PARAM_DEFAULT_LANGUAGE] =
                Locale.getDefault().language
            app.store("feedback_translation", details)
        }
    }

    companion object {
        const val TAG = "_frag_translation"
    }
}

private const val TRANSLATION_PARAM_LANGUAGE = "language"
private const val TRANSLATION_PARAM_EMAIL = "email"
private const val TRANSLATION_PARAM_APP = "app"
private const val TRANSLATION_PARAM_DEFAULT_LANGUAGE = "languageCode"
