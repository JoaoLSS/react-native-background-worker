package com.backgroundworker;

import android.app.Notification;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.facebook.react.bridge.ReactApplicationContext;

import java.util.ArrayList;

import static android.os.SystemClock.sleep;

public class BackgroundWorker extends Worker {

    final ReactApplicationContext reactContext;
    final String id;
    private String result;

    private Data data;

    public BackgroundWorker(@NonNull Context appContext, @NonNull WorkerParameters workerParams , ReactApplicationContext reactContext) {
        super(appContext, workerParams);
        this.reactContext = reactContext;
        this.id = workerParams.getId().toString();
        this.data = workerParams.getInputData();

        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                BackgroundWorker.this.result = intent.hasExtra("result") ? intent.getStringExtra("result") : "failure";
            }
        };

        LocalBroadcastManager.getInstance(reactContext).registerReceiver(receiver, new IntentFilter(this.id));
    }

    @NonNull
    @Override
    public Result doWork() {

        this.result = "running";

        Intent service = new Intent(this.reactContext, BackgroundWorkerService.class);
        service.putExtra("id", this.id);
        service.putExtra("actions", this.data.getStringArray("actions"));
        service.putExtra("title", this.data.getString("title"));
        service.putExtra("message", this.data.getString("message"));

        Log.d("BackgroundWorker", "starting service " + this.id);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) this.reactContext.startForegroundService(service);
        else this.reactContext.startService(service);

        while(this.result.equals("running")) { sleep(1000); }

        switch(result) {
            case "failure" : return Result.failure();
            case "retry"   : return Result.retry();
            default        : return Result.success();
        }
    }
}
