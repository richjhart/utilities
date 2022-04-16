package com.rjhartsoftware.utilities

import androidx.multidex.MultiDexApplication
import com.rjhartsoftware.utilities.D.init

open class UtilitiesApp : MultiDexApplication() {
    override fun onCreate() {
        super.onCreate()
        init(this)
    }
}