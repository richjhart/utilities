package com.rjhartsoftware.fragments

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

open class TransactionsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FragmentTransactions.activityCreated(this)
    }

    override fun onPostResume() {
        super.onPostResume()
        FragmentTransactions.activityResumed(this)
    }

    override fun onStart() {
        super.onStart()
        FragmentTransactions.activityStarted(this)
    }

    override fun onPause() {
        FragmentTransactions.activityPaused(this)
        super.onPause()
    }

    override fun onStop() {
        FragmentTransactions.activityStopped(this)
        super.onStop()
    }

    override fun onDestroy() {
        FragmentTransactions.activityDestroyed(this)
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        FragmentTransactions.activitySaved(this)
        super.onSaveInstanceState(outState)
    }
}