package com.rjhartsoftware.debug

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.rjhartsoftware.utilities.D
import com.rjhartsoftware.utilities.D.DebugTag
import com.rjhartsoftware.utilities.D.error
import com.rjhartsoftware.utilities.D.log
import com.rjhartsoftware.utilities.D.setLines
import com.rjhartsoftware.utilities.D.warn

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        log(D.GENERAL, "General comment")
        log(TEST, "Test comment")
        log(TEST_2, "Test comment 2")
        TEST.disable()
        log(TEST, "This shouldn't appear")
        TEST.enable()
        log(TEST, "This should appear")
        if (!D.isLogging(TEST)) {
            log(TEST, "This also shouldn't appear")
        }
        TEST.setEnabled(false)
        log(TEST, "This shouldn't appear")
        TEST.setEnabled(true)
        if (D.isBeta()) {
            log(TEST, "Shouldn't appear as we're not in Beta")
        }
        log(TEST, "This is a long message that should be cut\n2\n3\n4\n5\n6\n7\n8\n9\n10\n11\n12")
        log(
            SHOW_ALL,
            "This is a long message that should be fully displayed\n2\n3\n4\n5\n6\n7\n8\n9\n10\n11\n12"
        )
        setLines(D.SHOW_ALL_LINES, D.SHOW_ALL_LINES)
        log(
            TEST,
            "This is a long message that should now be fully displayed\n2\n3\n4\n5\n6\n7\n8\n9\n10\n11\n12"
        )
        log(TEST, "This %s %d %s", "is a formatted message with", 3, "arguments")
        indirectLog()
        log(TEST, "Errors and so on...")
        log(TEST, "==============================================")
        warn(TEST, "This is a warning")
        error(TEST, "This is an error")
        log(
            TEST,
            "This is a formatted log with an exception (%d)",
            Exception("Log level exception"),
            1
        )
        warn(
            TEST,
            "This is a formatted warning with an exception (%d)",
            Exception("Warning level exception"),
            2
        )
        error(
            TEST,
            "This is a formatted error with an exception (%d)",
            Exception("Error level exception"),
            3
        )
        log(TEST, "==============================================")
    }

    private fun indirectLog() {
        log(TEST.indirect(), "This should state it's in onCreate")
        log(TEST, "This should state it's in indirectLog")
        TEST.increaseLevel()
        log(TEST, "This should statis it's in onCreate")
        TEST.decreaseLevel()
    }

    companion object {
        private val TEST = DebugTag("test")
        private val TEST_2 = DebugTag("test2", true)
        private val SHOW_ALL = DebugTag("test3", true, 0, D.SHOW_ALL_LINES, D.SHOW_ALL_LINES)
    }
}