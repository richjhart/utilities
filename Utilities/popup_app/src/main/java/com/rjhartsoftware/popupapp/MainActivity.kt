package com.rjhartsoftware.popupapp

import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.text.style.ForegroundColorSpan
import com.rjhartsoftware.utilities.D.GENERAL
import com.rjhartsoftware.utilities.D.log
import com.rjhartsoftware.utilities.RjhsTruss
import com.rjhartsoftware.utilities.fragments.RjhsFragmentTransactions
import com.rjhartsoftware.utilities.fragments.RjhsActivityTransactions
import com.rjhartsoftware.utilities.popup.RjhsFragmentMessage
import com.rjhartsoftware.utilities.popup.PopupCheckboxChanged
import com.rjhartsoftware.utilities.popup.PopupResult
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe

class MainActivity : RjhsActivityTransactions() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EventBus.getDefault().register(this)
        setContentView(R.layout.activity_main)
        if (savedInstanceState == null) {
            var builder: RjhsFragmentMessage.Builder = RjhsFragmentMessage.Builder("second")
                .allowCancel(false)
                .inactiveNegativeButton("Cancel")
                .positiveButton("OK")
                .style(R.style.Alert)
                .transparent()
                .message("This is the second message")
            RjhsFragmentTransactions.beginTransaction(this)
                .add(builder.getFragment(this), builder.tag)
                .dontDuplicateTag()
                .commit()
            builder = RjhsFragmentMessage.Builder("third")
                .allowCancel(false)
                .allowCancelOnTouchOutside(false)
                .positiveButton("OK")
                .title("Scrollable message")
                .message("This message will scroll<br><br><br><br>.<br><br><br><br><br>.<br><br><br><br><br>.<br><br><br><br><br><br>.<br><br><br><br><br><br>.<br><br><br><br><br><br>At least it should") //.message("This message will scroll<br><br><br><br>At least it should")
                .checkBox("Checkbox", false)
                .mustViewAll()
            RjhsFragmentTransactions.beginTransaction(this)
                .add(builder.getFragment(this), builder.tag)
                .dontDuplicateTag()
                .commit()
            builder = RjhsFragmentMessage.Builder("fourth")
                .allowCancel(false)
                .allowCancelOnTouchOutside(false)
                .positiveButton("OK")
                .negativeButton("Cancel")
                .title("Scrollable message")
                .message("This message will scroll<br><br><br><br>.<br><br><br><br><br>.<br><br><br><br><br>.<br><br><br><br><br><br>.<br><br><br><br><br><br>.<br><br><br><br><br><br>At least it should") //.message("This message will scroll<br><br><br><br>At least it should")
                .mustViewAll("More")
            RjhsFragmentTransactions.beginTransaction(this)
                .add(builder.getFragment(this), builder.tag)
                .addToBackStack(null)
                .dontDuplicateTag()
                .commit()
            builder = RjhsFragmentMessage.Builder("fifth")
                .allowCancel(false)
                .allowCancelOnTouchOutside(false)
                .positiveButton("OK")
                .title("Non-scrollable message")
                .message("This message will not scroll<br><br><br><br>.")
                .mustViewAll("More")
            RjhsFragmentTransactions.beginTransaction(this)
                .add(builder.getFragment(this), builder.tag)
                .dontDuplicateTag()
                .commit()
            val rawHtml = getString(R.string.raw_html)
            builder = RjhsFragmentMessage.Builder("sixth")
                .allowCancel(false)
                .allowCancelOnTouchOutside(false)
                .positiveButton("OK")
                .title("Must-scroll message")
                .message(rawHtml)
                .mustViewAll("More")
            RjhsFragmentTransactions.beginTransaction(this)
                .add(builder.getFragment(this), builder.tag)
                .dontDuplicateTag()
                .commit()
            RjhsFragmentMessage.Builder("seventh")
                .message("Long buttons")
                .inactivePositiveButton("This is a long positive button")
                .inactiveNegativeButton("This is a long negative button")
                .inactiveNeutralButton("This is a long neutral button")
                .show(this)
            builder = RjhsFragmentMessage.Builder("first")
                .allowCancel(false)
                .inactiveNegativeButton("Cancel")
                .positiveButton("OK")
                .message("This is the first message")
                .input("What do you want to return?")
            RjhsFragmentTransactions.beginTransaction(this)
                .add(builder.getFragment(this), builder.tag)
                .dontDuplicateTag()
                .commit()
            builder = RjhsFragmentMessage.Builder("email")
                .allowCancel(false)
                .inactiveNegativeButton("Cancel")
                .positiveButton("OK")
                .message("This is the first message")
                .input("Email address", InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
            RjhsFragmentTransactions.beginTransaction(this)
                .add(builder.getFragment(this), builder.tag)
                .dontDuplicateTag()
                .commit()
            RjhsFragmentMessage.Builder("neutral")
                .neutralButton("Help")
                .neutralButtonShouldNotClose()
                .positiveButton("OK")
                .show(this)

            with(RjhsTruss()) {
                pushSpan(ForegroundColorSpan(Color.GREEN))
                append("Merry ")
                popSpan()
                pushSpan(ForegroundColorSpan(Color.RED))
                append("Christmas")

                RjhsFragmentMessage.Builder("annotated")
                    .message(build())
                    .positiveButton("OK")
                    .show(this@MainActivity)

                append(". And another thing...")

                RjhsFragmentMessage.Builder("extra_annotated")
                    .message(build())
                    .positiveButton("OK")
                    .show(this@MainActivity)
            }
        }
    }

    override fun onDestroy() {
        EventBus.getDefault().unregister(this)
        super.onDestroy()
    }

    @Subscribe
    fun onPopupResult(result: PopupResult) {
        log(GENERAL, "Message closed: " + result.request + ". which: " + result.b)
    }

    @Subscribe
    fun onPopupCheckboxChanged(result: PopupCheckboxChanged) {
        log(GENERAL, "Checkbox changed: " + result.request + ". checkbox: " + result.checkbox)
    }
}