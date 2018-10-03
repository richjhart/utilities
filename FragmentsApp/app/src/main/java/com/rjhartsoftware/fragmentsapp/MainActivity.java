package com.rjhartsoftware.fragmentsapp;

import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import com.rjhartsoftware.fragments.FragmentTransactions;
import com.rjhartsoftware.logcatdebug.D;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        D.init(BuildConfig.VERSION_NAME, BuildConfig.DEBUG);
        super.onCreate(savedInstanceState);
        FragmentTransactions.activityCreated(this);
        setContentView(R.layout.activity_main);
    }

    @Override
    protected void onStart() {
        FragmentTransactions.activityStarted(this);
        super.onStart();
    }

    @Override
    protected void onResume() {
        FragmentTransactions.activityResumed(this);
        super.onResume();
    }

    @Override
    protected void onPause() {
        FragmentTransactions.activityPaused(this);
        super.onPause();
    }

    @Override
    protected void onStop() {
        FragmentTransactions.activityStopped(this);
        super.onStop();

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                FragmentTransactions
                        .beginTransaction(MainActivity.this)
                        .replace(R.id.fragment_container, new MainFragment(), MainFragment.TAG)
                        .dontDuplicateInView()
                        .addToBackStack(null)
                        .runOnceAttached(new Runnable() {
                            @Override
                            public void run() {
                                D.log(D.GENERAL, "Fragment Attached");
                            }
                        })
                        .runOnceComplete(new Runnable() {
                            @Override
                            public void run() {
                                D.log(D.GENERAL, "Transaction Complete");
                            }
                        })
                        .commit();
            }
        }, 5000);
    }

    @Override
    protected void onDestroy() {
        FragmentTransactions.activityDestroyed(this);
        super.onDestroy();
    }


}
