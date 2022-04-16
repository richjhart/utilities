package com.rjhartsoftware.utilities

import android.text.SpannableStringBuilder
import android.text.Spanned
import java.util.*

class Truss {
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

    private constructor(truss: Truss) {
        builder = SpannableStringBuilder(truss.builder)
        stack = ArrayDeque(truss.stack)
    }

    fun append(string: String?): Truss {
        builder.append(string)
        return this
    }

    fun append(charSequence: CharSequence?): Truss {
        builder.append(charSequence)
        return this
    }

    fun append(c: Char): Truss {
        builder.append(c)
        return this
    }

    fun append(number: Int): Truss {
        builder.append(number.toString())
        return this
    }

    /**
     * Starts `span` at the current position in the builder.
     */
    fun pushSpan(span: Any?): Truss {
        stack.addLast(Span(builder.length, span))
        return this
    }

    /**
     * End the most recently pushed span at the current position in the builder.
     */
    fun popSpan(): Truss {
        val span = stack.removeLast()
        builder.setSpan(span.span, span.start, builder.length, Spanned.SPAN_INCLUSIVE_EXCLUSIVE)
        return this
    }

    /**
     * Create the final [CharSequence], popping any remaining spans.
     */
    fun build(): CharSequence {
        val t = Truss(this)
        while (!t.stack.isEmpty()) {
            t.popSpan()
        }
        return t.builder
    }

    private class Span constructor(val start: Int, val span: Any?)
}