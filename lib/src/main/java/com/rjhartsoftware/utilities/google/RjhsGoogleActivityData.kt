package com.rjhartsoftware.utilities.google

import android.content.SharedPreferences
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.ViewFlipper
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.preference.PreferenceManager
import com.rjhartsoftware.utilities.R
import com.rjhartsoftware.utilities.cs

const val EXTRA_ORIGINAL_INTENT: String = "_intent"

internal class RjhsGoogleActivityData : AppCompatActivity(), SharedPreferences.OnSharedPreferenceChangeListener,
    RjhsGooglePurchaseStatusChangeListener {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app = applicationContext as RjhsGoogleApplicationBase
        setContentView(R.layout.rjhs_internal_layout_activity_data)

        findViewById<Button>(R.id.google_button_data_main_manage).setOnClickListener {
            app.reportAnalytics(CONSENT_EVENT, 10)
            findViewById<ViewFlipper>(R.id.google_data_container).showNext()
        }
        findViewById<SwitchCompat>(R.id.google_switch_data_manage_analytics).setOnCheckedChangeListener { _, isChecked ->
            app.setBoolPref(getString(R.string.rjhs_fixed_settings_key_analytics), isChecked)
            app.reportAnalytics(CONSENT_EVENT, 20 + if (isChecked) 1 else 0)
            syncSummaries()
        }
        findViewById<SwitchCompat>(R.id.google_switch_data_manage_ads).setOnCheckedChangeListener { _, isChecked ->
            app.setBoolPref(getString(R.string.rjhs_fixed_settings_key_personalised), isChecked)
            app.reportAnalytics(CONSENT_EVENT, 30 + if (isChecked) 1 else 0)
            syncSummaries()
        }
        syncSummaries()

        findViewById<TextView>(R.id.google_text_data_main_message).let {
            it.text =
                cs(
                    R.string.rjhs_internal_str_consent_main_message,
                    getString(R.string.rjhs_internal_cookie_policy),
                    getString(R.string.rjhs_override_privacy_policy)
                )
            if (it.movementMethod !is LinkMovementMethod) {
                it.movementMethod = LinkMovementMethod.getInstance()
            }
        }

        findViewById<TextView>(R.id.google_text_data_manage_message).let {
            it.text =
                cs(
                    R.string.rjhs_internal_str_consent_manage_message,
                    getString(R.string.rjhs_internal_cookie_policy),
                    getString(R.string.rjhs_override_privacy_policy)
                )
            if (it.movementMethod !is LinkMovementMethod) {
                it.movementMethod = LinkMovementMethod.getInstance()
            }
        }

        findViewById<View>(R.id.google_button_data_main_ok).setOnClickListener {
            app.setBoolPref(getString(R.string.rjhs_fixed_settings_key_analytics), true)
            app.setBoolPref(getString(R.string.rjhs_fixed_settings_key_analytics), true)
            app.reportAnalytics(CONSENT_EVENT, 0)
            openOriginalIntent()
        }
        findViewById<View>(R.id.google_button_data_manage_save).setOnClickListener {
            app.reportAnalytics(CONSENT_EVENT, 1)
            openOriginalIntent()
        }

        findViewById<View>(R.id.google_button_data_manage_purchase).setOnClickListener {
            app.reportAnalytics(CONSENT_EVENT, 2)
            app.startPurchaseFromConsent(this)
        }

        findViewById<View>(R.id.google_button_data_manage_back).setOnClickListener {
            onBackPressed()
        }

        onSharedPreferenceChanged(null, null)

        PreferenceManager.getDefaultSharedPreferences(this)
            .registerOnSharedPreferenceChangeListener(this)

        purchaseStatusChanged()
        app.registerPurchaseChangeListener(this, app.handler)

    }

    private fun syncSummaries() {
        if (app.getBoolPref(getString(R.string.rjhs_fixed_settings_key_analytics))) {
            findViewById<View>(R.id.google_switch_data_manage_analytics_summary_off).visibility =
                View.INVISIBLE
            findViewById<View>(R.id.google_switch_data_manage_analytics_summary_on).visibility =
                View.VISIBLE
        } else {
            findViewById<View>(R.id.google_switch_data_manage_analytics_summary_off).visibility =
                View.VISIBLE
            findViewById<View>(R.id.google_switch_data_manage_analytics_summary_on).visibility =
                View.INVISIBLE
        }
        if (app.getBoolPref(getString(R.string.rjhs_fixed_settings_key_personalised))) {
            findViewById<View>(R.id.google_switch_data_manage_ads_summary_off).visibility =
                View.INVISIBLE
            findViewById<View>(R.id.google_switch_data_manage_ads_summary_on).visibility =
                View.VISIBLE
        } else {
            findViewById<View>(R.id.google_switch_data_manage_ads_summary_off).visibility =
                View.VISIBLE
            findViewById<View>(R.id.google_switch_data_manage_ads_summary_on).visibility =
                View.INVISIBLE
        }
    }

    private fun openOriginalIntent() {
        app.setBoolPref(getString(R.string.rjhs_fixed_settings_key_consent), true)
        startActivity(intent.getParcelableExtra(EXTRA_ORIGINAL_INTENT))
        finish()
    }

    override fun onDestroy() {
        PreferenceManager.getDefaultSharedPreferences(this)
            .unregisterOnSharedPreferenceChangeListener(this)
        app.unregisterPurchaseChangeListener(this)
        super.onDestroy()
    }

    override fun onBackPressed() {
        findViewById<ViewFlipper>(R.id.google_data_container).let {
            if (it.currentView.id == R.id.google_data_manage) {
                app.reportAnalytics(CONSENT_EVENT, 11)
                it.showPrevious()
                return
            }
        }
        app.reportAnalytics(CONSENT_EVENT, 12)
        finish()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        findViewById<SwitchCompat>(R.id.google_switch_data_manage_analytics).isChecked =
            app.getBoolPref(getString(R.string.rjhs_fixed_settings_key_analytics))
        findViewById<SwitchCompat>(R.id.google_switch_data_manage_ads).isChecked =
            app.getBoolPref(getString(R.string.rjhs_fixed_settings_key_personalised))
        syncSummaries()
    }

    override fun purchaseStatusChanged() {
        if (app.showAds) {
            findViewById<View>(R.id.google_divider_data_manage_ads).visibility = View.VISIBLE
            findViewById<View>(R.id.google_switch_data_manage_ads).visibility = View.VISIBLE
            findViewById<View>(R.id.google_switch_data_manage_ads_summary).visibility = View.VISIBLE
            app.checkIfConsentPurchaseRegistered(findViewById(R.id.google_button_data_manage_purchase))
            syncSummaries()
        } else {
            findViewById<View>(R.id.google_divider_data_manage_ads).visibility = View.GONE
            findViewById<View>(R.id.google_switch_data_manage_ads).visibility = View.GONE
            findViewById<View>(R.id.google_switch_data_manage_ads_summary).visibility = View.GONE
            findViewById<View>(R.id.google_button_data_manage_purchase).visibility = View.GONE
        }
    }
}