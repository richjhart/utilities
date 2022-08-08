package com.rjhartsoftware.utilities.popup

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.text.method.LinkMovementMethod
import android.view.View
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.annotation.StyleRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import com.google.android.material.textfield.TextInputLayout
import com.rjhartsoftware.utilities.fragments.RjhsFragmentTransactions
import com.rjhartsoftware.utilities.R
import com.rjhartsoftware.utilities.fromHtml
import org.greenrobot.eventbus.EventBus

class PopupCheckboxChanged internal constructor(
    val checkbox: Boolean,
    val request: String,
    val b: Bundle
)

class PopupResult(val which: Int, val request: String, val b: Bundle) {
    fun checkboxResult(): Boolean {
        return b.getBoolean(RjhsFragmentMessage.ARG_CHECKBOX_RESULT, false)
    }

    fun inputResult(): String {
        return b.getString(RjhsFragmentMessage.ARG_INPUT_RESULT, "")
    }
}

class RjhsFragmentMessage : DialogFragment(), DialogInterface.OnClickListener,
    CompoundButton.OnCheckedChangeListener, TextWatcher {
    override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
    override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
        requireArguments().putString(ARG_INPUT_RESULT, s.toString())
        val ad = dialog as AlertDialog?
        if (ad != null) {
            ad.getButton(DialogInterface.BUTTON_POSITIVE).isEnabled = !TextUtils.isEmpty(s)
        }
    }

    override fun afterTextChanged(s: Editable) {}

    @SuppressLint("InflateParams")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        assert(arguments != null)
        assert(activity != null)
        val builder = AlertDialog.Builder(
            requireActivity(), requireArguments().getInt(ARG_STYLE)
        )
        val inflater = requireActivity().layoutInflater
        val dialogInterface = inflater.inflate(R.layout.rjhs_fragment_dialog_message, null)
        val title = dialogInterface.findViewById<TextView>(R.id.rjhs_popup_title)
        var titleText = requireArguments().getCharSequence(ARG_TITLE, "")
        if (titleText is String) {
            titleText = fromHtml(titleText)
        }
        title.text = titleText
        val message = dialogInterface.findViewById<TextView>(R.id.rjhs_popup_message)
        var msgText = requireArguments().getCharSequence(ARG_MESSAGE, "")
        if (msgText is String) {
            msgText = fromHtml(msgText)
        }
        message.text = msgText
        val m = message.movementMethod
        if (m !is LinkMovementMethod) {
            message.movementMethod = LinkMovementMethod.getInstance()
        }
        val checkbox = dialogInterface.findViewById<CheckBox>(R.id.rjhs_popup_checkbox)
        if (requireArguments().getCharSequence(ARG_CHECKBOX) != null) {
            checkbox.text = requireArguments().getCharSequence(ARG_CHECKBOX)
            if (requireArguments().getBoolean(ARG_CHECKBOX_RESULT, false)) {
                checkbox.isChecked = true
            }
            checkbox.setOnCheckedChangeListener(this)
        } else {
            checkbox.visibility = View.GONE
        }
        val edit = dialogInterface.findViewById<EditText>(R.id.rjhs_popup_input)
        val editLayout = dialogInterface.findViewById<View>(R.id.rjhs_popup_input_hint)
        if (requireArguments().getString(ARG_INPUT) != null) {
            if (editLayout is TextInputLayout) {
                editLayout.hint = requireArguments().getString(ARG_INPUT)
            } else {
                edit.hint = requireArguments().getString(ARG_INPUT)
            }
            if (requireArguments().getString(ARG_INPUT_RESULT) != null) {
                edit.setText(requireArguments().getString(ARG_INPUT_RESULT))
                edit.setSelection(0, edit.text.length)
            }
            if (requireArguments().containsKey(ARG_INPUT_TYPE)) {
                edit.inputType = requireArguments().getInt(ARG_INPUT_TYPE)
            }
            edit.addTextChangedListener(this)
        } else {
            editLayout.visibility = View.GONE
            edit.visibility = View.GONE
        }
        builder.setView(dialogInterface)
        if (requireArguments().getString(ARG_POSITIVE_BUTTON) != null) {
            builder.setPositiveButton(requireArguments().getString(ARG_POSITIVE_BUTTON), this)
        }
        if (requireArguments().getString(ARG_NEGATIVE_BUTTON) != null) {
            builder.setNegativeButton(requireArguments().getString(ARG_NEGATIVE_BUTTON), this)
        }
        if (requireArguments().getString(ARG_NEUTRAL_BUTTON) != null) {
            builder.setNeutralButton(requireArguments().getString(ARG_NEUTRAL_BUTTON), this)
        }
        builder.setCancelable(requireArguments().getBoolean(ARG_CANCEL))
        val dialog = builder.create()
        dialog.setCanceledOnTouchOutside(requireArguments().getBoolean(ARG_CANCEL_TOUCH))
        dialog.setOnShowListener { dialogInt ->
            assert(arguments != null)
            if (requireArguments().getBoolean(ARG_TRANSPARENT)) {
                if ((dialogInt as AlertDialog).window != null) {
                    dialogInt.window!!.decorView.setBackgroundResource(android.R.color.transparent)
                }
            }
            val sv =
                (dialogInt as AlertDialog).findViewById<NestedScrollView>(R.id.rjhs_popup_scroll)
            sv!!.tag = false
            if (requireArguments().getBoolean(ARG_NEUTRAL_BUTTON_STAY_OPEN)) {
                val neutral = dialogInt.getButton(DialogInterface.BUTTON_NEUTRAL)
                neutral?.setOnClickListener { onClick(dialogInt, AlertDialog.BUTTON_NEUTRAL) }
            }
            if (requireArguments().getBoolean(ARG_MUST_VIEW_ALL)) {
                val ok = dialogInt.getButton(DialogInterface.BUTTON_POSITIVE)
                if (ok != null) {
                    sv.tag = true
                    if (requireArguments().getString(ARG_MUST_VIEW_ALL_MORE) == null) {
                        ok.isEnabled = false
                    } else {
                        ok.isEnabled = true
                        ok.text = requireArguments().getString(ARG_MUST_VIEW_ALL_MORE)
                    }
                    if (sv.canScrollVertically(1)) {
                        sv.setOnScrollChangeListener(NestedScrollView.OnScrollChangeListener { v, scrollX, scrollY, oldScrollX, oldScrollY ->
                            if (!v.canScrollVertically(1)) {
                                ok.isEnabled = true
                                sv.tag = false
                                ok.text = requireArguments().getString(ARG_POSITIVE_BUTTON)
                            }
                        })
                        if (requireArguments().getString(ARG_MUST_VIEW_ALL_MORE) != null) {
                            ok.setOnClickListener {
                                if (sv.tag as Boolean) {
                                    sv.pageScroll(View.FOCUS_DOWN)
                                } else {
                                    onClick(dialogInt, AlertDialog.BUTTON_POSITIVE)
                                    dialogInt.dismiss()
                                }
                            }
                        }
                    } else {
                        ok.isEnabled = true
                        sv.tag = false
                        ok.text = requireArguments().getString(ARG_POSITIVE_BUTTON)
                    }
                }
            }
        }
        return dialog
    }

    private class SavedString(
        private val which: String,
        @field:StringRes @param:StringRes private val res: Int,
        vararg inArgs: Any?
    ) {
        private val args: Array<out Any?> = inArgs

        fun process(bundle: Bundle, context: Context) {
            bundle.putString(which, String.format(context.getString(res), *args))
        }

    }

    class Builder @JvmOverloads constructor( /*Class<? extends PopupResult> callback,*/
        requestId: String? =  /*null,*/null
    ) {
        private val mArguments = Bundle()
        private val mStrings: MutableList<SavedString> = ArrayList()
        private fun setString(which: String, @StringRes value: Int, vararg format: Any?): Builder {
            mStrings.add(SavedString(which, value, *format))
            return this
        }

        private fun setString(which: String, value: CharSequence): Builder {
            mArguments.putCharSequence(which, value)
            return this
        }

        fun title(@StringRes title: Int, vararg format: Any?): Builder {
            return setString(ARG_TITLE, title, *format)
        }

        fun title(title: CharSequence): Builder {
            return setString(ARG_TITLE, title)
        }

        fun message(@StringRes message: Int, vararg format: Any?): Builder {
            return setString(ARG_MESSAGE, message, *format)
        }

        fun message(message: CharSequence): Builder {
            return setString(ARG_MESSAGE, message)
        }

        fun positiveButton(@StringRes label: Int, vararg format: Any?): Builder {
            return setString(ARG_POSITIVE_BUTTON, label, *format)
        }

        fun positiveButton(label: String): Builder {
            return setString(ARG_POSITIVE_BUTTON, label)
        }

        fun inactivePositiveButton(@StringRes label: Int, vararg format: Any?): Builder {
            mArguments.putBoolean(ARG_POSITIVE_BUTTON_INACTIVE, true)
            return setString(ARG_POSITIVE_BUTTON, label, *format)
        }

        fun inactivePositiveButton(label: String): Builder {
            mArguments.putBoolean(ARG_POSITIVE_BUTTON_INACTIVE, true)
            return setString(ARG_POSITIVE_BUTTON, label)
        }

        fun negativeButton(@StringRes label: Int, vararg format: Any?): Builder {
            return setString(ARG_NEGATIVE_BUTTON, label, *format)
        }

        fun negativeButton(label: String): Builder {
            return setString(ARG_NEGATIVE_BUTTON, label)
        }

        fun inactiveNegativeButton(@StringRes label: Int, vararg format: Any?): Builder {
            mArguments.putBoolean(ARG_NEGATIVE_BUTTON_INACTIVE, true)
            return setString(ARG_NEGATIVE_BUTTON, label, *format)
        }

        fun inactiveNegativeButton(label: String): Builder {
            mArguments.putBoolean(ARG_NEGATIVE_BUTTON_INACTIVE, true)
            return setString(ARG_NEGATIVE_BUTTON, label)
        }

        fun neutralButton(@StringRes label: Int, vararg format: Any?): Builder {
            return setString(ARG_NEUTRAL_BUTTON, label, *format)
        }

        fun neutralButton(label: String): Builder {
            return setString(ARG_NEUTRAL_BUTTON, label)
        }

        fun inactiveNeutralButton(@StringRes label: Int, vararg format: Any?): Builder {
            mArguments.putBoolean(ARG_NEUTRAL_BUTTON_INACTIVE, true)
            return setString(ARG_NEUTRAL_BUTTON, label, *format)
        }

        fun inactiveNeutralButton(label: String): Builder {
            mArguments.putBoolean(ARG_NEUTRAL_BUTTON_INACTIVE, true)
            return setString(ARG_NEUTRAL_BUTTON, label)
        }

        fun neutralButtonShouldNotClose(): Builder {
            mArguments.putBoolean(ARG_NEUTRAL_BUTTON_STAY_OPEN, true)
            return this
        }

        fun allowCancel(allowCancel: Boolean): Builder {
            mArguments.putBoolean(ARG_CANCEL, allowCancel)
            return this
        }

        fun allowCancelOnTouchOutside(allowCancel: Boolean): Builder {
            mArguments.putBoolean(ARG_CANCEL_TOUCH, allowCancel)
            return this
        }

        fun save(key: String?, value: String?): Builder {
            mArguments.putString(key, value)
            return this
        }

        fun transparent(): Builder {
            mArguments.putBoolean(ARG_TRANSPARENT, true)
            return this
        }

        fun style(@StyleRes style: Int): Builder {
            mArguments.putInt(ARG_STYLE, style)
            return this
        }

        fun input(query: String?, type: Int): Builder {
            mArguments.putString(ARG_INPUT, query)
            mArguments.putInt(ARG_INPUT_TYPE, type)
            return this
        }

        fun input(query: String?): Builder {
            mArguments.putString(ARG_INPUT, query)
            return this
        }

        fun input(query: String?, initial: String?): Builder {
            mArguments.putString(ARG_INPUT, query)
            mArguments.putString(ARG_INPUT_RESULT, initial)
            return this
        }

        fun input(query: String?, initial: String?, type: Int): Builder {
            mArguments.putString(ARG_INPUT, query)
            mArguments.putString(ARG_INPUT_RESULT, initial)
            mArguments.putInt(ARG_INPUT_TYPE, type)
            return this
        }

        fun mustViewAll(): Builder {
            mArguments.putBoolean(ARG_MUST_VIEW_ALL, true)
            return this
        }

        fun mustViewAll(moreButton: String?): Builder {
            mArguments.putBoolean(ARG_MUST_VIEW_ALL, true)
            mArguments.putString(ARG_MUST_VIEW_ALL_MORE, moreButton)
            return this
        }

        fun checkBox(message: CharSequence?, checked: Boolean): Builder {
            mArguments.putBoolean(ARG_CHECKBOX_RESULT, checked)
            mArguments.putCharSequence(ARG_CHECKBOX, message)
            return this
        }

        fun getFragment(context: Context): Fragment {
            for (ss in mStrings) {
                ss.process(mArguments, context)
            }
            mStrings.clear()
            val frag = RjhsFragmentMessage()
            frag.arguments = mArguments
            frag.isCancelable = mArguments.getBoolean(ARG_CANCEL)
            return frag
        }

        val tag: String
            get() = TAG + mArguments.getString(ARG_REQUEST_TAG)

        fun show(activity: AppCompatActivity?) {
            if (activity == null) {
                return
            }
            RjhsFragmentTransactions.beginTransaction(activity)
                .add(getFragment(activity), tag)
                .dontDuplicateTag()
                .commit()
        }

        init {
            var rId = requestId
            if (rId == null) {
                rId = "_auto_" + sAutoRequestId
                sAutoRequestId++
            }
            //            mArguments.putString(ARG_RESULT_CLASS, callback.getCanonicalName());
            mArguments.putString(ARG_REQUEST_TAG, rId)
            //            mArguments.putString(ARG_CHECKBOX_CHANGED_CLASS, PopupCheckboxChanged.class.getCanonicalName());
            mArguments.putInt(ARG_STYLE, R.style.RjhsAlertDialogTheme)
        }
    }

    override fun onClick(dialog: DialogInterface, which: Int) {
        assert(arguments != null)
        when (which) {
            AlertDialog.BUTTON_POSITIVE -> if (requireArguments().getBoolean(
                    ARG_POSITIVE_BUTTON_INACTIVE,
                    false
                )
            ) {
                return
            }
            AlertDialog.BUTTON_NEGATIVE -> if (requireArguments().getBoolean(
                    ARG_NEGATIVE_BUTTON_INACTIVE,
                    false
                )
            ) {
                return
            }
            AlertDialog.BUTTON_NEUTRAL -> if (requireArguments().getBoolean(
                    ARG_NEUTRAL_BUTTON_INACTIVE,
                    false
                )
            ) {
                return
            }
        }
        EventBus.getDefault().post(
            PopupResult(
                which,
                requireArguments().getString(ARG_REQUEST_TAG)!!,
                requireArguments()
            )
        )
    }

    override fun onCancel(dialog: DialogInterface) {
        onClick(dialog, DialogInterface.BUTTON_NEGATIVE)
    }

    override fun onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) {
        assert(arguments != null)
        requireArguments().putBoolean(ARG_CHECKBOX_RESULT, isChecked)
        EventBus.getDefault().post(
            PopupCheckboxChanged(
                isChecked,
                requireArguments().getString(ARG_REQUEST_TAG)!!,
                requireArguments()
            )
        )
    }

    companion object {
        private const val TAG = "_frag_message."

        //    private static final String ARG_RESULT_CLASS = "class";
        //    private static final String ARG_CHECKBOX_CHANGED_CLASS = "checkbox_class";
        private const val ARG_TITLE = "title"
        private const val ARG_MESSAGE = "message"
        private const val ARG_POSITIVE_BUTTON = "positive"
        private const val ARG_NEGATIVE_BUTTON = "negative"
        private const val ARG_NEUTRAL_BUTTON = "neutral"
        private const val ARG_POSITIVE_BUTTON_INACTIVE = "positive_action"
        private const val ARG_NEGATIVE_BUTTON_INACTIVE = "negative_action"
        private const val ARG_NEUTRAL_BUTTON_INACTIVE = "neutral_action"
        private const val ARG_CANCEL = "allowCancel"
        private const val ARG_CANCEL_TOUCH = "allowCancelOnTouch"
        private const val ARG_REQUEST_TAG = "tag"
        private const val ARG_INPUT = "input"
        const val ARG_INPUT_RESULT = "input_result"
        private const val ARG_INPUT_TYPE = "input_type"
        private const val ARG_CHECKBOX = "checkbox"
        const val ARG_CHECKBOX_RESULT = "checkbox_result"
        private const val ARG_STYLE = "style"
        private const val ARG_TRANSPARENT = "transparent"
        private const val ARG_MUST_VIEW_ALL = "must_view_all"
        private const val ARG_MUST_VIEW_ALL_MORE = "must_view_all_more"
        private const val ARG_NEUTRAL_BUTTON_STAY_OPEN = "neutral_stay_open"
        private var sAutoRequestId = 0
    }
}