package com.rjhartsoftware.utilities

import androidx.annotation.CallSuper
import androidx.multidex.MultiDexApplication
import com.rjhartsoftware.utilities.D.init

open class RjhsBaseApp : MultiDexApplication() {

    @CallSuper
    override fun onCreate() {
        super.onCreate()
        init(this)
    }
}