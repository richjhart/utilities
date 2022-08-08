package com.rjhartsoftware.utilities.google

import androidx.fragment.app.Fragment

open class RjhsGoogleFragmentBase : Fragment() {

    // TODO prevent adding fragments that don't extend this to activities
    val googleActivity: RjhsGoogleActivityBase?
        get() {
            return activity as RjhsGoogleActivityBase
        }
}