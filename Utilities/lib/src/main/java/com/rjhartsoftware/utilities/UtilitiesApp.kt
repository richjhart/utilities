package com.rjhartsoftware.utilities

import android.app.Application
import androidx.multidex.MultiDexApplication
import com.rjhartsoftware.utilities.D.init

class UtilitiesApp : MultiDexApplication() {
    override fun onCreate() {
        super.onCreate()
        init(this)
    }
}