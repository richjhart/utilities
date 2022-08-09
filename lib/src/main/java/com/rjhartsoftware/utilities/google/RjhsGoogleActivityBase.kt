package com.rjhartsoftware.utilities.google

import android.content.Intent
import android.os.Bundle
import android.view.View
import com.google.ads.mediation.admob.AdMobAdapter
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.rjhartsoftware.utilities.R
import com.rjhartsoftware.utilities.cs
import com.rjhartsoftware.utilities.fragments.RjhsActivityTransactions
import com.rjhartsoftware.utilities.popup.RjhsFragmentMessage
import com.rjhartsoftware.utilities.utils.D
import com.rjhartsoftware.utilities.utils.D.log

private const val ABOUT_REQUEST_ID = "_about"

private enum class AdRequestState {
    None,
    Personalised,
    Anonymous
}

open class RjhsGoogleActivityBase : RjhsActivityTransactions() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app = applicationContext as RjhsGoogleApplicationBase
        app.registerPurchaseChangeListener(purchaseStatusChangeListener, app.handler)
        checkConsent()
    }

    override fun onDestroy() {
        app.unregisterPurchaseChangeListener(purchaseStatusChangeListener)
        super.onDestroy()
    }

    private var adView: AdView? = null
    private var requestingAds: AdRequestState = AdRequestState.None

    private fun updateAdVisibility() {
        if (isOld) {
            adView?.visibility = View.GONE
            return
        }
        if (GoogleApiAvailability.getInstance()
                .isGooglePlayServicesAvailable(this) == ConnectionResult.SUCCESS
        ) {
            adView?.let {
                if (app.showAds) {
                    it.adListener = object : AdListener() {
                        override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                            super.onAdFailedToLoad(loadAdError)
                            app.handler.post {
                                adView?.let { delayedIt ->
                                    log(ADS, "Ad failed to load: %s", loadAdError.toString())
                                    delayedIt.visibility = View.GONE
                                }
                            }
                        }

                        override fun onAdLoaded() {
                            super.onAdLoaded()
                            log(ADS, "Ad loaded")
                            app.handler.post {
                                adView?.let { delayedIt ->
                                    if (requestingAds != AdRequestState.None) {
                                        delayedIt.visibility = View.VISIBLE
                                    } else {
                                        hideAds()
                                    }
                                }
                            }
                        }
                    }
                    if (
                        requestingAds == AdRequestState.None ||
                                requestingAds == AdRequestState.Anonymous && !app.anonymousAds ||
                                requestingAds == AdRequestState.Personalised && app.anonymousAds
                            ) {
                        val builder = AdRequest.Builder()
                        requestingAds = AdRequestState.Personalised
                        if (app.anonymousAds) {
                            val extras = Bundle()
                            extras.putString("npa", "1")
                            builder.addNetworkExtrasBundle(AdMobAdapter::class.java, extras)
                            requestingAds = AdRequestState.Anonymous
                        }
                        it.loadAd(builder.build())
                    }
                    it.resume()
                } else {
                    hideAds()
                }
            }
        }

    }

    private fun hideAds() {
        app.handler.post {
            adView?.let {
                it.pause()
                it.visibility = View.GONE
            }
            requestingAds = AdRequestState.None
        }
    }

    private val purchaseStatusChangeListener = object : RjhsGooglePurchaseStatusChangeListener {
        override fun purchaseStatusChanged() {
            updateAdVisibility()
        }
    }

    override fun setContentView(layoutResID: Int) {
        super.setContentView(layoutResID)
        findViewById<AdView>(R.id.google_ad_view)?.let {
            adView = it
        }
    }

    internal fun registerAdView(ad: AdView) {
        log(ADS, "Adview has been registered")
        adView = ad
        updateAdVisibility()
    }

    internal fun unregisterAdView() {
        hideAds()
        adView?.destroy()
        adView = null
    }

    fun resumeAd() {
        if (isOld) return
        updateAdVisibility()
    }

    fun pauseAd() {
        if (isOld) return
        adView?.pause()
    }

    override fun onResume() {
        super.onResume()
        resumeAd()
    }

    override fun onPause() {
        pauseAd()
        super.onPause()
    }

    fun clearConsent() {
        if (isOld) return
        app.setBoolPref(getString(R.string.settings_key_consent), false)
        checkConsent()
    }

    private fun checkConsent() {
        if (isOld) return
        log(EU_CONSENT, "Checking consent status")
        if (!app.getBoolPref(getString(R.string.settings_key_consent))) {
            log(EU_CONSENT, "Consent has not been granted")
            val openThis = Intent(this, this::class.java)
            startActivity(
                Intent(this, RjhsGoogleActivityData::class.java).putExtra(
                    EXTRA_ORIGINAL_INTENT, openThis
                )
            )
            finish()
        }
    }

    fun showAbout(versionName: String, versionCode: Int) {
        RjhsFragmentMessage.Builder(ABOUT_REQUEST_ID)
            .title(R.string.general_about)
            .message(
                cs(
                    R.string.about_message,
                    versionName,
                    getString(R.string.app_name),
                    versionCode,
                    getString(R.string.third_party_libraries),
                    resources.getInteger(R.integer.copyright_start),
                    resources.getInteger(R.integer.copyright_latest)
                )
            )
            .inactivePositiveButton(R.string.general_ok_1)
            .show(this)
    }

}

