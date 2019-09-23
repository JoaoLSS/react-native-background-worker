package com.backgroundworker;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.work.Data;
import androidx.work.ListenableWorker;
import androidx.work.WorkerFactory;
import androidx.work.WorkerParameters;

import com.facebook.react.bridge.ReactApplicationContext;

public class BackgroundWorkerFactory extends WorkerFactory {

    private ReactApplicationContext reactContext;

    public BackgroundWorkerFactory(ReactApplicationContext reactContext) {
        super();
        this.reactContext = reactContext;
    }

    @Nullable
    @Override
    public ListenableWorker createWorker(@NonNull Context appContext, @NonNull String workerClassName, @NonNull WorkerParameters workerParameters) {
        return new BackgroundWorker(appContext, workerParameters, this.reactContext);
    }

}
