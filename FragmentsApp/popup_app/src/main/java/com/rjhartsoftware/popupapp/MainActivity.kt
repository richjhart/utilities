package com.rjhartsoftware.popupapp

import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.text.style.ForegroundColorSpan
import com.rjhartsoftware.utilities.fragments.FragmentTransactions
import com.rjhartsoftware.utilities.fragments.TransactionsActivity
import com.rjhartsoftware.utilities.popup.FragmentMessage
import com.rjhartsoftware.utilities.popup.PopupCheckboxChanged
import com.rjhartsoftware.utilities.popup.PopupResult
import com.rjhartsoftware.utilities.Truss
import com.rjhartsoftware.logcatdebug.D.GENERAL
import com.rjhartsoftware.logcatdebug.D.init
import com.rjhartsoftware.logcatdebug.D.log
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe

class MainActivity : TransactionsActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        init(this)
        super.onCreate(savedInstanceState)
        EventBus.getDefault().register(this)
        setContentView(R.layout.activity_main)
        if (savedInstanceState == null) {
            var builder: FragmentMessage.Builder = FragmentMessage.Builder("second")
                .allowCancel(false)
                .inactiveNegativeButton("Cancel")
                .positiveButton("OK")
                .style(R.style.Alert)
                .transparent()
                .message("This is the second message")
            FragmentTransactions.beginTransaction(this)
                .add(builder.getFragment(this), builder.tag)
                .dontDuplicateTag()
                .commit()
            builder = FragmentMessage.Builder("third")
                .allowCancel(false)
                .allowCancelOnTouchOutside(false)
                .positiveButton("OK")
                .title("Scrollable message")
                .message("This message will scroll<br><br><br><br>.<br><br><br><br><br>.<br><br><br><br><br>.<br><br><br><br><br><br>.<br><br><br><br><br><br>.<br><br><br><br><br><br>At least it should") //.message("This message will scroll<br><br><br><br>At least it should")
                .checkBox("Checkbox", false)
                .mustViewAll()
            FragmentTransactions.beginTransaction(this)
                .add(builder.getFragment(this), builder.tag)
                .dontDuplicateTag()
                .commit()
            builder = FragmentMessage.Builder("fourth")
                .allowCancel(false)
                .allowCancelOnTouchOutside(false)
                .positiveButton("OK")
                .negativeButton("Cancel")
                .title("Scrollable message")
                .message("This message will scroll<br><br><br><br>.<br><br><br><br><br>.<br><br><br><br><br>.<br><br><br><br><br><br>.<br><br><br><br><br><br>.<br><br><br><br><br><br>At least it should") //.message("This message will scroll<br><br><br><br>At least it should")
                .mustViewAll("More")
            FragmentTransactions.beginTransaction(this)
                .add(builder.getFragment(this), builder.tag)
                .addToBackStack(null)
                .dontDuplicateTag()
                .commit()
            builder = FragmentMessage.Builder("fifth")
                .allowCancel(false)
                .allowCancelOnTouchOutside(false)
                .positiveButton("OK")
                .title("Non-scrollable message")
                .message("This message will not scroll<br><br><br><br>.")
                .mustViewAll("More")
            FragmentTransactions.beginTransaction(this)
                .add(builder.getFragment(this), builder.tag)
                .dontDuplicateTag()
                .commit()
            val rawHtml = getString(R.string.raw_html)
            builder = FragmentMessage.Builder("sixth")
                .allowCancel(false)
                .allowCancelOnTouchOutside(false)
                .positiveButton("OK")
                .title("Must-scroll message")
                .message(rawHtml)
                .mustViewAll("More")
            FragmentTransactions.beginTransaction(this)
                .add(builder.getFragment(this), builder.tag)
                .dontDuplicateTag()
                .commit()
            FragmentMessage.Builder("seventh")
                .message("Long buttons")
                .inactivePositiveButton("This is a long positive button")
                .inactiveNegativeButton("This is a long negative button")
                .inactiveNeutralButton("This is a long neutral button")
                .show(this)
            builder = FragmentMessage.Builder("first")
                .allowCancel(false)
                .inactiveNegativeButton("Cancel")
                .positiveButton("OK")
                .message("This is the first message")
                .input("What do you want to return?")
            FragmentTransactions.beginTransaction(this)
                .add(builder.getFragment(this), builder.tag)
                .dontDuplicateTag()
                .commit()
            builder = FragmentMessage.Builder("email")
                .allowCancel(false)
                .inactiveNegativeButton("Cancel")
                .positiveButton("OK")
                .message("This is the first message")
                .input("Email address", InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
            FragmentTransactions.beginTransaction(this)
                .add(builder.getFragment(this), builder.tag)
                .dontDuplicateTag()
                .commit()
            FragmentMessage.Builder("neutral")
                .neutralButton("Help")
                .neutralButtonShouldNotClose()
                .positiveButton("OK")
                .show(this)

            with(Truss()) {
                pushSpan(ForegroundColorSpan(Color.GREEN))
                append("Merry ")
                popSpan()
                pushSpan(ForegroundColorSpan(Color.RED))
                append("Christmas")

                FragmentMessage.Builder("annotated")
                    .message(build())
                    .positiveButton("OK")
                    .show(this@MainActivity)

                append(". And another thing...")

                FragmentMessage.Builder("extra_annotated")
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