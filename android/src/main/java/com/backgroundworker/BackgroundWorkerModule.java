package com.backgroundworker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.work.Configuration;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.ListenableWorker;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.WorkRequest;
import androidx.work.WorkerFactory;
import androidx.work.WorkerParameters;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class BackgroundWorkerModule extends ReactContextBaseJavaModule {

    static final String TAG = "RNBW";

    static ReactApplicationContext context;

    static Map<String, WritableMap> workers = new HashMap();

    public BackgroundWorkerModule(ReactApplicationContext reactContext) {
        super(reactContext);
        BackgroundWorkerModule.context = reactContext;
    }

    @Override
    public String getName() {
        return "BackgroundWorker";
    }

    @ReactMethod
    public void setWorker(ReadableMap worker) {

        String name = worker.getString("name");
        String type = worker.getString("type");

        workers.put(name, Arguments.fromBundle(Arguments.toBundle(worker)));

        if(type.equals("periodic")) {

            Constraints constraints = Parser.getConstraints(worker.getMap("constraints"));

            PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(BackgroundWorker.class, 15, TimeUnit.MINUTES)
                    .setConstraints(constraints)
                    .build();

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(name, ExistingPeriodicWorkPolicy.REPLACE, request);

        }

    }

    @ReactMethod
    public void result(String id, String result) {
        Intent intent = new Intent(id + "result");
        intent.putExtra("result", result);
        LocalBroadcastManager.getInstance(this.getReactApplicationContext()).sendBroadcast(intent);
    }

    @ReactMethod
    private void enqueue(ReadableMap work, Callback sendId) {

        String worker = work.getString("worker");
        Map payload = work.getMap("payload").toHashMap();

        Constraints constraints = Parser.getConstraints(workers.get(worker).getMap("constraints"));

        WorkRequest request = new OneTimeWorkRequest.Builder(BackgroundWorker.class)
                .setConstraints(constraints)
                .setInputData(new Data.Builder().putAll(payload).build())
                .addTag(worker)
                .build();

        String id = request.getId().toString();

        sendId.invoke(id);

        WorkManager.getInstance(context).enqueue(request);

    }

    @ReactMethod
    public void cancelWorker(String id) {
        UUID _id = UUID.fromString(id);
        WorkManager.getInstance(context).cancelWorkById(_id);
    }

    @ReactMethod
    public void workInfo(String id, Promise sendInfo) {
    }

}
