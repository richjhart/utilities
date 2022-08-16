package com.rjhartsoftware.utilities.google

import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import androidx.annotation.CallSuper
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.*
import com.rjhartsoftware.utilities.R
import com.rjhartsoftware.utilities.openUrl
import com.takisoft.preferencex.PreferenceFragmentCompat
import com.takisoft.preferencex.SwitchPreferenceCompat

open class RjhsGoogleFragmentPreferences : PreferenceFragmentCompat(),
    RjhsGooglePurchaseStatusChangeListener, SharedPreferences.OnSharedPreferenceChangeListener {

    @CallSuper
    override fun onCreatePreferencesFix(bundle: Bundle?, s: String?) {
        setPreferencesFromResource(R.xml.preferences, s)
        context?.let {

            findPreference<ListPreference>(resources.getString(R.string.rjhs_fixed_settings_theme_key))?.let {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2) {
                    it.isVisible = false
                    it.parent?.let { group ->
                        if (group.children.count() == 1) {
                            group.isVisible = false
                        }
                    }
                } else {
                    it.setDefaultValue(getString(R.string.rjhs_internal_settings_theme_default))
                    it.setDialogTitle(R.string.rjhs_internal_str_settings_theme_title)
                    it.setEntries(R.array.rjhs_internal_settings_theme_entries_text)
                    it.setEntryValues(R.array.rjhs_internal_settings_theme_values)
                    it.layoutResource = R.layout.rjhs_layout_preference
                    it.summary = "%s"
                    it.setTitle(R.string.rjhs_internal_str_settings_theme_title)
                }
            }

            findPreference<PreferenceCategory>(
                resources.getString(R.string.rjhs_fixed_settings_key_google)
            )?.let { category ->
                category.layoutResource = R.layout.rjhs_layout_preference_category
                category.setTitle(R.string.rjhs_internal_str_settings_title_ads)

                category.forEach { preference ->
                    if (app.isPurchaseRegistered(preference.key)) {
                        app.initPurchasePreference(preference)
                        preference.setOnPreferenceClickListener {
                            activity?.let { act -> app.startPurchase(preference.key, act) }
                            true
                        }
                    }
                }
            }

            findPreference<SwitchPreferenceCompat>(resources.getString(R.string.rjhs_fixed_settings_key_personalised))?.let {
                it.layoutResource = R.layout.rjhs_layout_preference
                it.setTitle(R.string.rjhs_internal_str_settings_personalised_title)
                it.setSummaryOff(R.string.rjhs_internal_str_settings_personalised_summary_off)
                it.setSummaryOn(R.string.rjhs_internal_str_settings_personalised_summary_on)
            }

            findPreference<SwitchPreferenceCompat>(resources.getString(R.string.rjhs_fixed_settings_key_analytics))?.let {
                if (isOld) {
                    it.isVisible = false
                } else {
                    it.layoutResource = R.layout.rjhs_layout_preference
                    it.setTitle(R.string.rjhs_internal_str_settings_analytics_title)
                    it.setSummaryOff(R.string.rjhs_internal_str_settings_anaytics_summary_off)
                    it.setSummaryOn(R.string.rjhs_internal_str_settings_analytics_summary_on)
                }
            }

            updatePurchasePrefs()

            findPreference<Preference>(resources.getString(R.string.rjhs_fixed_settings_key_cookie))?.let {
                it.setTitle(R.string.rjhs_internal_str_settings_support_cookie)
                it.layoutResource = R.layout.rjhs_layout_preference
                it.setOnPreferenceClickListener {
                    openUrl(
                        activity as AppCompatActivity?,
                        getString(R.string.rjhs_internal_cookie_policy)
                    )
                    true
                }
            }
            findPreference<Preference>(resources.getString(R.string.rjhs_fixed_settings_key_privacy))?.let {
                it.setTitle(R.string.rjhs_internal_str_settings_support_privacy)
                it.layoutResource = R.layout.rjhs_layout_preference
                it.setOnPreferenceClickListener {
                    openUrl(
                        activity as AppCompatActivity?,
                        getString(R.string.rjhs_override_privacy_policy)
                    )
                    true
                }
            }
            findPreference<Preference>(resources.getString(R.string.rjhs_fixed_settings_key_support))?.let {
                it.setTitle(R.string.rjhs_internal_str_settings_support_title)
                it.layoutResource = R.layout.rjhs_layout_preference
                it.setOnPreferenceClickListener {
                    openUrl(activity as AppCompatActivity?, URL_SUPPORT)
                    true
                }
            }

            app.registerPurchaseChangeListener(this, app.handler)
            PreferenceManager.getDefaultSharedPreferences(app)
                .registerOnSharedPreferenceChangeListener(this)
        }
    }

    private fun updatePurchasePrefs() {
        findPreference<PreferenceCategory>(
            resources.getString(R.string.rjhs_fixed_settings_key_google)
        )?.let { category ->
            category.forEach { preference ->
                if (app.isPurchaseRegistered(preference.key)) {
                    app.updatePurchasePreference(preference)
                }
            }
        }

        findPreference<Preference>(resources.getString(R.string.rjhs_fixed_settings_key_personalised))?.let {
            it.isVisible = app.showAds
        }
    }

    @CallSuper
    override fun onDestroyView() {
        app.unregisterPurchaseChangeListener(this)
        PreferenceManager.getDefaultSharedPreferences(app)
            .unregisterOnSharedPreferenceChangeListener(this)
        super.onDestroyView()
    }

    fun findPreference(@StringRes key: Int): Preference? {
        return findPreference(getString(key))
    }

    companion object {
        const val TAG = "_frag_settings"
    }

    @CallSuper
    override fun purchaseStatusChanged() {
        updatePurchasePrefs()
    }

    @CallSuper
    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        updatePurchasePrefs()
    }
}

private const val URL_SUPPORT = "https://buymeacoffee.com/rjhartsoftware"