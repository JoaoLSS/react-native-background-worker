package com.backgroundworker;

import android.app.Notification;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static android.os.SystemClock.sleep;

public class BackgroundWorker extends Worker {

    private static String TAG = "BG_WORKER::";

    private final String id;
    private final String payload;
    private final String worker;
    private final boolean shouldRetry;

    private String workflowResult = "running";
    private String result;


    BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            BackgroundWorker.this.workflowResult = intent.getStringExtra("workflowResult");
            BackgroundWorker.this.result = intent.getStringExtra("result");
        }
    };

    public BackgroundWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        this.id = workerParams.getId().toString();
        Data inputData = workerParams.getInputData();
        this.worker = inputData.getString("worker");
        this.payload = inputData.getString("payload");
        this.shouldRetry = inputData.getBoolean("shouldRetry", false);
        LocalBroadcastManager.getInstance(this.getApplicationContext()).registerReceiver(this.receiver, new IntentFilter(this.id + "result"));
    }

    @NonNull
    @Override
    public Result doWork() {

        Log.d(TAG, "doing Work");

        if(BackgroundWorkerModule.context == null) return Result.retry();

        if(this.payload == null || this.worker == null) return Result.failure();

        Bundle extras = new Bundle();
        extras.putString("payload", this.payload);
        extras.putString("id", this.id);
        extras.putString("worker", this.worker);

        Intent broadcast = new Intent("DO_WORK");

        broadcast.putExtras(extras);

        BackgroundWorkerModule.context.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(worker, Arguments.fromBundle(extras));

        while(this.workflowResult.equals("running")) { sleep(100); }

        Data outputData = new Data.Builder()
                .putString("result", this.result)
                .build();

        switch (this.workflowResult) {
            case "success": return Result.success(outputData);
            default: return shouldRetry ? Result.retry() : Result.failure(outputData);
        }

    }
}
