package com.rjhartsoftware.utilities.google

import androidx.fragment.app.Fragment

open class RjhsGoogleFragmentBase : Fragment() {

    val googleActivity: RjhsGoogleActivityBase?
        get() {
            return activity as RjhsGoogleActivityBase
        }
}