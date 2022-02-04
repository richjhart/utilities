package com.rjhartsoftware.fragmentsapp

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.view.View
import com.rjhartsoftware.fragments.FragmentTransactions.Companion.beginTransaction
import com.rjhartsoftware.fragments.TransactionsActivity
import com.rjhartsoftware.logcatdebug.D.GENERAL
import com.rjhartsoftware.logcatdebug.D.init
import com.rjhartsoftware.logcatdebug.D.log

class MainActivity : TransactionsActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        init(BuildConfig.VERSION_NAME, BuildConfig.DEBUG)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<View>(R.id.button_pause).setOnClickListener { // trigger an event that will pause it
            val popupScreen = Intent(this@MainActivity, PopupActivity::class.java)
            startActivity(popupScreen)

            // then run the transaction after a delay
            Handler().postDelayed(mDelayedTransaction, 2000)
        }
        findViewById<View>(R.id.button_stop).setOnClickListener { // trigger an event that will stop it
            val fullScreen = Intent(this@MainActivity, FullActivity::class.java)
            startActivity(fullScreen)

            // then run the transaction after a delay
            Handler().postDelayed(mDelayedTransaction, 2000)
        }
        findViewById<View>(R.id.button_clear).setOnClickListener {
            beginTransaction(this@MainActivity)
                .clear(R.id.fragment_container)
                .commit()
        }
        findViewById<View>(R.id.button_popup).setOnClickListener {
            beginTransaction(this@MainActivity)
                .add(PopupFragment(), PopupFragment.TAG)
                .commit()
        }
    }

    private val mDelayedTransaction = Runnable {
        beginTransaction(this@MainActivity)
            .replace(R.id.fragment_container, MainFragment(), MainFragment.TAG)
            .dontDuplicateInView()
            .addToBackStack(null)
            .runOnceAttached { log(GENERAL, "Fragment Attached") }
            .runOnceComplete { log(GENERAL, "Transaction Complete") }
            .commit()
    }
}