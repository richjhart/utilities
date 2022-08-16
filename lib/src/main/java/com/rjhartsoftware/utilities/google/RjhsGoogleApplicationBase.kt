package com.rjhartsoftware.utilities.google

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application.getProcessName
import android.content.*
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.net.ConnectivityManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.View
import android.widget.Button
import androidx.annotation.CallSuper
import androidx.annotation.IntDef
import androidx.annotation.XmlRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.multidex.MultiDexApplication
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import com.android.billingclient.api.*
import com.google.android.gms.ads.MobileAds
import com.google.common.collect.ImmutableMap
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.rjhartsoftware.utilities.R
import com.rjhartsoftware.utilities.utils.D
import com.rjhartsoftware.utilities.utils.D.error
import com.rjhartsoftware.utilities.utils.D.log
import com.rjhartsoftware.utilities.utils.D.warn
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.*
import kotlin.experimental.xor

val EU_CONSENT: D.DebugTag = D.DebugTag("google_consent")
val ADS: D.DebugTag = D.DebugTag("google_ads")
val ANALYTICS: D.DebugTag = D.DebugTag("google_analytics")
val BILLING: D.DebugTag = D.DebugTag("google_billing")
val FIRESTORE: D.DebugTag = D.DebugTag("google_firestore")
private val REMOTE_CONFIG: D.DebugTag = D.DebugTag("google_remote")

private const val SETTINGS_KEY_PURCHASE = "_g_ps."

val isOld: Boolean
    get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT
lateinit var app: RjhsGoogleApplicationBase

// Status
const val PURCHASE_ENABLED = 0
const val PURCHASE_DISABLED = 1
const val PURCHASE_NOT_YET_KNOWN = 2
const val PURCHASE_PENDING = 3

// Flags
private const val PURCHASE_FLAG_BENEFIT_OF_DOUBT = 1
private const val PURCHASE_FLAG_CARE_ABOUT_WAITING = 1 shl 1
private const val PURCHASE_FLAG_CARE_ABOUT_PENDING = 1 shl 2

@IntDef(value = [PURCHASE_ENABLED, PURCHASE_DISABLED, PURCHASE_NOT_YET_KNOWN, PURCHASE_PENDING])
@kotlin.annotation.Retention
annotation class PurchaseStatusFull

@IntDef(value = [PURCHASE_ENABLED, PURCHASE_DISABLED])
@kotlin.annotation.Retention
annotation class PurchaseStatus

@IntDef(
    flag = true,
    value = [PURCHASE_FLAG_BENEFIT_OF_DOUBT, PURCHASE_FLAG_CARE_ABOUT_WAITING, PURCHASE_FLAG_CARE_ABOUT_PENDING]
)
@kotlin.annotation.Retention
private annotation class PurchaseRequestFlags

private class State {
    lateinit var billingKey: String
    val hasBillingKey: Boolean
        get() {
            return billingKey.isNotEmpty()
        }
    val purchaseRegistered: Boolean
        get() {
            return hasBillingKey && purchaseInfo.isNotEmpty()
        }

    val purchaseInfo: MutableMap<String, PurchaseInfo> = HashMap()
    val adRegistered: Boolean
        get() {
            return purchaseInfo.filter { it.value.removesAds }.isNotEmpty()
        }

    var billingClient: BillingClient? = null
    var serviceConnected = false
    var serviceConnecting = false

    val purchaseChangeListeners: MutableList<PurchaseStatusChangeListenerHolder> = ArrayList()
}

private class PurchaseInfo(val key: String, vararg val otherKeys: String) {
    var consentPurchase = false
    var removesAds = false

    var status: InternalPurchaseStatus = InternalPurchaseStatus.Init
    var priceString: String? = null
    var skuDetails: ProductDetails? = null
    var token: String? = null
    var simulated = false
}

internal enum class InternalPurchaseStatus {
    Nothing,
    Init,
    Off,
    Unknown,
    On,
    Pending,
    NoneRegistered
}

interface RjhsGooglePurchaseStatusChangeListener {
    fun purchaseStatusChanged()
}

private class PurchaseStatusChangeListenerHolder(
    val listener: RjhsGooglePurchaseStatusChangeListener,
    val handler: Handler
)

open class RjhsGoogleApplicationBase : MultiDexApplication() {

    private val state: State = State()
    internal val handler: Handler
        get() {
            return Handler(Looper.getMainLooper())
        }

    fun getIntPref(key: String): Int = getIntPref(key, 0)
    fun getIntPref(key: String, default: Int): Int =
        PreferenceManager.getDefaultSharedPreferences(this).getInt(key, default)

    fun setIntPref(key: String, value: Int) =
        PreferenceManager.getDefaultSharedPreferences(this).edit().putInt(key, value).apply()

    fun getBoolPref(key: String): Boolean = getBoolPref(key, false)
    fun getBoolPref(key: String, default: Boolean): Boolean =
        PreferenceManager.getDefaultSharedPreferences(this).getBoolean(key, default)

    fun setBoolPref(key: String, value: Boolean) =
        PreferenceManager.getDefaultSharedPreferences(this).edit().putBoolean(key, value).apply()

    private val networkReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // check if the new internet connection allows us to query purchases
            start()
        }
    }

    private lateinit var remoteConfig: FirebaseRemoteConfig

    fun initRemoteConfig(@XmlRes initRes: Int) {
        remoteConfig = FirebaseRemoteConfig.getInstance()
        val configSettings = FirebaseRemoteConfigSettings.Builder()
            .setMinimumFetchIntervalInSeconds(3600)
            .build()
        remoteConfig.setDefaultsAsync(initRes)
        remoteConfig.setConfigSettingsAsync(configSettings)
        remoteConfig.fetchAndActivate()
    }

    fun getRemoteLong(key: String?): Long {
        val res = remoteConfig.getLong(key!!)
        log(REMOTE_CONFIG, "Value of %s: %d", key, res) //NON-NLS
        return res
    }

    fun getRemoteString(key: String?): String {
        val res = remoteConfig.getString(key!!)
        log(REMOTE_CONFIG, "Value of %s: %s", key, res) //NON-NLS
        return res
    }

    fun getRemoteBoolean(key: String?): Boolean {
        val res = remoteConfig.getBoolean(key!!)
        log(REMOTE_CONFIG, "Value of %s: %b", key, res) //NON-NLS
        return res
    }

    fun getRemoteDouble(key: String?): Double {
        val res = remoteConfig.getDouble(key!!)
        log(REMOTE_CONFIG, "Value of %s: %f", key, res) //NON-NLS
        return res
    }

    fun start() {
        if (isOld) {
            state.purchaseInfo.forEach { item ->
                setPurchaseStatus(
                    item.key,
                    InternalPurchaseStatus.On
                )
            }
            return
        }
        if (state.purchaseRegistered) {
            if (state.billingClient == null) {
                state.billingClient = BillingClient.newBuilder(this)
                    .enablePendingPurchases()
                    .setListener(purchaseUpdated)
                    .build()
            }
            initialiseBilling()
        }
        state.purchaseInfo.filter { it.value.status == InternalPurchaseStatus.Init }.forEach {
            val newStatus = InternalPurchaseStatus.values()[getIntPref(
                SETTINGS_KEY_PURCHASE + it.key.hashCode(),
                InternalPurchaseStatus.Nothing.ordinal
            )]
            log(
                BILLING,
                "Startup - initialising purchase status of %s to %s",
                it.key,
                newStatus.name
            )
            setPurchaseStatus(it.key, newStatus)
        }
    }

    private fun setPurchaseStatus(key: String, status: InternalPurchaseStatus) {
        state.purchaseInfo[key]?.let { info ->
            if (status != info.status) {
                log(
                    BILLING,
                    "Updating purchase status for %s from %s to %s",
                    key,
                    info.status.name,
                    status.name
                )
                info.status = status
                setIntPref(SETTINGS_KEY_PURCHASE + key.hashCode(), status.ordinal)
                purchasesStatusChanged()
                if (status == InternalPurchaseStatus.Pending) {
                    maybeStartDelayedQuery(0)
                }
            }
        }
    }

    private fun maybeStartDelayedQuery(callCount: Int) {
        if (state.purchaseInfo
                .filter { it.value.status == InternalPurchaseStatus.Pending }
                .isNotEmpty()
        ) {
            handler.postDelayed(
                {
                    queryPurchases()
                    maybeStartDelayedQuery(callCount + 1)
                },
                when (callCount) {
                    0 -> 1 * 60000
                    1 -> 5 * 60000
                    2 -> 10 * 60000
                    else -> 60 * 60000
                }
            )
        }
    }

    @CallSuper
    override fun onCreate() {
        updateTheme()
        super.onCreate()
        D.init(this)
        if (!D.isDebug()) {
            FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true)
        }
        if (!processName.contains(":")) {
            app = this
            initRemoteConfig(R.xml.remote_config_defaults)
            registerReceiver(networkReceiver, IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION))
            state.billingKey = getString(R.string.rjhs_override_billing_public_key)
            if (state.hasBillingKey) {
                if (resources.getBoolean(R.bool.rjhs_public_google_auto_setup)) {
                    addPurchaseInfo(getString(R.string.rjhs_override_sku_remove_ads))
                    setRemovesAds(getString(R.string.rjhs_override_sku_remove_ads))
                    start()
                }
            }
            sharedPreferencesListener.onSharedPreferenceChanged(null, null)
            PreferenceManager.getDefaultSharedPreferences(this)
                .registerOnSharedPreferenceChangeListener(sharedPreferencesListener)
        }
    }

    private fun updateTheme() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(this)
            val theme = prefs.getString(
                getString(R.string.rjhs_fixed_settings_theme_key),
                getString(R.string.rjhs_internal_settings_theme_default)
            )
            if ("light" == theme) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            } else if ("dark" == theme) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                } else {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY)
                }
            }
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
    }

    private val sharedPreferencesListener =
        OnSharedPreferenceChangeListener { _: SharedPreferences?, key: String? ->
            if (getString(R.string.rjhs_fixed_settings_theme_key) == key) {
                updateTheme()
            }
        }

    fun addPurchaseInfo(key: String, vararg otherKeys: String): RjhsGoogleApplicationBase {
        state.purchaseInfo[key] = PurchaseInfo(key, *otherKeys)
        return this
    }

    fun setRemovesAds(key: String): RjhsGoogleApplicationBase {
        state.purchaseInfo[key]?.let {
            it.removesAds = true
            it.consentPurchase = true
            for (otherKey in it.otherKeys) {
                state.purchaseInfo[otherKey]?.let { other ->
                    other.removesAds = true
                }
            }
        }
        if (isOld) {
            return this
        }
        MobileAds.initialize(this) {
            log(ADS, "Ad initialisation complete")
        }
        return this
    }

    private val purchaseUpdated = PurchasesUpdatedListener { result, purchases ->
        handler.post {
            logBillingResult(result)
            when (result.responseCode) {
                BillingClient.BillingResponseCode.OK -> {
                    log(BILLING, "Purchase updated - success. Need to verify signature")
                    reportAnalytics(BILLING_EVENT_PURCHASE_UPDATED, 0)
                    purchases?.forEach { purchase ->
                        purchase.products.forEach { key ->
                            state.purchaseInfo[key]?.let {
                                verifyValidSignature(key, purchase)
                            }
                        }
                    }
                }
                BillingClient.BillingResponseCode.USER_CANCELED -> {
                    log(BILLING, "User cancelled the purchase flow")
                    reportAnalytics(BILLING_EVENT_PURCHASE_UPDATED, 1)
                }
                BillingClient.BillingResponseCode.ERROR -> {
                    log(BILLING, "Billing error")
                    reportAnalytics(BILLING_EVENT_PURCHASE_UPDATED, 2, 0, result.debugMessage)
                }
                BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                    log(BILLING, "Item already owned - re-check")
                    reportAnalytics(BILLING_EVENT_PURCHASE_UPDATED, 3)
                    queryPurchases()
                }
                else -> {
                    reportAnalytics(
                        BILLING_EVENT_PURCHASE_UPDATED, 4,
                        result.responseCode,
                        result.debugMessage
                    )
                }
            }
        }
    }

    fun registerPurchaseChangeListener(
        listener: RjhsGooglePurchaseStatusChangeListener,
        handler: Handler
    ) {
        if (state.purchaseChangeListeners.any { it.listener == listener }) return
        state.purchaseChangeListeners.add(PurchaseStatusChangeListenerHolder(listener, handler))
    }

    fun unregisterPurchaseChangeListener(listener: RjhsGooglePurchaseStatusChangeListener) {
        state.purchaseChangeListeners
            .filter { it.listener == listener }
            .forEach { state.purchaseChangeListeners.remove(it) }
    }

    fun startPurchaseFromConsent(activity: Activity) {
        if (isOld) return
        state.purchaseInfo
            .filter { it.value.consentPurchase }
            .values
            .firstOrNull()?.let {
                startPurchase(it.key, activity)
            }
    }

    fun checkIfConsentPurchaseRegistered(button: Button) {
        if (isOld) button.visibility = View.GONE
        if (state.purchaseRegistered) {
            state.purchaseInfo.filter { it.value.consentPurchase }.values.firstOrNull()?.let {
                button.visibility = View.VISIBLE
                button.isEnabled = true
                when {
                    getPurchaseStatusPending(it.key) == PURCHASE_PENDING -> {
                        app.reportAnalytics(CONSENT_EVENT, 41)
                        button.setText(R.string.rjhs_internal_str_consent_manage_purchase_pending)
                        button.isEnabled = false
                    }
                    it.priceString.isNullOrBlank() -> button.setText(R.string.rjhs_internal_str_consent_manage_purchase_no_price)
                    else -> button.text =
                        getString(
                            R.string.rjhs_internal_str_consent_manage_purchase_price,
                            it.priceString
                        )
                }
                return
            }
        }
        app.reportAnalytics(CONSENT_EVENT, 40)
        button.visibility = View.GONE
    }

    fun startPurchase(key: String, activity: Activity) {
        if (isOld) return
        state.purchaseInfo[key]?.let { purchaseInfo ->
            log(BILLING, "Starting purchase flow")
            purchaseInfo.skuDetails?.let { skuDetails ->
                state.billingClient?.let { billingClient ->
                    executeServiceRequest {
                        val list: MutableList<BillingFlowParams.ProductDetailsParams> = ArrayList()
                        list.add(
                            BillingFlowParams.ProductDetailsParams.newBuilder()
                                .setProductDetails(skuDetails).build()
                        )
                        val purchaseParams =
                            BillingFlowParams.newBuilder().setProductDetailsParamsList(list).build()
                        val result = billingClient.launchBillingFlow(activity, purchaseParams)
                        logBillingResult(result)
                        when (result.responseCode) {
                            BillingClient.BillingResponseCode.OK -> {
                                reportAnalytics(BILLING_EVENT_PURCHASE_STARTED, 0)
                                log(BILLING, "Purchase flow started successfully")
                            }
                            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                                log(BILLING, "Item already owned - checking")
                                reportAnalytics(BILLING_EVENT_PURCHASE_STARTED, 1)
                                queryPurchases()
                            }
                            else -> {
                                reportAnalytics(
                                    BILLING_EVENT_PURCHASE_STARTED, 2,
                                    result.responseCode,
                                    result.debugMessage
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    fun simulatePurchase(key: String, purchased: Boolean) {
        if (isOld) return
        state.purchaseInfo[key]?.let {
            it.simulated = true
            setPurchaseStatus(
                key,
                if (purchased) InternalPurchaseStatus.On else InternalPurchaseStatus.Off
            )
        }
    }

    fun consumePurchase(key: String) {
        if (isOld) return
        state.purchaseInfo[key]?.let {
            if (it.simulated) {
                it.simulated = false
                setPurchaseStatus(key, InternalPurchaseStatus.Off)
                return
            }
            it.token?.let { token ->
                log(BILLING, "Attempting to consume purchase %s", key)
                executeServiceRequest {
                    state.billingClient?.consumeAsync(
                        ConsumeParams.newBuilder()
                            .setPurchaseToken(token)
                            .build()
                    ) { response, _ ->
                        handler.post {
                            logBillingResult(response)
                            if (response.responseCode == BillingClient.BillingResponseCode.OK ||
                                response.responseCode == BillingClient.BillingResponseCode.ITEM_NOT_OWNED
                            ) {
                                setPurchaseStatus(key, InternalPurchaseStatus.Off)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun purchasesStatusChanged() {
        state.purchaseChangeListeners.forEach {
            it.handler.post {
                try {
                    it.listener.purchaseStatusChanged()
                } catch (t: Throwable) {
                    error(BILLING, "Error calling app listener", t)
                }
            }
        }
    }

    private fun initialiseBilling() {
        if (isOld) {
            return
        }
        if (state.serviceConnecting) {
            log(BILLING, "Already connecting")
            return
        }
        if (state.serviceConnected) {
            log(BILLING, "Already connected. No further action needed")
            return
        }
        if (state.purchaseRegistered) {
            state.serviceConnecting = true
            log(BILLING, "Initialise billing")
            startServiceConnection { // IAB is fully set up. Now, let's get an inventory of stuff we own.
                log(BILLING, "Setup successful. Querying inventory.") //NON-NLS
                queryPurchases()
                queryProducts()
            }
        }
    }


    private fun logBillingResult(result: BillingResult) {
        log(
            BILLING.indirect(),
            "Result: %s(%d) (%s)",
            getBillingResponseString(result.responseCode),
            result.responseCode,
            result.debugMessage
        )
    }

    private fun getBillingResponseString(@BillingClient.BillingResponseCode billingResponseCode: Int): String {
        when (billingResponseCode) {
            BillingClient.BillingResponseCode.BILLING_UNAVAILABLE -> return "Billing Unavailable"
            BillingClient.BillingResponseCode.DEVELOPER_ERROR -> return "Developer Error"
            BillingClient.BillingResponseCode.ERROR -> return "Error"
            BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED -> return "Feature Not Supported"
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> return "Item Already Owned"
            BillingClient.BillingResponseCode.ITEM_NOT_OWNED -> return "Item Not Owned"
            BillingClient.BillingResponseCode.ITEM_UNAVAILABLE -> return "Item Unavailable"
            BillingClient.BillingResponseCode.OK -> return "OK"
            BillingClient.BillingResponseCode.SERVICE_DISCONNECTED -> return "Service Disconnected"
            BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE -> return "Service Unavailable"
            BillingClient.BillingResponseCode.USER_CANCELED -> return "User Cancelled"
        }
        return "Unknown"
    }

    private fun executeServiceRequest(runnable: Runnable) {
        if (isOld) return
        if (state.purchaseRegistered) {
            log(BILLING, "Requesting service request (Online action)")
            if (state.serviceConnected) {
                runnable.run()
            } else {
                log(BILLING, "Not ready to run - restarting service connection")
                startServiceConnection(runnable)
            }
        }
    }

    private fun startServiceConnection(executeOnSuccess: Runnable) {
        if (isOld) return
        if (state.purchaseRegistered) {
            state.billingClient?.startConnection(
                object : BillingClientStateListener {
                    override fun onBillingSetupFinished(billingResult: BillingResult) {
                        handler.post {
                            state.serviceConnecting = false
                            logBillingResult(billingResult)
                            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                                log(BILLING, "Billing setup worked. Continue with requested action")
                                state.serviceConnected = true
                                reportAnalytics(BILLING_EVENT_SETUP, 0)
                                executeOnSuccess.run()
                            } else {
                                log(BILLING, "Billing setup failed. Set purchase status to unknown")
                                state.purchaseInfo
                                    .filter { it.value.status == InternalPurchaseStatus.Init || it.value.status == InternalPurchaseStatus.Nothing }
                                    .forEach {
                                        setPurchaseStatus(
                                            it.key,
                                            InternalPurchaseStatus.Unknown
                                        )
                                    }
                                reportAnalytics(
                                    BILLING_EVENT_SETUP, 1,
                                    billingResult.responseCode,
                                    billingResult.debugMessage
                                )
                            }
                        }
                    }

                    override fun onBillingServiceDisconnected() {
                        handler.post {
                            log(BILLING, "Billing service disconnected")
                            state.serviceConnected = false
                            state.purchaseInfo
                                .filter { it.value.status == InternalPurchaseStatus.Init || it.value.status == InternalPurchaseStatus.Nothing }
                                .forEach {
                                    setPurchaseStatus(
                                        it.key,
                                        InternalPurchaseStatus.Unknown
                                    )
                                }
                            reportAnalytics(BILLING_EVENT_SERVICE_DISCONNECTED, 1)
                        }
                    }
                }
            )
        }
    }

    private fun queryPurchases() {
        if (isOld) return

        if (state.purchaseRegistered) {
            log(BILLING, "Querying purchases")
            executeServiceRequest {
                log(BILLING, "Running purchases query")
                state.billingClient?.queryPurchasesAsync(
                    QueryPurchasesParams.newBuilder()
                        .setProductType(BillingClient.ProductType.INAPP).build()
                ) { billingResult, list ->
                    handler.post {
                        logBillingResult(billingResult)
                        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                            reportAnalytics(BILLING_EVENT_QUERY, 0)
                            val all: MutableList<String> = ArrayList()
                            all.addAll(state.purchaseInfo.keys)
                            list.forEach {
                                it.products.forEach { key ->
                                    state.purchaseInfo[key]?.let { _ ->
                                        all.remove(key)
                                        if (it.purchaseState == Purchase.PurchaseState.PURCHASED) {
                                            verifyValidSignature(key, it)
                                        } else if (it.purchaseState == Purchase.PurchaseState.PENDING) {
                                            setPurchaseStatus(key, InternalPurchaseStatus.Pending)
                                        }
                                    }
                                }
                            }
                            all.forEach { setPurchaseStatus(it, InternalPurchaseStatus.Off) }
                        } else {
                            state.purchaseInfo.forEach {
                                it.value.status = InternalPurchaseStatus.Unknown
                            }
                            reportAnalytics(
                                BILLING_EVENT_QUERY, 1,
                                billingResult.responseCode,
                                billingResult.debugMessage
                            )
                        }
                        purchasesStatusChanged()
                    }
                }
            }
        }
    }

    private fun queryProducts() {
        if (isOld) return

        if (state.purchaseRegistered) {
            log(BILLING, "Get product details")
            executeServiceRequest {
                log(BILLING, "Running product query")
                val skus: MutableList<QueryProductDetailsParams.Product> = ArrayList()
                state.purchaseInfo.forEach {
                    skus.add(
                        QueryProductDetailsParams.Product.newBuilder()
                            .setProductId(it.key)
                            .setProductType(BillingClient.ProductType.INAPP)
                            .build()
                    )
                }
                val params = QueryProductDetailsParams.newBuilder()
                    .setProductList(skus)
                    .build()

                state.billingClient?.queryProductDetailsAsync(params) { billingResult, skuDetailsList ->
                    handler.post {
                        logBillingResult(billingResult)
                        val list = skuDetailsList.filter { it.oneTimePurchaseOfferDetails != null }
                        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK
                            && list.isNotEmpty()
                        ) {
                            reportAnalytics(BILLING_EVENT_PRODUCTS, 0)
                            list.forEach {
                                log(
                                    BILLING, "Price for %s: %d %s",
                                    it.productId,
                                    it.oneTimePurchaseOfferDetails!!.priceAmountMicros,
                                    it.oneTimePurchaseOfferDetails!!.priceCurrencyCode
                                )
                                state.purchaseInfo[it.productId]?.let { purchaseInfo ->
                                    purchaseInfo.priceString =
                                        it.oneTimePurchaseOfferDetails!!.formattedPrice
                                    purchaseInfo.skuDetails = it
                                }
                            }
                            purchasesStatusChanged()

                        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                            log(BILLING, "No purchase types found") //NON-NLS
                            reportAnalytics(BILLING_EVENT_PRODUCTS, 1)
                        } else {
                            reportAnalytics(
                                BILLING_EVENT_PRODUCTS, 2,
                                billingResult.responseCode,
                                billingResult.debugMessage
                            )
                        }
                    }
                }
            }
        }
    }

    private fun verifyValidSignature(key: String, purchase: Purchase): Boolean {
        if (isOld) return false

        val signedData = purchase.originalJson
        val signature = purchase.signature

        state.purchaseInfo[key]?.let {
            if (signedData.isEmpty()) {
                log(BILLING, "Signed data is empty")
                reportAnalytics(BILLING_EVENT_SIGNATURE, 3)
                setPurchaseStatus(key, InternalPurchaseStatus.Off)
                return false
            }
            if (signature.isEmpty()) {
                reportAnalytics(BILLING_EVENT_SIGNATURE, 4)
                log(BILLING, "Signature is empty")
                setPurchaseStatus(key, InternalPurchaseStatus.Off)
                return false
            }
            generatePublicKey(decode(state.billingKey))?.let { publicKey ->
                if (verify(publicKey, signedData, signature)) {
                    log(BILLING, "Signature is valid")
                    it.token = purchase.purchaseToken
                    when (purchase.purchaseState) {
                        Purchase.PurchaseState.PURCHASED -> {
                            reportAnalytics(BILLING_EVENT_SIGNATURE, 0)
                            setPurchaseStatus(key, InternalPurchaseStatus.On)
                            if (!purchase.isAcknowledged) {
                                acknowledgePurchase(purchase)
                            }
                        }
                        Purchase.PurchaseState.PENDING -> {
                            reportAnalytics(BILLING_EVENT_SIGNATURE, 1)
                            setPurchaseStatus(
                                key,
                                InternalPurchaseStatus.Pending
                            )
                        }
                        Purchase.PurchaseState.UNSPECIFIED_STATE -> {
                            reportAnalytics(BILLING_EVENT_SIGNATURE, 2)
                            setPurchaseStatus(
                                key,
                                InternalPurchaseStatus.Unknown
                            )
                        }
                    }
                    return true
                } else {
                    reportAnalytics(BILLING_EVENT_SIGNATURE, 5)
                    log(BILLING, "Signature is invalid. Assume purchase is off")
                    it.token = null
                    setPurchaseStatus(key, InternalPurchaseStatus.Off)
                    return false
                }
            } ?: run {
                log(
                    BILLING,
                    "Public Key is null. This should never happen, but assume purchase is off"
                )
                reportAnalytics(BILLING_EVENT_SIGNATURE, 6)
                it.token = null
                setPurchaseStatus(key, InternalPurchaseStatus.Off)
                return false
            }

        }
        log(BILLING, "No purchase info found for %s. Should never happen", key)
        reportAnalytics(BILLING_EVENT_SIGNATURE, 7)
        return false
    }

    private fun acknowledgePurchase(purchase: Purchase) {
        if (isOld) return

        executeServiceRequest {
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            state.billingClient?.acknowledgePurchase(params) { billingResult ->
                handler.post {
                    logBillingResult(billingResult)
                    when (billingResult.responseCode) {
                        BillingClient.BillingResponseCode.OK -> {
                            reportAnalytics(BILLING_EVENT_ACK, 0)
                        }
                        else -> {
                            reportAnalytics(BILLING_EVENT_ACK, 1, billingResult.responseCode, billingResult.debugMessage)
                        }
                    }
                }
            }
        }
    }

    private fun verify(publicKey: PublicKey, signedData: String, signature: String): Boolean {
        val signatureBytes: ByteArray = try {
            Base64.decode(signature, Base64.DEFAULT)
        } catch (e: IllegalArgumentException) {
            reportAnalytics(BILLING_EVENT_SIGNATURE, 8, 0, e::class.java.name)
            log(BILLING, "Base64 decoding failed.") //NON-NLS
            return false
        }
        try {
            val signatureAlgorithm = Signature.getInstance("SHA1withRSA") //NON-NLS
            signatureAlgorithm.initVerify(publicKey)
            signatureAlgorithm.update(signedData.toByteArray())
            if (!signatureAlgorithm.verify(signatureBytes)) {
                reportAnalytics(BILLING_EVENT_SIGNATURE, 9)
                log(BILLING, "Signature verification failed.") //NON-NLS
                return false
            }
            return true
        } catch (e: Exception) {
            reportAnalytics(BILLING_EVENT_SIGNATURE, 10, 0, e::class.java.name)
        }
        return false
    }

    private fun generatePublicKey(encodedPublicKey: String): PublicKey? {
        try {
            val decodedKey = Base64.decode(encodedPublicKey, Base64.DEFAULT)
            val keyFactory = KeyFactory.getInstance("RSA") //NON-NLS
            return keyFactory.generatePublic(X509EncodedKeySpec(decodedKey))
        } catch (e: java.lang.Exception) {
            reportAnalytics(BILLING_EVENT_SIGNATURE, 11, 0, e::class.java.name)
        }
        return null
    }

    private fun decode(s: String?): String {
        return String(xorWithKey(base64Decode(s)))
    }

    private fun xorWithKey(a: ByteArray): ByteArray {
        val key = ByteArray(12)
        val r = Random(34534089753456L)
        r.nextBytes(key)
        val out = ByteArray(a.size)
        for (i in a.indices) {
            out[i] = (a[i].xor(key[i % key.size]))
        }
        return out
    }

    private fun base64Decode(s: String?): ByteArray {
        return Base64.decode(s, Base64.NO_WRAP)
    }

// These aren't usually needed
//        public static String encode(String s) {
//            return base64Encode(xorWithKey(s.getBytes()));
//        }
//
//        private static String base64Encode(byte[] bytes) {
//            byte[] output = Base64.encode(bytes, Base64.NO_WRAP);
//            return new String(output);
//        }

    @PurchaseStatus
    fun getPurchaseStatusNormal(key: String): Int =
        if (getPurchaseStatus(key, 0) == PURCHASE_ENABLED) PURCHASE_ENABLED else PURCHASE_DISABLED

    @PurchaseStatus
    fun getPurchaseStatusGenerous(key: String): Int =
        if (getPurchaseStatus(
                key,
                PURCHASE_FLAG_BENEFIT_OF_DOUBT
            ) == PURCHASE_ENABLED
        ) PURCHASE_ENABLED else PURCHASE_DISABLED

    @PurchaseStatusFull
    fun getPurchaseStatusWaiting(key: String): Int =
        getPurchaseStatus(key, PURCHASE_FLAG_CARE_ABOUT_WAITING)

    @PurchaseStatusFull
    fun getPurchaseStatusPending(key: String): Int =
        getPurchaseStatus(key, PURCHASE_FLAG_CARE_ABOUT_PENDING)

    @PurchaseStatusFull
    private fun getPurchaseStatus(key: String, @PurchaseRequestFlags flags: Int): Int {
        if (isOld) return PURCHASE_ENABLED
        if (!state.purchaseRegistered) return PURCHASE_DISABLED
        state.purchaseInfo[key]?.let {
            if (it.simulated) return PURCHASE_ENABLED

            var allOff = true
            var anyWaiting = false

            when (it.status) {
                InternalPurchaseStatus.On -> return PURCHASE_ENABLED
                InternalPurchaseStatus.Nothing,
                InternalPurchaseStatus.Init -> {
                    anyWaiting = true
                    allOff = false
                }
                InternalPurchaseStatus.Unknown,
                InternalPurchaseStatus.NoneRegistered -> {
                    allOff = false
                }
                InternalPurchaseStatus.Off -> {
                    // Do Nothing
                }
                InternalPurchaseStatus.Pending -> {
                    if (flags and PURCHASE_FLAG_CARE_ABOUT_PENDING != 0) {
                        return PURCHASE_PENDING
                    }
                    if (flags and PURCHASE_FLAG_BENEFIT_OF_DOUBT != 0) {
                        return PURCHASE_ENABLED
                    }
                }
            }
            it.otherKeys.forEach { otherKey ->
                state.purchaseInfo[otherKey]?.let { other ->
                    when (other.status) {
                        InternalPurchaseStatus.On -> return PURCHASE_ENABLED
                        InternalPurchaseStatus.Nothing,
                        InternalPurchaseStatus.Init -> {
                            anyWaiting = true
                            allOff = false
                        }
                        InternalPurchaseStatus.Unknown,
                        InternalPurchaseStatus.NoneRegistered -> {
                            allOff = false
                        }
                        InternalPurchaseStatus.Off -> {
                            // Do nothing
                        }
                        InternalPurchaseStatus.Pending -> {
                            if (flags and PURCHASE_FLAG_BENEFIT_OF_DOUBT != 0) {
                                return PURCHASE_ENABLED
                            }
                        }
                    }
                }
            }
            if (flags and PURCHASE_FLAG_CARE_ABOUT_WAITING != 0 && anyWaiting) return PURCHASE_NOT_YET_KNOWN
            if (allOff) return PURCHASE_DISABLED
            if (flags and PURCHASE_FLAG_BENEFIT_OF_DOUBT != 0) return PURCHASE_ENABLED
        }
        return PURCHASE_DISABLED
    }// should never happen

    fun isPurchaseRegistered(key: String?): Boolean {
        key?.let {
            if (state.purchaseInfo[it] != null) {
                return true
            }
        }
        return false
    }

    internal fun initPurchasePreference(preference: Preference) {
        if (isOld) {
            preference.isVisible = false
            return
        }
        state.purchaseInfo[preference.key]?.let {
            preference.isVisible = true
            preference.layoutResource = R.layout.rjhs_layout_preference
            it.skuDetails?.let { details ->
                preference.title = details.title
            } ?: run {
                preference.isVisible = false
            }
        } ?: run {
            preference.isVisible = false
        }
    }

    fun updatePurchasePreference(preference: Preference) {
        if (isOld) {
            preference.isVisible = false
            return
        }
        state.purchaseInfo[preference.key]?.let {
            preference.isVisible = true
            if (getPurchaseStatusNormal(preference.key) == PURCHASE_ENABLED) {
                preference.isEnabled = false
                preference.setSummary(R.string.rjhs_internal_str_settings_purchase_summary_on)
            } else if (getPurchaseStatusPending(preference.key) == PURCHASE_PENDING) {
                preference.isEnabled = false
                preference.setSummary(R.string.rjhs_internal_str_consent_manage_purchase_pending)
            } else {
                preference.isEnabled = true
                if (it.priceString.isNullOrBlank()) {
                    preference.setSummary(R.string.rjhs_internal_str_settings_purchase_summary_off_no_price)
                } else {
                    preference.summary =
                        resources.getString(
                            R.string.rjhs_internal_str_settings_purchase_summary_off,
                            it.priceString
                        )
                }
            }
        } ?: run {
            preference.isVisible = false
        }
    }

    internal val showAds: Boolean
        get() {
            if (isOld) return false
            if (!state.adRegistered) return false
            state.purchaseInfo.filter { it.value.removesAds }.forEach {
                if (getPurchaseStatusGenerous(it.key) == PURCHASE_ENABLED) return false
            }
            return true
        }

    internal val anonymousAds: Boolean
        get() {
            return !getBoolPref(getString(R.string.rjhs_fixed_settings_key_personalised))
        }

    fun reportAnalytics(event: AnalyticsEvent, code: Int) {
        reportAnalytics(event, code, 0, null)
    }

    fun reportAnalytics(event: AnalyticsEvent, code: Int, subCode: Int = 0, extraData: Any? = null) {
        reportAnalytics(
            AnalyticsInfo(
                event = event,
                code = code,
                subCode = subCode,
                extraData = extraData
            )
        )
    }

    private fun reportAnalytics(event: AnalyticsInfo) {
        log(ANALYTICS, "Reporting code: ${event.event.eventCodeBase + event.code}")
        if (isOld) {
            return
        }
        if (event.send) {
            FirebaseAnalytics.getInstance(this).logEvent("custom_event", event.bundle)
        } else {
            log(ANALYTICS, "Not sending analytics - not authorised by user")
        }
    }

    fun store(collection: String, vararg values: Pair<String, Any>) {
        store(collection, values.toMap())
    }

    fun store(collection: String, details: Map<String, Any>) {
        if (isOld) {
            return
        }
        System.currentTimeMillis().let { now ->
            FirebaseFirestore.getInstance()
                .collection(if (D.isDebug()) "debug_" else "" + collection)
                .document("time_$now")
                .set(details
                    .toMutableMap().apply {
                        put("device_model", Build.MODEL)
                        put("device_brand", Build.BRAND)
                        put("device_manufacturer", Build.MANUFACTURER)
                        put("device_sdk", Build.VERSION.SDK_INT)
                        put("event_time", System.currentTimeMillis())
                    })
                .addOnSuccessListener {
                    log(FIRESTORE, "Successfully sent to Firestore")
                }
                .addOnFailureListener { e ->
                    warn(FIRESTORE, "Error writing to Firestore", e)
                }
        }
    }
}

enum class AnalyticsLevel {
    Event,
    Warning,
    Error
}

enum class AnalyticsSensitivity {
    Sensitive,
    Normal,
    Fine
}

private val BILLING_EVENT_PURCHASE_UPDATED = AnalyticsEvent('u', 10100)
private val BILLING_EVENT_PURCHASE_STARTED = AnalyticsEvent('u', 10200)
private val BILLING_EVENT_SETUP = AnalyticsEvent('u', 10300)
private val BILLING_EVENT_SIGNATURE = AnalyticsEvent('u', 10400)
private val BILLING_EVENT_ACK = AnalyticsEvent('u', 10500)
private val BILLING_EVENT_PRODUCTS = AnalyticsEvent('u', 10600)
private val BILLING_EVENT_QUERY = AnalyticsEvent('u', 10700)
private val BILLING_EVENT_SERVICE_DISCONNECTED = AnalyticsEvent('u', 10800)

internal val AD_LOAD_EVENT = AnalyticsEvent('u', 20100)
internal val AD_SETUP_EVENT = AnalyticsEvent('u', 20200)

internal val CONSENT_EVENT = AnalyticsEvent('u', 30100)

data class AnalyticsEvent(
    val eventDomain: Char,
    val eventCodeBase: Int,
    val sensitivity: AnalyticsSensitivity = AnalyticsSensitivity.Normal,
    val level: AnalyticsLevel = AnalyticsLevel.Event,
)

internal data class AnalyticsInfo(
    val event: AnalyticsEvent,
    val code: Int,
    val subCode: Int = 0,
    val extraData: Any? = null
) {

    internal val bundle: Bundle
        get() {
            return Bundle()
                .addCode()
                .addExtraCode()
                .addExtraData()
                .addDate()
        }

    private val debugFlag: String = if (D.isDebug()) "d:" else ""

    private fun Bundle.addCode(): Bundle {
        putString(
            "code",
            String.format(Locale.US, "$debugFlag%c.%c.%c.%d", event.eventDomain, reasonCode, sensitivityCode, event.eventCodeBase + code)
        )
        putInt("rawCode", event.eventCodeBase)
        return this
    }

    private fun Bundle.addExtraCode(): Bundle {
        if (subCode != 0 && event.sensitivity == AnalyticsSensitivity.Fine || userAuthorised) {
            putString(
                "extraCode",
                String.format(
                    Locale.US,
                    "$debugFlag%c.%c.%c.%d.%d",
                    event.eventDomain,
                    reasonCode,
                    sensitivityCode,
                    event.eventCodeBase + code,
                    subCode
                )
            )
        }
        return this
    }

    private fun Bundle.addExtraData(): Bundle {
        if (extraData != null && event.sensitivity == AnalyticsSensitivity.Fine || userAuthorised) {
            putString("extraData", extraData.toString())
        }
        return this
    }

    private fun Bundle.addDate(): Bundle {
        putLong("date", System.currentTimeMillis())
        return this
    }

    internal val send: Boolean
        get() = event.sensitivity == AnalyticsSensitivity.Normal || event.sensitivity == AnalyticsSensitivity.Fine || userAuthorised

    private val reasonCode: Char = when (event.level) {
        AnalyticsLevel.Event -> '-'
        AnalyticsLevel.Warning -> 'w'
        AnalyticsLevel.Error -> 'e'
    }

    private val sensitivityCode: Char = when (event.sensitivity) {
        AnalyticsSensitivity.Sensitive -> 's'
        AnalyticsSensitivity.Normal -> 'n'
        AnalyticsSensitivity.Fine -> 'f'
    }

    private val userAuthorised: Boolean =
        app.getBoolPref(app.getString(R.string.rjhs_fixed_settings_key_analytics))
}

// Before API 18, the method was incorrectly named "currentPackageName", but it still returned the process name
// See https://github.com/aosp-mirror/platform_frameworks_base/commit/b57a50bd16ce25db441da5c1b63d48721bb90687
// Using the same technique as Application.getProcessName() for older devices
// Using reflection since ActivityThread is an internal API
val processName: String
    get() =
        if (Build.VERSION.SDK_INT >= 28) {
            getProcessName()
        } else {
            try {
                @SuppressLint("PrivateApi") val activityThread =
                    Class.forName("android.app.ActivityThread")

                // Before API 18, the method was incorrectly named "currentPackageName", but it still returned the process name
                // See https://github.com/aosp-mirror/platform_frameworks_base/commit/b57a50bd16ce25db441da5c1b63d48721bb90687
                val methodName =
                    if (Build.VERSION.SDK_INT >= 18) "currentProcessName" else "currentPackageName"
                @SuppressLint("PrivateApi") val getProcessName =
                    activityThread.getDeclaredMethod(methodName)
                getProcessName.invoke(null) as String
            } catch (e: Exception) {
                "(unknown process name)"
            }
        }