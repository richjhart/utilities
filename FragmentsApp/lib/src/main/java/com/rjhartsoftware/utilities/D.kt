package com.rjhartsoftware.utilities

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.rjhartsoftware.fragments.R
import java.util.*

@SuppressLint("SetTextI18n")
object D {
    private const val TAG_FORMAT = "%s%s"
    private const val TAG = "debug_"

    private enum class Level {
        DEBUG,
        WARNING,
        ERROR
    }

    const val SHOW_DEFAULT_LINES = -1
    const val SHOW_ALL_LINES = -2
    private const val DEFAULT_LINES_AT_START = 4
    private const val DEFAULT_LINE_AT_END = 4

    @JvmField
    val GENERAL = DebugTag("general", true)

    @JvmStatic
    fun init(context: Context) {
        sDebug = context.resources.getBoolean(R.bool.enable_debug_mode)
        sBeta = context.resources.getBoolean(R.bool.enable_beta_mode)
    }

    @JvmStatic
    private var sDebug: Boolean? = null

    @JvmStatic
    private fun isDebug(): Boolean {
        if (sDebug != null) {
            return sDebug as Boolean
        }
        return false
    }

    @JvmStatic
    private var sBeta: Boolean? = null

    @JvmStatic
    fun isBeta(): Boolean {
        if (sBeta != null) {
            return sBeta as Boolean
        }
        return false
    }

    private var sDefaultStartLines: Int = DEFAULT_LINES_AT_START
    private var sDefaultEndLines: Int = DEFAULT_LINE_AT_END

    @JvmStatic
    fun setLines(lines_at_start: Int, lines_at_end: Int) {
        sDefaultStartLines = lines_at_start
        sDefaultEndLines = lines_at_end
    }

    @JvmStatic
    fun log(tag: DebugTag, msg: String, vararg args: Any?) {
        if (isDebug()) {
            internalLog(tag, msg, args, Level.DEBUG, null, tag.startLines(), tag.endLines())
        }
    }

    @JvmStatic
    fun warn(tag: DebugTag, msg: String, vararg args: Any?) {
        if (isDebug()) {
            internalLog(tag, msg, args, Level.WARNING, null, tag.startLines(), tag.endLines())
        }
    }

    @JvmStatic
    fun error(tag: DebugTag, msg: String, vararg args: Any?) {
        if (isDebug()) {
            internalLog(tag, msg, args, Level.ERROR, null, tag.startLines(), tag.endLines())
        }
    }

    @JvmStatic
    fun log(tag: DebugTag, msg: String, tr: Throwable?, vararg args: Any?) {
        if (isDebug()) {
            internalLog(tag, msg, args, Level.DEBUG, tr, tag.startLines(), tag.endLines())
        }
    }

    @JvmStatic
    fun warn(tag: DebugTag, msg: String, tr: Throwable?, vararg args: Any?) {
        if (isDebug()) {
            internalLog(tag, msg, args, Level.WARNING, tr, tag.startLines(), tag.endLines())
        }
    }

    @JvmStatic
    fun error(tag: DebugTag, msg: String, tr: Throwable?, vararg args: Any?) {
        if (isDebug()) {
            internalLog(tag, msg, args, Level.ERROR, tr, tag.startLines(), tag.endLines())
        }
    }

    private fun internalLog(
        tag: DebugTag,
        msg: String,
        args: Array<out Any?>?,
        level: Level,
        tr: Throwable?,
        showStart: Int,
        showEnd: Int
    ) {
        var intMsg = msg
        var intShowStart = showStart
        var intShowEnd = showEnd
        if (tag.mEnabled) {
            if (args != null && args.isNotEmpty()) {
                try {
                    intMsg = String.format(Locale.US, intMsg, *args)
                } catch (e: Exception) {
                    error(tag, "Unable to format string, showing raw msg:")
                }
            }
            val origLines = intMsg.split("\n").toTypedArray()
            val lines: Queue<String> = ArrayDeque()
            for (orig_line in origLines) {
                var remaining = orig_line
                while (remaining.isNotEmpty()) {
                    remaining = if (remaining.length > 1000) {
                        lines.add(remaining.substring(0, 1000))
                        remaining.substring(1000)
                    } else {
                        lines.add(remaining)
                        ""
                    }
                }
            }
            if (intShowStart < 0) {
                intShowStart = lines.size
            }
            if (intShowEnd < 0) {
                intShowEnd = lines.size
            }
            var caller: String = caller(tag.mFileLevel, tag.mCallLevel + tag.mTempLevel)
            tag.mTempLevel = 0
            val sb = StringBuilder()
            for (i in caller.indices) {
                sb.append("\u00A0")
            }
            while (intShowStart > 0 && !lines.isEmpty()) {
                // lines.peek() can't return null, because we just checked that it's not empty
                @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
                (print(
        tag.toString(),
        caller,
        lines.peek(),
        if (lines.size == 1) tr else null,
        level
    ))
                lines.poll()
                caller = sb.toString()
                intShowStart--
            }
            if (!lines.isEmpty()) {
                if (intShowEnd < lines.size) {
                    print(tag.toString(), caller, "...", tr, level)
                    caller = sb.toString()
                }
                while (intShowEnd > 0 && !lines.isEmpty()) {
                    // lines.peek() can't return null, because we just checked that it's not empty
                    @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
                    (print(
        tag.toString(),
        caller,
        lines.peek(),
        if (lines.size == 1) tr else null,
        level
    ))
                    lines.poll()
                    caller = sb.toString()
                    intShowEnd--
                }
            }
        }
    }

    @SuppressLint("LogNotTimber")
    private fun print(tag: String, prefix: String, main: String, tr: Throwable?, level: Level) {
        val msg = String.format("%s%s", prefix, main)
        when (level) {
            Level.DEBUG -> if (tr != null) {
                Log.d(tag, msg, tr)
            } else {
                Log.d(tag, msg)
            }
            Level.WARNING -> if (tr != null) {
                Log.w(tag, msg, tr)
            } else {
                Log.w(tag, msg)
            }
            Level.ERROR -> if (tr != null) {
                Log.e(tag, msg, tr)
            } else {
                Log.e(tag, msg)
            }
        }
    }

    private fun caller(fileLevel: Int, callLevel: Int): String {
        val t: Throwable = Exception()
        if (t.stackTrace.isNotEmpty()) {
            var thisFile = t.stackTrace[0].fileName
            if (thisFile == null) {
                thisFile = "(unknown file): "
            }
            var fileCount = 0
            var callCount = 0
            for (ste in t.stackTrace) {
                if (thisFile != ste.fileName) {
                    fileCount++
                    callCount = 0
                } else {
                    callCount++
                }
                if (fileCount > fileLevel && callCount == callLevel) {
                    return String.format(
                        Locale.US,
                        "(%s:%d).%s(): ",
                        ste.fileName,
                        ste.lineNumber,
                        ste.methodName
                    )
                } else {
                    thisFile = ste.fileName
                    if (thisFile == null) {
                        thisFile = "(unknown file): "
                    }
                }
            }
        }
        return "(unknown source): "
    }

    @JvmStatic
    fun isLogging(tag: DebugTag): Boolean {
        return isDebug() && tag.mEnabled
    }

    class DebugTag @JvmOverloads constructor(
        private val mTag: String?,
        enabled_by_default: Boolean = true,
        caller_level: Int = 0,
        lines_at_start: Int = SHOW_DEFAULT_LINES,
        lines_at_end: Int = SHOW_DEFAULT_LINES
    ) {
        internal var mEnabled = false
        internal var mFileLevel = 0
        internal var mCallLevel = 0
        internal var mTempLevel = 0
        private val mStartLines: Int
        private val mEndLines: Int

        override fun toString(): String {
            return String.format(TAG_FORMAT, TAG, mTag)
        }

        fun enable() {
            setEnabled(true)
        }

        fun disable() {
            setEnabled(false)
        }

        fun indirect(): DebugTag {
            mTempLevel++
            return this
        }

        fun increaseLevel() {
            mCallLevel++
        }

        fun decreaseLevel() {
            mCallLevel--
        }

        fun setEnabled(enabled: Boolean) {
            mEnabled = enabled
        }

        fun startLines(): Int {
            return when (mStartLines) {
                SHOW_DEFAULT_LINES -> {
                    sDefaultStartLines
                }
                SHOW_ALL_LINES -> {
                    SHOW_ALL_LINES
                }
                else -> {
                    mStartLines
                }
            }
        }

        fun endLines(): Int {
            return when {
                mEndLines == SHOW_DEFAULT_LINES -> {
                    sDefaultEndLines
                }
                mStartLines == SHOW_ALL_LINES -> {
                    SHOW_ALL_LINES
                }
                else -> {
                    mEndLines
                }
            }
        }

        init {
            mEnabled = enabled_by_default
            mFileLevel = caller_level
            mStartLines = lines_at_start
            mEndLines = lines_at_end
        }
    }
}