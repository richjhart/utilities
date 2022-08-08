package com.rjhartsoftware.utilities.fragments

import android.os.Build
import android.text.TextUtils
import androidx.annotation.AnimRes
import androidx.annotation.AnimatorRes
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import androidx.lifecycle.LifecycleObserver
import com.rjhartsoftware.utilities.D
import com.rjhartsoftware.utilities.D.error
import com.rjhartsoftware.utilities.D.log
import java.lang.ref.WeakReference
import java.util.*

class RjhsFragmentTransactions : Fragment(), LifecycleObserver {
    private var mAllowTransactions = false
    private val mDelayedTransactions: Queue<DelayedTransaction> = ArrayDeque()
    private var mExecutingTransactions = false

    private fun executeTransaction() {
        if (mAllowTransactions && !mExecutingTransactions) {
            mExecutingTransactions = true
            if (!mDelayedTransactions.isEmpty()) {
                log(TRANSACTIONS, "executing delayed fragment transactions...") //NON-NLS
            }
            while (!mDelayedTransactions.isEmpty()) {
                val transaction = mDelayedTransactions.poll()
                var manager: FragmentManager? = null
                var newTransaction: FragmentTransaction? = null
                if (transaction?.mParentTag == null) {
                    manager = parentFragmentManager
                    newTransaction = manager
                        .beginTransaction()
                } else {
                    // search for the parent fragment
                    val parent = findFragment(parentFragmentManager, transaction.mParentTag)
                    if (parent == null) {
                        error(
                            TRANSACTIONS,
                            "Unable to find fragment manager for " + transaction.mParentTag + "!"
                        ) //NON-NLS
                    } else {
                        manager = parent.childFragmentManager
                        newTransaction = manager.beginTransaction()
                    }
                }
                if (manager == null || newTransaction == null) {
                    mExecutingTransactions = false
                    return
                }
                if (transaction?.mBackstack == true) {
                    log(TRANSACTIONS_VERBOSE, "adding transaction to back stack") //NON-NLS
                    newTransaction.addToBackStack(transaction.mBackstackTag)
                }
                if (transaction?.mTransitions != null) {
                    log(TRANSACTIONS_VERBOSE, "settings custom transitions") //NON-NLS
                    newTransaction.setCustomAnimations(
                        transaction.mTransitions!![0],
                        transaction.mTransitions!![1],
                        transaction.mTransitions!![2],
                        transaction.mTransitions!![3]
                    )
                }
                var proceed = true
                var allowCallback = true
                when (transaction?.mAction) {
                    DelayedTransaction.ACTION_ADD_1 -> {
                        log(
                            TRANSACTIONS,
                            "executing delayed fragment addition (view) for %s", transaction.mFragment?.javaClass?.simpleName
                        ) //NON-NLS
                        if (checkViewDuplicate(transaction, manager) || checkTagDuplicate(
                                transaction,
                                manager
                            )
                        ) {
                            proceed = false
                        } else {
                            newTransaction.add(
                                transaction.mId,
                                transaction.mFragment!!,
                                transaction.mTag
                            )
                        }
                    }
                    DelayedTransaction.ACTION_REPLACE_1 -> {
                        log(
                            TRANSACTIONS,
                            "executing delayed fragment replacement for %s", transaction.mFragment?.javaClass?.simpleName
                        ) //NON-NLS
                        if (checkViewDuplicate(transaction, manager) || checkTagDuplicate(
                                transaction,
                                manager
                            )
                        ) {
                            proceed = false
                        } else {
                            newTransaction.replace(
                                transaction.mId,
                                transaction.mFragment!!,
                                transaction.mTag
                            )
                        }
                    }
                    DelayedTransaction.ACTION_ADD_2 -> {
                        log(
                            TRANSACTIONS,
                            "executing delayed fragment addition (no view) for %s", transaction.mFragment?.javaClass?.simpleName
                        ) //NON-NLS
                        if (checkTagDuplicate(transaction, manager)) {
                            proceed = false
                        } else {
                            newTransaction.add(transaction.mFragment!!, transaction.mTag)
                        }
                    }
                    DelayedTransaction.ACTION_POP -> {
                        if (transaction.mParentTag == null) {
                            log(TRANSACTIONS, "executing delayed fragment pop") //NON-NLS
                        } else {
                            log(
                                TRANSACTIONS,
                                "executing delayed fragment pop for " + transaction.mParentTag
                            ) //NON-NLS
                        }
                        manager.popBackStackImmediate()
                        proceed = false
                    }
                    DelayedTransaction.ACTION_REMOVE -> {
                        log(
                            TRANSACTIONS,
                            "executing delayed fragment removal of " + transaction.mTag
                        ) //NON-NLS
                        val fragment = manager.findFragmentByTag(transaction.mTag)
                        if (fragment != null) {
                            newTransaction.remove(fragment)
                        } else {
                            proceed = false
                        }
                    }
                    DelayedTransaction.ACTION_CLEAR -> {
                        log(TRANSACTIONS, "executing delayed removal from " + transaction.mId)
                        val fragmentToRemove = manager.findFragmentById(transaction.mId)
                        if (fragmentToRemove != null) {
                            newTransaction.remove(fragmentToRemove)
                        } else {
                            proceed = false
                        }
                    }
                    else -> {
                        proceed = false
                        allowCallback = false
                    }
                }
                if (proceed) {
                    log(TRANSACTIONS_VERBOSE, "Committing transaction to manager") //NON-NLS
                    newTransaction.commit()
                    manager.executePendingTransactions()
                    if (transaction?.mCallbackAttached != null) {
                        transaction.mCallbackAttached!!.run()
                    }
                }
                if (allowCallback && transaction?.mCallbackComplete != null) {
                    transaction.mCallbackComplete!!.run()
                }
            }
            mExecutingTransactions = false
        } else {
            for (transaction in mDelayedTransactions) {
                transaction.clearTransitions()
            }
        }
    }

    private fun findFragment(fragmentManager: FragmentManager, tag: String?): Fragment? {
        var fragment = fragmentManager.findFragmentByTag(tag)
        if (fragment != null) {
            return fragment
        }
        for (child in fragmentManager.fragments) {
            fragment = findFragment(child.childFragmentManager, tag)
            if (fragment != null) {
                return fragment
            }
        }
        return null
    }

    private fun checkViewDuplicate(
        transaction: DelayedTransaction,
        fragmentManager: FragmentManager
    ): Boolean {
        if (!transaction.mDontDuplicateInView) {
            return false
        }
        val fragment = fragmentManager.findFragmentById(transaction.mId) ?: return false
        if (transaction.mFirstFragmentOnly) {
            log(
                TRANSACTIONS,
                "Not performing transaction because any fragment is already there"
            ) //NON-NLS
            return true
        }
        if (fragment.javaClass == transaction.mFragment?.javaClass) {
            log(
                TRANSACTIONS,
                "Not performing transaction because the correct fragment type is already there"
            ) //NON-NLS
            return true
        }
        return false
    }

    private fun checkTagDuplicate(
        transaction: DelayedTransaction,
        fragmentManager: FragmentManager
    ): Boolean {
        if (!transaction.mDontDuplicateTag) {
            return false
        }
        val fragment = fragmentManager.findFragmentByTag(transaction.mTag)
        return fragment != null
    }

    class DelayedTransaction internal constructor(activity: AppCompatActivity?) {
        private val mActivity: WeakReference<AppCompatActivity?> = WeakReference(activity)
        var mAction = ACTION_NONE
        var mId = -1
        var mFragment: Fragment? = null
        var mCallbackAttached: Runnable? = null
        var mCallbackComplete: Runnable? = null
        var mTag: String? = null
        internal var mBackstackTag: String? = null
        var mDontDuplicateInView = false
        var mDontDuplicateTag = false
        internal var mBackstack = false
        var mParentTag: String? = null

        fun add(id: Int, fragment: Fragment?, tag: String?): DelayedTransaction {
            mAction = ACTION_ADD_1
            mId = id
            mFragment = fragment
            mTag = tag
            return this
        }

        fun replace(id: Int, fragment: Fragment?, tag: String?): DelayedTransaction {
            mAction = ACTION_REPLACE_1
            mId = id
            mFragment = fragment
            mTag = tag
            return this
        }

        fun clear(id: Int): DelayedTransaction {
            mAction = ACTION_CLEAR
            mId = id
            return this
        }

        fun add(fragment: Fragment?, tag: String?): DelayedTransaction {
            mAction = ACTION_ADD_2
            mFragment = fragment
            mTag = tag
            return this
        }

        fun addToBackStack(tag: String?): DelayedTransaction {
            mBackstack = true
            mBackstackTag = tag
            return this
        }

        fun setParentFragment(tag: String?): DelayedTransaction {
            mParentTag = tag
            return this
        }

        fun commit() {
            if (mActivity.get() == null || mActivity.get()!!.isFinishing) {
                log(
                    TRANSACTIONS,
                    "no activity or activity is closing - not bothering to execute transaction"
                ) //NON-NLS
                return
            }
            val fragment = getFragment(mActivity.get())
            if (fragment != null) {
                if (mFragment != null) {
                    log(
                        TRANSACTIONS,
                        "queuing transaction for now or later for " + mFragment!!.javaClass.simpleName
                    ) //NON-NLS
                } else if (mAction == ACTION_POP) {
                    if (mParentTag == null) {
                        log(TRANSACTIONS, "queuing fragment pop for now or later") //NON-NLS
                    } else {
                        log(
                            TRANSACTIONS,
                            "queuing fragment pop for now or later for $mParentTag"
                        ) //NON-NLS
                    }
                } else if (mAction == ACTION_REMOVE && !TextUtils.isEmpty(mTag)) {
                    log(
                        TRANSACTIONS,
                        "queuing fragment removal for now or later of $mTag"
                    ) //NON-NLS
                } else if (mAction == ACTION_CLEAR) {
                    log(TRANSACTIONS, "queuing removal of all fragments from $mId")
                } else {
                    error(TRANSACTIONS, "queued transaction doesn't make sense") //NON-NLS
                }
                fragment.mDelayedTransactions.add(this)
                fragment.executeTransaction()
            } else {
                error(TRANSACTIONS, "unable to find transaction fragment") //NON-NLS
            }
        }

        fun dontDuplicateInView(): DelayedTransaction {
            // note, it's not checked at this point - because a previous queued transaction might result in a duplicate that wouldn't otherwise be detected
            mDontDuplicateInView = true
            return this
        }

        fun dontDuplicateTag(): DelayedTransaction {
            mDontDuplicateTag = true
            return this
        }

        fun clearTransitions() {
            mTransitions = null
        }

        var mTransitions: IntArray? = null

        fun setCustomAnimations(
            @AnimatorRes @AnimRes enter: Int,
            @AnimatorRes @AnimRes exit: Int,
            @AnimatorRes @AnimRes popEnter: Int,
            @AnimatorRes @AnimRes popExit: Int
        ): DelayedTransaction {
            mTransitions = intArrayOf(enter, exit, popEnter, popExit)
            return this
        }

        fun popBackStack() {
            mAction = ACTION_POP
            commit()
        }

        var mFirstFragmentOnly = false
        fun firstFragmentOnly(): DelayedTransaction {
            mFirstFragmentOnly = true
            // simplifies later calls to make this true as well
            mDontDuplicateInView = true
            return this
        }

        fun runOnceAttached(runnable: Runnable?): DelayedTransaction {
            mCallbackAttached = runnable
            return this
        }

        fun runOnceComplete(runnable: Runnable?): DelayedTransaction {
            mCallbackComplete = runnable
            return this
        }

        fun remove(tag: String?): DelayedTransaction {
            mAction = ACTION_REMOVE
            mTag = tag
            return this
        }

        companion object {
            private const val ACTION_NONE = -1
            const val ACTION_ADD_1 = 1
            const val ACTION_ADD_2 = 2
            const val ACTION_REPLACE_1 = 3
            const val ACTION_POP = 4
            const val ACTION_REMOVE = 5
            const val ACTION_CLEAR = 6
        }

    }

    companion object {
        private const val TAG = "_transactions"
        private val TRANSACTIONS: D.DebugTag =
            D.DebugTag("delayed_transaction", true, 1) //NON-NLS
        private val TRANSACTIONS_VERBOSE: D.DebugTag =
            D.DebugTag("delayed_transaction_extra", false, 1) //NON-NLS

        fun activityCreated(activity: AppCompatActivity) {
            val fragment = getFragment(activity)
            if (fragment == null) {
                activity.supportFragmentManager.beginTransaction()
                    .add(RjhsFragmentTransactions(), TAG)
                    .commitNow()
                log(TRANSACTIONS, "attached transaction fragment to activity") //NON-NLS
            } else {
                log(TRANSACTIONS, "transaction fragment already attached to activity") //NON-NLS
            }
        }

        fun activityStarted(activity: AppCompatActivity?) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                log(TRANSACTIONS, "no need to check for started state") //NON-NLS
                return
            }
            val fragment = getFragment(activity)
            if (fragment != null) {
                fragment.mAllowTransactions = true
                log(TRANSACTIONS, "activity has started - allowing further transactions") //NON-NLS
                fragment.executeTransaction()
            } else {
                error(
                    TRANSACTIONS,
                    "activity has started, but unable to find the transaction fragment"
                ) //NON-NLS
            }
        }

        fun activityResumed(activity: AppCompatActivity?) {
            val fragment = getFragment(activity)
            if (fragment != null && !fragment.mAllowTransactions) {
                fragment.mAllowTransactions = true
                log(TRANSACTIONS, "activity has resumed - allowing further transactions") //NON-NLS
                fragment.executeTransaction()
            } else if (fragment != null) {
                log(TRANSACTIONS, "no need to check resumption - transactions already allowed")
            } else {
                error(
                    TRANSACTIONS,
                    "activity has resumed, but unable to find the transaction fragment"
                ) //NON-NLS
            }
        }

        fun activitySaved(activity: AppCompatActivity?) {
            val fragment = getFragment(activity)
            if (fragment != null) {
                fragment.mAllowTransactions = false
                log(
                    TRANSACTIONS,
                    "activity has been saved - stopping further transactions"
                ) //NON-NLS
            } else {
                error(
                    TRANSACTIONS,
                    "activity has been saved, but unable to find the transaction fragment"
                ) //NON-NLS
            }
        }

        fun activityPaused(activity: AppCompatActivity?) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                log(TRANSACTIONS, "no need to check for paused state") //NON-NLS
                return
            }
            val fragment = getFragment(activity)
            if (fragment != null) {
                fragment.mAllowTransactions = false
                log(TRANSACTIONS, "activity has paused - stopping further transactions") //NON-NLS
            } else {
                error(
                    TRANSACTIONS,
                    "activity has paused, but unable to find the transaction fragment"
                ) //NON-NLS
            }
        }

        fun activityStopped(activity: AppCompatActivity?) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                log(TRANSACTIONS, "no need to check for stopped state") //NON-NLS
                return
            }
            val fragment = getFragment(activity)
            if (fragment != null) {
                fragment.mAllowTransactions = false
                log(TRANSACTIONS, "activity has stopped - stopping further transactions") //NON-NLS
            } else {
                error(
                    TRANSACTIONS,
                    "activity has stopped, but unable to find the transaction fragment"
                ) //NON-NLS
            }
        }

        fun activityDestroyed(activity: AppCompatActivity?) {
            log(TRANSACTIONS, "activity has been destroyed") //NON-NLS
        }

        private fun getFragment(activity: AppCompatActivity?): RjhsFragmentTransactions? {
            return if (activity != null) {
                activity.supportFragmentManager.findFragmentByTag(TAG) as RjhsFragmentTransactions?
            } else null
        }

        @JvmStatic
        fun beginTransaction(activity: AppCompatActivity?): DelayedTransaction {
            return DelayedTransaction(activity)
        }
    }

    init {
        retainInstance = true
    }
}