package com.rjhartsoftware.utilities.fragments

import android.os.Bundle
import androidx.annotation.CallSuper
import androidx.appcompat.app.AppCompatActivity

open class RjhsActivityTransactions : AppCompatActivity() {

    @CallSuper
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RjhsFragmentTransactions.activityCreated(this)
    }

    @CallSuper
    override fun onPostResume() {
        super.onPostResume()
        RjhsFragmentTransactions.activityResumed(this)
    }

    @CallSuper
    override fun onStart() {
        super.onStart()
        RjhsFragmentTransactions.activityStarted(this)
    }

    @CallSuper
    override fun onPause() {
        RjhsFragmentTransactions.activityPaused(this)
        super.onPause()
    }

    @CallSuper
    override fun onStop() {
        RjhsFragmentTransactions.activityStopped(this)
        super.onStop()
    }

    @CallSuper
    override fun onDestroy() {
        RjhsFragmentTransactions.activityDestroyed(this)
        super.onDestroy()
    }

    @CallSuper
    override fun onSaveInstanceState(outState: Bundle) {
        RjhsFragmentTransactions.activitySaved(this)
        super.onSaveInstanceState(outState)
    }
}