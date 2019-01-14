package com.rjhartsoftware.fragments;

import androidx.lifecycle.LifecycleObserver;
import android.os.Build;
import androidx.annotation.AnimRes;
import androidx.annotation.AnimatorRes;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.appcompat.app.AppCompatActivity;
import android.text.TextUtils;

import com.rjhartsoftware.logcatdebug.D;

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.Queue;

public class FragmentTransactions extends Fragment implements LifecycleObserver {
    private static final String TAG = "_transactions";
    private static final D.DebugTag TRANSACTIONS = new D.DebugTag("delayed_transaction", true, true, 1); //NON-NLS
    private static final D.DebugTag TRANSACTIONS_VERBOSE = new D.DebugTag("delayed_transaction_extra", false, false, 1); //NON-NLS
    private boolean mAllowTransactions = false;
    private final Queue<DelayedTransaction> mDelayedTransactions = new ArrayDeque<>();

    public FragmentTransactions() {
        setRetainInstance(true);
    }

    public static void activityCreated(AppCompatActivity activity) {
        FragmentTransactions fragment = getFragment(activity);
        if (fragment == null) {
            activity.getSupportFragmentManager().beginTransaction()
                    .add(new FragmentTransactions(), TAG)
                    .commitNow();
            D.log(TRANSACTIONS, "attached transaction fragment to activity"); //NON-NLS
        } else {
            D.log(TRANSACTIONS, "transaction fragment already attached to activity"); //NON-NLS
        }
    }

    public static void activityStarted(AppCompatActivity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            D.log(TRANSACTIONS, "no need to check for started state"); //NON-NLS
            return;
        }
        FragmentTransactions fragment = getFragment(activity);
        if (fragment != null) {
            fragment.mAllowTransactions = true;
            D.log(TRANSACTIONS, "activity has started - allowing further transactions"); //NON-NLS
            fragment.executeTransaction();
        } else {
            D.error(TRANSACTIONS, "activity has started, but unable to find the transaction fragment"); //NON-NLS
        }
    }

    public static void activityResumed(AppCompatActivity activity) {
        FragmentTransactions fragment = getFragment(activity);
        if (fragment != null && !fragment.mAllowTransactions) {
            fragment.mAllowTransactions = true;
            D.log(TRANSACTIONS, "activity has resumed - allowing further transactions"); //NON-NLS
            fragment.executeTransaction();
        } else if (fragment != null) {
            D.log(TRANSACTIONS, "no need to check resumption - transactions already allowed");
        } else {
            D.error(TRANSACTIONS, "activity has resumed, but unable to find the transaction fragment"); //NON-NLS
        }
    }

    public static void activitySaved(AppCompatActivity activity) {
        FragmentTransactions fragment = getFragment(activity);
        if (fragment != null) {
            fragment.mAllowTransactions = false;
            D.log(TRANSACTIONS, "activity has been saved - stopping further transactions"); //NON-NLS
        } else {
            D.error(TRANSACTIONS, "activity has been saved, but unable to find the transaction fragment"); //NON-NLS
        }
    }

    public static void activityPaused(AppCompatActivity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            D.log(TRANSACTIONS, "no need to check for paused state"); //NON-NLS
            return;
        }
        FragmentTransactions fragment = getFragment(activity);
        if (fragment != null) {
            fragment.mAllowTransactions = false;
            D.log(TRANSACTIONS, "activity has paused - stopping further transactions"); //NON-NLS
        } else {
            D.error(TRANSACTIONS, "activity has paused, but unable to find the transaction fragment"); //NON-NLS
        }
    }

    public static void activityStopped(AppCompatActivity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            D.log(TRANSACTIONS, "no need to check for stopped state"); //NON-NLS
            return;
        }
        FragmentTransactions fragment = getFragment(activity);
        if (fragment != null) {
            fragment.mAllowTransactions = false;
            D.log(TRANSACTIONS, "activity has stopped - stopping further transactions"); //NON-NLS
        } else {
            D.error(TRANSACTIONS, "activity has stopped, but unable to find the transaction fragment"); //NON-NLS
        }
    }

    public static void activityDestroyed(@SuppressWarnings("unused") AppCompatActivity activity) {
        D.log(TRANSACTIONS, "activity has been destroyed"); //NON-NLS
    }

    private boolean mExecutingTransactions = false;

    private void executeTransaction() {
        if (getFragmentManager() == null) {
            D.error(TRANSACTIONS, "the transaction fragment is not attached to the fragment manager. Unable to execute queue"); //NON-NLS
            return;
        }
        if (mAllowTransactions && !mExecutingTransactions) {
            mExecutingTransactions = true;
            if (!mDelayedTransactions.isEmpty()) {
                D.log(TRANSACTIONS, "executing delayed fragment transactions..."); //NON-NLS
            }
            while (!mDelayedTransactions.isEmpty()) {
                DelayedTransaction transaction = mDelayedTransactions.poll();
                FragmentManager manager = null;
                FragmentTransaction new_transaction = null;
                //noinspection ConstantConditions
                if (transaction.mParentTag == null) {
                    manager = getFragmentManager();
                    new_transaction = manager
                            .beginTransaction();
                } else {
                    // search for the parent fragment
                    Fragment parent = findFragment(getFragmentManager(), transaction.mParentTag);
                    if (parent == null) {
                        D.error(TRANSACTIONS, "Unable to find fragment manager for " + transaction.mParentTag + "!"); //NON-NLS
                    } else {
                        manager = parent.getChildFragmentManager();
                        new_transaction = manager.beginTransaction();
                    }
                }
                if (manager == null || new_transaction == null) {
                    mExecutingTransactions = false;
                    return;
                }
                if (transaction.mBackstack) {
                    D.log(TRANSACTIONS_VERBOSE, "adding transaction to back stack"); //NON-NLS
                    new_transaction.addToBackStack(transaction.mBackstackTag);
                }
                if (transaction.mTransitions != null) {
                    D.log(TRANSACTIONS_VERBOSE, "settings custom transitions"); //NON-NLS
                    new_transaction.setCustomAnimations(transaction.mTransitions[0],
                            transaction.mTransitions[1],
                            transaction.mTransitions[2],
                            transaction.mTransitions[3]
                    );
                }
                boolean proceed = true;
                boolean allow_callback = true;
                switch (transaction.mAction) {
                    case DelayedTransaction.ACTION_ADD_1:
                        D.log(TRANSACTIONS, "executing delayed fragment addition (view) for " + transaction.mFragment.getClass().getSimpleName()); //NON-NLS
                        if (checkViewDuplicate(transaction, manager) || checkTagDuplicate(transaction, manager)) {
                            proceed = false;
                        } else {
                            new_transaction.add(transaction.mId, transaction.mFragment, transaction.mTag);
                        }
                        break;
                    case DelayedTransaction.ACTION_REPLACE_1:
                        D.log(TRANSACTIONS, "executing delayed fragment replacement for " + transaction.mFragment.getClass().getSimpleName()); //NON-NLS
                        if (checkViewDuplicate(transaction, manager) || checkTagDuplicate(transaction, manager)) {
                            proceed = false;
                        } else {
                            new_transaction.replace(transaction.mId, transaction.mFragment, transaction.mTag);
                        }
                        break;
                    case DelayedTransaction.ACTION_ADD_2:
                        D.log(TRANSACTIONS, "executing delayed fragment addition (no view) for " + transaction.mFragment.getClass().getSimpleName()); //NON-NLS
                        if (checkTagDuplicate(transaction, manager)) {
                            proceed = false;
                        } else {
                            new_transaction.add(transaction.mFragment, transaction.mTag);
                        }
                        break;
                    case DelayedTransaction.ACTION_POP:
                        if (transaction.mParentTag == null) {
                            D.log(TRANSACTIONS, "executing delayed fragment pop"); //NON-NLS
                        } else {
                            D.log(TRANSACTIONS, "executing delayed fragment pop for " + transaction.mParentTag); //NON-NLS
                        }
                        manager.popBackStackImmediate();
                        proceed = false;
                        break;
                    case DelayedTransaction.ACTION_REMOVE:
                        D.log(TRANSACTIONS, "executing delayed fragment removal of " + transaction.mTag); //NON-NLS
                        Fragment fragment = manager.findFragmentByTag(transaction.mTag);
                        if (fragment != null) {
                            new_transaction.remove(fragment);
                        } else {
                            proceed = false;
                        }
                        break;
                    default:
                        proceed = false;
                        allow_callback = false;
                        break;
                }
                if (proceed) {
                    D.log(TRANSACTIONS_VERBOSE, "Committing transaction to manager"); //NON-NLS
                    new_transaction.commit();
                    manager.executePendingTransactions();
                    if (transaction.mCallbackAttached != null) {
                        transaction.mCallbackAttached.run();
                    }
                }
                if (allow_callback && transaction.mCallbackComplete != null) {
                    transaction.mCallbackComplete.run();
                }
            }
            mExecutingTransactions = false;
        } else {
            for (DelayedTransaction transaction : mDelayedTransactions) {
                transaction.clearTransitions();
            }
        }
    }

    @Nullable
    private Fragment findFragment(FragmentManager fragmentManager, String tag) {
        Fragment fragment = fragmentManager.findFragmentByTag(tag);
        if (fragment != null) {
            return fragment;
        }
        for (Fragment child : fragmentManager.getFragments()) {
            fragment = findFragment(child.getChildFragmentManager(), tag);
            if (fragment != null) {
                return fragment;
            }
        }
        return null;
    }

    private boolean checkViewDuplicate(DelayedTransaction transaction, FragmentManager fragmentManager) {
        if (!transaction.mDontDuplicateInView) {
            return false;
        }
        Fragment fragment = fragmentManager.findFragmentById(transaction.mId);
        if (fragment == null) {
            return false;
        }
        if (transaction.mFirstFragmentOnly) {
            D.log(TRANSACTIONS, "Not performing transaction because any fragment is already there"); //NON-NLS
            return true;
        }
        if (fragment.getClass().equals(transaction.mFragment.getClass())) {
            D.log(TRANSACTIONS, "Not performing transaction because the correct fragment type is already there"); //NON-NLS
            return true;
        }
        return false;
    }

    private boolean checkTagDuplicate(DelayedTransaction transaction, FragmentManager fragmentManager) {
        if (!transaction.mDontDuplicateTag) {
            return false;
        }
        Fragment fragment = fragmentManager.findFragmentByTag(transaction.mTag);
        return fragment != null;
    }

    @Nullable
    private static FragmentTransactions getFragment(AppCompatActivity activity) {
        if (activity != null && activity.getSupportFragmentManager() != null) {
            return (FragmentTransactions) activity.getSupportFragmentManager().findFragmentByTag(TAG);
        }
        return null;
    }

    public static DelayedTransaction beginTransaction(AppCompatActivity activity) {
        return new DelayedTransaction(activity);
    }

    public static class DelayedTransaction {

        private static final int ACTION_NONE = -1;
        private static final int ACTION_ADD_1 = 1;
        private static final int ACTION_ADD_2 = 2;
        private static final int ACTION_REPLACE_1 = 3;
        private static final int ACTION_POP = 4;
        private static final int ACTION_REMOVE = 5;
        private final WeakReference<AppCompatActivity> mActivity;
        private int mAction = ACTION_NONE;
        private int mId = -1;
        private Fragment mFragment;
        private Runnable mCallbackAttached;
        private Runnable mCallbackComplete;
        private String mTag;
        private String mBackstackTag;
        private boolean mDontDuplicateInView = false;
        private boolean mDontDuplicateTag = false;
        private boolean mBackstack = false;
        private String mParentTag;

        DelayedTransaction(AppCompatActivity activity) {
            mActivity = new WeakReference<>(activity);
        }

        public DelayedTransaction add(int id, Fragment fragment, String tag) {
            mAction = ACTION_ADD_1;
            mId = id;
            mFragment = fragment;
            mTag = tag;
            return this;
        }

        public DelayedTransaction replace(int id, Fragment fragment, String tag) {
            mAction = ACTION_REPLACE_1;
            mId = id;
            mFragment = fragment;
            mTag = tag;
            return this;
        }

        public DelayedTransaction add(Fragment fragment, String tag) {
            mAction = ACTION_ADD_2;
            mFragment = fragment;
            mTag = tag;
            return this;
        }

        public DelayedTransaction addToBackStack(String tag) {
            mBackstack = true;
            mBackstackTag = tag;
            return this;
        }

        public DelayedTransaction setParentFragment(String tag) {
            mParentTag = tag;
            return this;
        }

        public void commit() {
            if (mActivity.get() == null || mActivity.get().isFinishing()) {
                D.log(TRANSACTIONS, "no activity or activity is closing - not bothering to execute transaction"); //NON-NLS
                return;
            }
            FragmentTransactions fragment = getFragment(mActivity.get());
            if (fragment != null) {
                if (mFragment != null) {
                    D.log(TRANSACTIONS, "queuing transaction for now or later for " + mFragment.getClass().getSimpleName()); //NON-NLS
                } else if (mAction == ACTION_POP) {
                    if (mParentTag == null) {
                        D.log(TRANSACTIONS, "queuing fragment pop for now or later"); //NON-NLS
                    } else {
                        D.log(TRANSACTIONS, "queuing fragment pop for now or later for " + mParentTag); //NON-NLS
                    }
                } else if (mAction == ACTION_REMOVE && !TextUtils.isEmpty(mTag)) {
                    D.log(TRANSACTIONS, "queuing fragment removal for now or later of " + mTag); //NON-NLS
                } else {
                    D.error(TRANSACTIONS, "queued transaction doesn't make sense"); //NON-NLS
                }
                fragment.mDelayedTransactions.add(this);
                fragment.executeTransaction();
            } else {
                D.error(TRANSACTIONS, "unable to find transaction fragment"); //NON-NLS
            }
        }

        public DelayedTransaction dontDuplicateInView() {
            // note, it's not checked at this point - because a previous queued transaction might result in a duplicate that wouldn't otherwise be detected
            mDontDuplicateInView = true;
            return this;
        }

        public DelayedTransaction dontDuplicateTag() {
            mDontDuplicateTag = true;
            return this;
        }

        private void clearTransitions() {
            mTransitions = null;
        }

        private int[] mTransitions;

        public DelayedTransaction setCustomAnimations(@AnimatorRes @AnimRes int enter,
                                                      @AnimatorRes @AnimRes int exit,
                                                      @AnimatorRes @AnimRes int popEnter,
                                                      @AnimatorRes @AnimRes int popExit) {
            mTransitions = new int[]{enter, exit, popEnter, popExit};
            return this;
        }

        public void popBackStack() {
            mAction = ACTION_POP;
            commit();
        }

        private boolean mFirstFragmentOnly = false;

        public DelayedTransaction firstFragmentOnly() {
            mFirstFragmentOnly = true;
            // simplifies later calls to make this true as well
            mDontDuplicateInView = true;
            return this;
        }

        public DelayedTransaction runOnceAttached(Runnable runnable) {
            mCallbackAttached = runnable;
            return this;
        }

        public DelayedTransaction runOnceComplete(Runnable runnable) {
            mCallbackComplete = runnable;
            return this;
        }

        public DelayedTransaction remove(String tag) {
            mAction = ACTION_REMOVE;
            mTag = tag;
            return this;
        }
    }

}

