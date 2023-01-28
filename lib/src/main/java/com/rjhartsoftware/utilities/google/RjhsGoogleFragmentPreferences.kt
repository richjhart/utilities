package com.rjhartsoftware.utilities.google

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.CallSuper
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.preference.*
import com.rjhartsoftware.utilities.BuildConfig
import com.rjhartsoftware.utilities.R
import com.rjhartsoftware.utilities.openUrl
import com.rjhartsoftware.utilities.popup.PopupResult
import com.rjhartsoftware.utilities.popup.RjhsFragmentMessage
import com.rjhartsoftware.utilities.utils.D
import com.takisoft.preferencex.PreferenceFragmentCompat
import com.takisoft.preferencex.SwitchPreferenceCompat
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import kotlin.system.exitProcess

private const val MESSAGE_RESET = "_reset"

open class RjhsGoogleFragmentPreferences : PreferenceFragmentCompat(),
    RjhsGooglePurchaseStatusChangeListener, SharedPreferences.OnSharedPreferenceChangeListener {

    val SETTINGS = D.DebugTag("settings")

    val googleActivity: RjhsGoogleActivityBase?
        get() {
            return activity as RjhsGoogleActivityBase
        }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        EventBus.getDefault().register(this)
    }

    override fun onDetach() {
        super.onDetach()
        EventBus.getDefault().unregister(this)
    }

    @CallSuper
    override fun onCreatePreferencesFix(bundle: Bundle?, s: String?) {
        context?.let { context ->
            PreferenceManager.getDefaultSharedPreferences(context).let { prefs ->
                prefs.edit().apply {
                    if (!prefs.contains(getString(R.string.rjhs_fixed_settings_key_analytics))) {
                        putBoolean(getString(R.string.rjhs_fixed_settings_key_analytics), false)
                    }
                    if (!prefs.contains(getString(R.string.rjhs_fixed_settings_key_personalised))) {
                        putBoolean(getString(R.string.rjhs_fixed_settings_key_personalised), false)
                    }
                    if (!prefs.contains(getString(R.string.rjhs_fixed_settings_key_external_browser))) {
                        putBoolean(
                            getString(R.string.rjhs_fixed_settings_key_external_browser),
                            false
                        )
                    }
                    if (!prefs.contains(getString(R.string.rjhs_fixed_settings_theme_key))) {
                        putString(
                            getString(R.string.rjhs_fixed_settings_theme_key),
                            getString(R.string.rjhs_internal_settings_theme_default)
                        )
                    }
                    apply()
                }
            }
            setPreferencesFromResource(R.xml.preferences, s)
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

            findPreference<SwitchPreferenceCompat>(resources.getString(R.string.rjhs_fixed_settings_key_external_browser))?.let {
                it.layoutResource = R.layout.rjhs_layout_preference
                it.setTitle(R.string.rjhs_internal_str_settings_browser_title)
                it.setSummaryOff(R.string.rjhs_internal_str_settings_browser_off)
                it.setSummaryOn(R.string.rjhs_internal_str_settings_browser_on)
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

            findPreference<Preference>(resources.getString(R.string.rjhs_fixed_settings_key_reset))?.let {
                it.setTitle(R.string.rjhs_internal_str_settings_reset_title)
                it.layoutResource = R.layout.rjhs_layout_preference
                it.setOnPreferenceClickListener {
                    RjhsFragmentMessage.Builder(MESSAGE_RESET)
                        .title(R.string.rjhs_internal_str_settings_reset_popup_title)
                        .message(R.string.rjhs_internal_str_settings_reset_popup_message)
                        .inactiveNegativeButton(R.string.rjhs_str_cancel)
                        .positiveButton(R.string.rjhs_internal_str_settings_reset_popup_ok)
                        .mustViewAll(getString(R.string.rjhs_str_more))
                        .mustAccept(getString(R.string.rjhs_internal_str_settings_reset_popup_confirm))
                        .show(activity as AppCompatActivity)
                    true
                }
            }

            checkPermission()

            app.registerPurchaseChangeListener(this, app.handler)
            PreferenceManager.getDefaultSharedPreferences(app)
                .registerOnSharedPreferenceChangeListener(this)
        }
    }

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _: Boolean ->
            checkPermission()
        }

    private fun checkPermission() {
        findPreference(R.string.rjhs_fixed_settings_key_allow_notifications)?.let {
            googleActivity?.let { activity ->
                if (activity.getNotificationState() == NotificationState.Allowed || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    it.isVisible = false
                } else {
                    it.isVisible = true
                    it.setTitle(R.string.rjhs_internal_str_allow_notification_title)
                    it.setSummary(R.string.rjhs_internal_str_allow_notification_message)
                    it.layoutResource = R.layout.rjhs_layout_preference
                    it.setOnPreferenceClickListener {
                        if (activity.getNotificationState() == NotificationState.Blocked) {
                            activity.openNotificationSettings()
                        } else {
                            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        true
                    }
                }
            }
        }

    }

    override fun onResume() {
        super.onResume()
        checkPermission()
    }

    @Subscribe
    @CallSuper
    open fun onPopupResult(result: PopupResult) {
        if (result.request == MESSAGE_RESET && result.which == AlertDialog.BUTTON_POSITIVE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                (app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).clearApplicationUserData()
            } else {
                context?.filesDir?.let { data ->
                    data.deleteRecursively()
                    data.parentFile?.listFiles()?.forEach { child ->
                        if (child.isDirectory) {
                            child.deleteRecursively()
                        }
                    }
                }
                exitProcess(0)
            }
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