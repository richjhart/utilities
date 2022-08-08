package com.rjhartsoftware.fragmentsapp

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.view.View
import com.rjhartsoftware.utilities.D.GENERAL
import com.rjhartsoftware.utilities.D.log
import com.rjhartsoftware.utilities.fragments.RjhsFragmentTransactions.Companion.beginTransaction
import com.rjhartsoftware.utilities.fragments.RjhsActivityTransactions

class MainFragmentsActivity : RjhsActivityTransactions() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<View>(R.id.button_pause).setOnClickListener { // trigger an event that will pause it
            val popupScreen = Intent(this@MainFragmentsActivity, PopupActivity::class.java)
            startActivity(popupScreen)

            // then run the transaction after a delay
            Handler().postDelayed(mDelayedTransaction, 2000)
        }
        findViewById<View>(R.id.button_stop).setOnClickListener { // trigger an event that will stop it
            val fullScreen = Intent(this@MainFragmentsActivity, FullActivity::class.java)
            startActivity(fullScreen)

            // then run the transaction after a delay
            Handler().postDelayed(mDelayedTransaction, 2000)
        }
        findViewById<View>(R.id.button_clear).setOnClickListener {
            beginTransaction(this@MainFragmentsActivity)
                .clear(R.id.fragment_container)
                .commit()
        }
        findViewById<View>(R.id.button_popup).setOnClickListener {
            beginTransaction(this@MainFragmentsActivity)
                .add(PopupFragment(), PopupFragment.TAG)
                .commit()
        }
    }

    private val mDelayedTransaction = Runnable {
        beginTransaction(this@MainFragmentsActivity)
            .replace(R.id.fragment_container, MainFragment(), MainFragment.TAG)
            .dontDuplicateInView()
            .addToBackStack(null)
            .runOnceAttached { log(GENERAL, "Fragment Attached") }
            .runOnceComplete { log(GENERAL, "Transaction Complete") }
            .commit()
    }
}