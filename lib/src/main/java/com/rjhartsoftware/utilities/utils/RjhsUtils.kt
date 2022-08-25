package com.rjhartsoftware.utilities

import android.content.ActivityNotFoundException
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Looper
import android.text.Html
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.view.View
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.rpc.Help
import com.rjhartsoftware.utilities.google.app
import com.rjhartsoftware.utilities.popup.RjhsFragmentMessage
import com.rjhartsoftware.utilities.utils.BackgroundActivityOnMainThreadException
import com.rjhartsoftware.utilities.utils.D
import me.saket.bettermovementmethod.BetterLinkMovementMethod
import java.util.*

class RjhsTruss {
    private val builder: SpannableStringBuilder
    private val stack: Deque<Span>

    constructor() {
        builder = SpannableStringBuilder()
        stack = ArrayDeque()
    }

    constructor(ssb: SpannableStringBuilder) {
        builder = ssb
        stack = ArrayDeque()
    }

    private constructor(truss: RjhsTruss) {
        builder = SpannableStringBuilder(truss.builder)
        stack = ArrayDeque(truss.stack)
    }

    fun append(string: String?): RjhsTruss {
        builder.append(string)
        return this
    }

    fun append(charSequence: CharSequence?): RjhsTruss {
        builder.append(charSequence)
        return this
    }

    fun append(c: Char): RjhsTruss {
        builder.append(c)
        return this
    }

    fun append(number: Int): RjhsTruss {
        builder.append(number.toString())
        return this
    }

    /**
     * Starts `span` at the current position in the builder.
     */
    fun pushSpan(span: Any?): RjhsTruss {
        stack.addLast(Span(builder.length, span))
        return this
    }

    /**
     * End the most recently pushed span at the current position in the builder.
     */
    fun popSpan(): RjhsTruss {
        val span = stack.removeLast()
        builder.setSpan(span.span, span.start, builder.length, Spanned.SPAN_INCLUSIVE_EXCLUSIVE)
        return this
    }

    /**
     * Create the final [CharSequence], popping any remaining spans.
     */
    fun build(): CharSequence {
        val t = RjhsTruss(this)
        while (!t.stack.isEmpty()) {
            t.popSpan()
        }
        return t.builder
    }

    private class Span constructor(val start: Int, val span: Any?)
}

fun fromHtml(html: String): Spanned =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        Html.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY)
    } else {
        @Suppress("DEPRECATION")
        Html.fromHtml(html)
    }

fun getActivity(v: View): AppCompatActivity {
    var c = v.context
    while (c is ContextWrapper) {
        if (c is AppCompatActivity) {
            return c
        }
        c = c.baseContext
    }
    throw RuntimeException("Unable to find activity")
}

fun prepareLinks(tv: TextView) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
        if (tv.movementMethod !is BetterLinkMovementMethod) {
            Linkify.addLinks(tv, Linkify.WEB_URLS)
            val mm = BetterLinkMovementMethod.newInstance()
            mm.setOnLinkClickListener { textView, url ->
                openUrl(getActivity(textView), url)
                true
            }
            tv.movementMethod = mm
        }
    } else {
        if (tv.movementMethod !is LinkMovementMethod) {
            Linkify.addLinks(tv, Linkify.WEB_URLS)
            tv.movementMethod = LinkMovementMethod.getInstance()
        }
    }
}

fun openUrl(context: AppCompatActivity?, url: String?) {
    if (context == null) {
        return
    }

    if (!app.getBoolPref(app.getString(R.string.rjhs_fixed_settings_key_external_browser))) {
        try {
            val builder = CustomTabsIntent.Builder()
            builder.setToolbarColor(ContextCompat.getColor(context, R.color.rjhs_color_primary))
            val customTabsIntent = builder.build()
            customTabsIntent.launchUrl(context, Uri.parse(url))
            return
        } catch (e: ActivityNotFoundException) {
        }
    }

    val uri = Uri.parse(url)
    val webIntent = Intent(Intent.ACTION_VIEW, uri)
    val packageManager = context.packageManager
    val activities =
        packageManager.queryIntentActivities(webIntent, PackageManager.MATCH_DEFAULT_ONLY)
    if (activities.isNotEmpty()) {
        try {
            context.startActivity(webIntent)
            return
        } catch (ignore: ActivityNotFoundException) {
        }
    }

    RjhsFragmentMessage.Builder()
        .message(R.string.rjhs_internal_str_msg_no_browser)
        .inactivePositiveButton(R.string.rjhs_str_ok)
        .show(context)
}

fun assertNotMainThread(reason: String? = null) {
    val isUiThread =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Looper.getMainLooper().isCurrentThread else Thread.currentThread() === Looper.getMainLooper().thread
    if (isUiThread) {
        val exception = if (reason.isNullOrBlank()) {
            BackgroundActivityOnMainThreadException()
        } else {
            BackgroundActivityOnMainThreadException(reason)
        }
        if (D.isDebug()) {
            throw exception
        } else {
            FirebaseCrashlytics.getInstance().recordException(exception)
        }
    }
}

fun assertMainThread(reason: String? = null) {
    val isUiThread =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Looper.getMainLooper().isCurrentThread else Thread.currentThread() === Looper.getMainLooper().thread
    if (!isUiThread) {
        val exception = if (reason.isNullOrBlank()) {
            BackgroundActivityOnMainThreadException()
        } else {
            BackgroundActivityOnMainThreadException(reason)
        }
        if (D.isDebug()) {
            throw exception
        } else {
            FirebaseCrashlytics.getInstance().recordException(exception)
        }
    }
}

fun cs(@StringRes id: Int, vararg formatArgs: Any?): CharSequence {
    val html: String = app.getString(id, *formatArgs)
    return fromHtml(html)
}

class RjhsUtils