package com.rjhartsoftware.fragments;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class TransactionsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FragmentTransactions.activityCreated(this);
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
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        FragmentTransactions.activitySaved(this);
        super.onSaveInstanceState(outState);
    }

}
