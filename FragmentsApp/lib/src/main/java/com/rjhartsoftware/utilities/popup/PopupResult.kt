package com.rjhartsoftware.utilities.popup

import android.os.Bundle

class PopupResult(val which: Int, val request: String, val b: Bundle) {
    fun checkboxResult(): Boolean {
        return b.getBoolean(FragmentMessage.ARG_CHECKBOX_RESULT, false)
    }

    fun inputResult(): String {
        return b.getString(FragmentMessage.ARG_INPUT_RESULT, "")
    }
}