package com.rjhartsoftware.utilities

import android.os.Build
import android.text.Html
import android.text.SpannableStringBuilder
import android.text.Spanned
import androidx.core.text.HtmlCompat
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

class RjhsUtils