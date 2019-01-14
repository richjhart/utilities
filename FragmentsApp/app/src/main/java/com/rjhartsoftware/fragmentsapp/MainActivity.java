package com.rjhartsoftware.fragmentsapp;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import androidx.appcompat.app.AppCompatActivity;
import android.view.View;

import com.rjhartsoftware.fragments.FragmentTransactions;
import com.rjhartsoftware.logcatdebug.D;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        D.init(BuildConfig.VERSION_NAME, BuildConfig.DEBUG);
        super.onCreate(savedInstanceState);
        FragmentTransactions.activityCreated(this);
        setContentView(R.layout.activity_main);

        findViewById(R.id.button_pause).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // trigger an event that will pause it
                Intent popup_screen = new Intent(MainActivity.this, PopupActivity.class);
                startActivity(popup_screen);

                // then run the transaction after a delay
                new Handler().postDelayed(mDelayedTransaction, 2000);
            }
        });

        findViewById(R.id.button_stop).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // trigger an event that will stop it
                Intent full_screen = new Intent(MainActivity.this, FullActivity.class);
                startActivity(full_screen);

                // then run the transaction after a delay
                new Handler().postDelayed(mDelayedTransaction, 2000);
            }
        });

        findViewById(R.id.button_clear).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FragmentTransactions
                        .beginTransaction(MainActivity.this)
                        .clear(R.id.fragment_container)
                        .commit();
            }
        });
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();
        FragmentTransactions.activityResumed(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        FragmentTransactions.activityStarted(this);
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
    }

    @Override
    protected void onDestroy() {
        FragmentTransactions.activityDestroyed(this);
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        FragmentTransactions.activitySaved(this);
        super.onSaveInstanceState(outState);
    }

    private final Runnable mDelayedTransaction = new Runnable() {
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
    };
}
