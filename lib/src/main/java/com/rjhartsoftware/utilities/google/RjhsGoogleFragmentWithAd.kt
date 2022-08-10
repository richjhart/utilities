package com.rjhartsoftware.utilities.google

import android.os.Bundle
import android.view.View
import androidx.annotation.CallSuper
import androidx.fragment.app.Fragment
import com.google.android.gms.ads.AdView
import com.rjhartsoftware.utilities.R
import com.rjhartsoftware.utilities.utils.D

open class RjhsGoogleFragmentWithAd : RjhsGoogleFragmentBase() {

    private var adViewRegistered = false

    @CallSuper
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<AdView>(R.id.rjhs_fixed_google_ad_view)?.let { adView ->
            activity?.let { act ->
                if (act is RjhsGoogleActivityBase) {
                    act.registerAdView(adView)
                    adViewRegistered = true
                } else {
                    D.error(ADS, "activity is wrong class: %s", act::class.java.name)
                }
            } ?: run {
                D.error(ADS, "Activity is null (this should never happen)")
            }
        } ?: run {
            D.error(ADS, "AdView is not found in the view")
        }
    }

    @CallSuper
    override fun onResume() {
        super.onResume()
        if (adViewRegistered) {
            activity?.let { act ->
                if (act is RjhsGoogleActivityBase) {
                    act.resumeAd()
                }
            }
        }
    }

    @CallSuper
    override fun onPause() {
        if (adViewRegistered) {
            activity?.let { act ->
                if (act is RjhsGoogleActivityBase) {
                    act.resumeAd()
                }
            }
        }
        super.onPause()
    }

    @CallSuper
    override fun onDestroyView() {
        if (adViewRegistered) {
            activity?.let { act ->
                if (act is RjhsGoogleActivityBase) {
                    act.unregisterAdView()
                }
            }
        }
        super.onDestroyView()
    }
}