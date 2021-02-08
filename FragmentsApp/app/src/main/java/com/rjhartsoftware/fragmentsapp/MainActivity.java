package com.rjhartsoftware.fragmentsapp;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import androidx.appcompat.app.AppCompatActivity;
import android.view.View;

import com.rjhartsoftware.fragments.FragmentTransactions;
import com.rjhartsoftware.fragments.TransactionsActivity;
import com.rjhartsoftware.logcatdebug.D;

public class MainActivity extends TransactionsActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        D.init(BuildConfig.VERSION_NAME, BuildConfig.DEBUG);
        super.onCreate(savedInstanceState);
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

        findViewById(R.id.button_popup).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FragmentTransactions.beginTransaction(MainActivity.this)
                        .add(new PopupFragment(), PopupFragment.TAG)
                        .commit();
            }
        });
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
