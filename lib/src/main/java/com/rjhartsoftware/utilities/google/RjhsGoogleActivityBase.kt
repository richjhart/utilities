package com.rjhartsoftware.utilities.google

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.annotation.CallSuper
import androidx.core.content.ContextCompat
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
import com.rjhartsoftware.utilities.utils.D.log

private const val ABOUT_REQUEST_ID = "_about"
private const val TRANSLATION_REQUEST_ID = "_translation"

private enum class AdRequestState {
    None,
    Personalised,
    Anonymous
}

open class RjhsGoogleActivityBase : RjhsActivityTransactions() {

    @CallSuper
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app = applicationContext as RjhsGoogleApplicationBase
        app.registerPurchaseChangeListener(purchaseStatusChangeListener, app.handler)
        checkConsent()
    }

    @CallSuper
    override fun onDestroy() {
        app.unregisterPurchaseChangeListener(purchaseStatusChangeListener)
        unregisterAdView()
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
                log(ADS, "ad_mob_app_id: %s", getString(R.string.rjhs_override_ad_mob_app_id))
                log(ADS, "ad_mob_ad_id: %s", getString(R.string.rjhs_override_ad_mob_ad_id))
                log(ADS, "AdView id: %s", it.adUnitId)
                if (app.showAds) {
                    it.adListener = object : AdListener() {
                        override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                            super.onAdFailedToLoad(loadAdError)
                            app.reportAnalytics(AD_LOAD_EVENT, 1, loadAdError.code, loadAdError.message)
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
                            app.reportAnalytics(AD_LOAD_EVENT, 0)
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
                            app.reportAnalytics(AD_SETUP_EVENT, 1)
                            val extras = Bundle()
                            extras.putString("npa", "1")
                            builder.addNetworkExtrasBundle(AdMobAdapter::class.java, extras)
                            requestingAds = AdRequestState.Anonymous
                        } else {
                            app.reportAnalytics(AD_SETUP_EVENT, 2)
                        }
                        it.loadAd(builder.build())
                    }
                    it.resume()
                } else {
                    app.reportAnalytics(AD_SETUP_EVENT, 3)
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

    @CallSuper
    override fun setContentView(layoutResID: Int) {
        super.setContentView(layoutResID)
        findViewById<AdView>(R.id.rjhs_fixed_google_ad_view)?.let {
            registerAdView(it)
        }
    }

    internal fun registerAdView(ad: AdView) {
        log(ADS, "Adview has been registered")
        app.reportAnalytics(AD_SETUP_EVENT, 4)
        adView = ad
        updateAdVisibility()
    }

    internal fun unregisterAdView() {
        hideAds()
        app.reportAnalytics(AD_SETUP_EVENT, 5)
        adView?.destroy()
        adView = null
    }

    internal fun resumeAd() {
        if (isOld) return
        app.reportAnalytics(AD_SETUP_EVENT, 6)
        updateAdVisibility()
    }

    internal fun pauseAd() {
        if (isOld) return
        app.reportAnalytics(AD_SETUP_EVENT, 7)
        adView?.pause()
    }

    @CallSuper
    override fun onResume() {
        super.onResume()
        resumeAd()
    }

    @CallSuper
    override fun onPause() {
        pauseAd()
        super.onPause()
    }

    fun clearConsent() {
        if (isOld) return
        app.setBoolPref(getString(R.string.rjhs_fixed_settings_key_consent), false)
        checkConsent()
    }

    private fun checkConsent() {
        if (isOld) return
        log(EU_CONSENT, "Checking consent status")
        if (!app.getBoolPref(getString(R.string.rjhs_fixed_settings_key_consent))) {
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
                .title(R.string.rjhs_str_about)
                .message(
                        cs(
                                R.string.rjhs_internal_str_msg_about,
                                versionName,
                                getString(R.string.rjhs_override_str_app_name),
                                versionCode,
                                getString(R.string.rjhs_override_third_party_libraries),
                                resources.getInteger(R.integer.rjhs_override_copyright_start),
                                resources.getInteger(R.integer.rjhs_override_copyright_latest)
                        )
                )
                .inactivePositiveButton(R.string.rjhs_str_ok)
                .show(this)
    }

    fun getNotificationState(): NotificationState {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            app.setIntPref(R.string.rjhs_fixed_settings_key_asked_about_notifications, 0)
            return NotificationState.Allowed
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return NotificationState.Blocked
        }
        if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
            app.setIntPref(R.string.rjhs_fixed_settings_key_asked_about_notifications, 1)
            return NotificationState.AlreadyAsked
        }
        if (app.getIntPref(R.string.rjhs_fixed_settings_key_asked_about_notifications) == 0) {
            return NotificationState.CanAsk
        }
        app.setIntPref(R.string.rjhs_fixed_settings_key_asked_about_notifications, 2)
        return NotificationState.Blocked
    }

    fun openNotificationSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        startActivity(
                intent
        )
        //startActivity(
        //    Intent(
        //        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        //        Uri.parse("package:" + app.packageName)
        //    )
        //)
    }
}

