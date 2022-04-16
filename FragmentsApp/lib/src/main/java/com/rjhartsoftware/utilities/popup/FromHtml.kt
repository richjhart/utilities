package com.rjhartsoftware.utilities.popup

import android.text.Editable
import android.text.Html
import android.text.Html.TagHandler
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.LeadingMarginSpan
import android.text.style.TabStopSpan
import com.rjhartsoftware.utilities.Truss
import org.xml.sax.XMLReader
import java.util.*

internal object FromHtml {
    private const val INDENT = 80

    @JvmStatic
    fun fromHtml(html: String): Spanned {
        var intHtml = html
        val overrideTags = arrayOf("ul", "ol", "li")
        for (tag in overrideTags) {
            intHtml = intHtml.replace("<$tag>", "<_$tag>")
            intHtml = intHtml.replace("<$tag ", "<_$tag ")
            intHtml = intHtml.replace("</$tag>", "</_$tag>")
        }
        // Can't use the new method until Android 24
        @Suppress("DEPRECATION")
        return Html.fromHtml(intHtml, null,
            object : TagHandler {
                private val lists: Deque<HtmlList> = ArrayDeque()
                private var truss: Truss? = null
                override fun handleTag(
                    opening: Boolean,
                    tag: String,
                    output: Editable,
                    xmlReader: XMLReader
                ) {
                    if (truss == null) {
                        truss = Truss(output as SpannableStringBuilder)
                    }
                    val attributes = Attributes(xmlReader)
                    // lists.peek() cannot be null because we just pushed
                    @Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
                    if (opening && "_ul" == tag) {
                        lists.push(HtmlList(HtmlList.TYPE_BULLET, lists.peek()))
                        checkNewline(output)
                        truss!!.pushSpan(
                            LeadingMarginSpan.Standard(
                                lists.peek().firstIndent,
                                lists.peek().otherIndent
                            )
                        )
                    }
                    if (!opening && "_ul" == tag) {
                        lists.pop()
                        checkNewline(output)
                        truss!!.popSpan()
                    }
                    @Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
                    if (opening && "_ol" == tag) {
                        var type = HtmlList.TYPE_INT
                        when (attributes["type"]) {
                            "a" -> type = HtmlList.TYPE_ALPHA
                            "A" -> type = HtmlList.TYPE_ALPHA_UPPER
                            "i" -> type = HtmlList.TYPE_ROMAN
                            "I" -> type = HtmlList.TYPE_ROMAN_UPPER
                            "1" -> {}
                            else -> {}
                        }
                        val newList = HtmlList(type, lists.peek())
                        if (attributes["indent"] == "false") {
                            newList.dontIndent()
                        }
                        lists.push(newList)
                        checkNewline(output)
                        truss!!.pushSpan(
                            LeadingMarginSpan.Standard(
                                newList.firstIndent,
                                newList.otherIndent
                            )
                        )
                    }
                    if (!opening && "_ol" == tag) {
                        lists.pop()
                        checkNewline(output)
                        truss!!.popSpan()
                    }
                    if (opening && "_li" == tag && lists.isNotEmpty()) {
                        checkNewline(output)
                        truss!!.pushSpan(TabStopSpan.Standard(INDENT))
                        @Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
                        output.append(lists.peek().nextLabel)
                        output.append("\t")
                    }
                    if (!opening && "_li" == tag) {
                        checkNewline(output)
                        truss!!.popSpan()
                    }
                    if (opening && "tab" == tag) {
                        output.append("\t")
                    }
                }

                private fun checkNewline(output: Editable) {
                    if (output.isNotEmpty() && output[output.length - 1] != '\n') {
                        output.append("\n")
                    }
                }
            })
    }

    private class Attributes(reader: XMLReader) {
        private val mAttributes = HashMap<String, String?>()
        private fun has(key: String): Boolean {
            return mAttributes.containsKey(key) && mAttributes[key] != null
        }

        operator fun get(key: String): String {
            val `val` = mAttributes[key]
            return `val` ?: ""
        }

        private fun getCanBeNull(key: String): String? {
            return mAttributes[key]
        }

        init {
            try {
                val elementField = reader.javaClass.getDeclaredField("theNewElement")
                elementField.isAccessible = true
                val element = elementField[reader]
                if (element != null) {
                    val attsField = element.javaClass.getDeclaredField("theAtts")
                    attsField.isAccessible = true
                    val atts = attsField[element]
                    val dataField = atts.javaClass.getDeclaredField("data")
                    dataField.isAccessible = true
                    val data = dataField[atts] as Array<String>
                    val lengthField = atts.javaClass.getDeclaredField("length")
                    lengthField.isAccessible = true
                    val len = lengthField[atts] as Int
                    for (i in 0 until len) {
                        mAttributes[data[i * 5 + 1]] = data[i * 5 + 4]
                    }
                }
            } catch (ignore: Exception) {
            }
        }
    }

    private class HtmlList(private val mType: Int, parent: HtmlList?) {
        private var index = 0
        private val mParent: HtmlList? = parent
        private var dontIndent = false
        fun dontIndent() {
            dontIndent = true
        }

        val firstIndent: Int
            get() = (level - 1) * INDENT
        val otherIndent: Int
            get() = if (mParent != null) {
                firstIndent
            } else {
                INDENT
            }
        private val level: Int
            get() = if (mParent == null) {
                1
            } else {
                mParent.level + if (dontIndent) 0 else 1
            }
        val nextLabel: String
            get() {
                index++
                return currentLabel
            }
        private val currentLabel: String
            get() {
                val builder = StringBuilder()
                when (mType) {
                    TYPE_BULLET -> builder.append("\u2022")
                    TYPE_INT -> {
                        if (mParent != null && mParent.mType == TYPE_INT) {
                            builder.append(mParent.currentLabel)
                            builder.append(".")
                        }
                        builder.append(index)
                    }
                    TYPE_ROMAN -> {
                        builder.append(ROMAN_NUMERALS[index - 1].lowercase(Locale.US))
                        builder.append(".")
                    }
                    TYPE_ROMAN_UPPER -> {
                        builder.append(ROMAN_NUMERALS[index - 1])
                        builder.append(".")
                    }
                    TYPE_ALPHA -> {
                        builder.append("(")
                        builder.append(('a'.code + (index - 1)).toChar())
                        builder.append(")")
                    }
                    TYPE_ALPHA_UPPER -> {
                        builder.append("(")
                        builder.append(('A'.code + (index - 1)).toChar())
                        builder.append(")")
                    }
                }
                return builder.toString()
            }

        companion object {
            const val TYPE_BULLET = 0
            const val TYPE_INT = 1
            const val TYPE_ROMAN = 2
            const val TYPE_ALPHA = 3
            const val TYPE_ALPHA_UPPER = 4
            const val TYPE_ROMAN_UPPER = 5
            private val ROMAN_NUMERALS = arrayOf(
                "I", "II", "III", "IV", "V", "VI", "VII", "IIX", "IX", "X",
                "XI", "XII", "XIII", "XIV", "XV", "XVI", "XVII", "XIIX", "XIX", "XX"
            )
        }

    }
}