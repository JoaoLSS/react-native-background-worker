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

import com.facebook.react.bridge.ReactApplicationContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static android.os.SystemClock.sleep;

public class BackgroundWorker extends Worker {

    final String id;
    final String payload;
    final String worker;

    String result = "running";


    BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            BackgroundWorker.this.result = intent.getStringExtra("result");
        }
    };

    public BackgroundWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        this.id = workerParams.getId().toString();
        Data inputData = workerParams.getInputData();
        this.worker = inputData.getString("worker");
        this.payload = inputData.getString("payload");
        LocalBroadcastManager.getInstance(this.getApplicationContext()).registerReceiver(this.receiver, new IntentFilter(this.id + "result"));
    }

    @NonNull
    @Override
    public Result doWork() {

        Bundle extras = new Bundle();
        extras.putString("payload", this.payload);
        extras.putString("id", this.id);
        extras.putString("worker", this.worker);

        Intent headlessJS = new Intent(this.getApplicationContext(), BackgroundWorkerService.class);
        headlessJS.putExtras(extras);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) this.getApplicationContext().startForegroundService(headlessJS);
        else this.getApplicationContext().startService(headlessJS);

        while(this.result.equals("running")) { sleep(100); }

        switch (this.result) {
            case "success": return Result.success();
            case "retry": return Result.retry();
            default: return Result.failure();
        }

    }
}
