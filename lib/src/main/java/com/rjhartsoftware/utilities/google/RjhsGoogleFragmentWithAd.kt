package com.rjhartsoftware.utilities.google

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.google.android.gms.ads.AdView
import com.rjhartsoftware.utilities.R

open class RjhsGoogleFragmentWithAd : RjhsGoogleFragmentBase() {

    private var adViewRegistered = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<AdView>(R.id.google_ad_view)?.let { adView ->
            activity?.let { act ->
                if (act is RjhsGoogleActivityBase) {
                    act.registerAdView(adView)
                    adViewRegistered = true
                }
            }
        }
    }

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