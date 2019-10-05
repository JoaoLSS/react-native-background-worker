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
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class BackgroundWorkerModule extends ReactContextBaseJavaModule {

    static boolean IS_DESTRUCTED = true;

    static final String TAG = "RNBW";

    static ReactApplicationContext context;

    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG,"INVOKING REACT FROM THE DEADE");
            BackgroundWorkerModule.this.getReactApplicationContext().getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                    .emit("WAKE-UP", null);
        }
    };

    private HashMap<String, ReadableMap> workers = new HashMap<>();

    public BackgroundWorkerModule(ReactApplicationContext reactContext) {
        super(reactContext);
        IS_DESTRUCTED = false;
        BackgroundWorkerModule.context = reactContext;
        LocalBroadcastManager.getInstance(getReactApplicationContext()).registerReceiver(this.receiver, new IntentFilter("WAKE-UP"));
    }

    @Override
    public String getName() {
        return "BackgroundWorker";
    }

    @ReactMethod
    public void setWorker(ReadableMap worker) {

        String name = worker.getString("name");
        String type = worker.getString("type");

        this.workers.put(name, worker);

        if(type != null && name != null && type.equals("periodic")) {

            Constraints constraints = Parser.getConstraints(worker.getMap("constraints"));

            PeriodicWorkRequest.Builder builder = new PeriodicWorkRequest.Builder(BackgroundWorker.class, 15, TimeUnit.MINUTES);
            if(constraints != null ) builder.setConstraints(constraints);

            PeriodicWorkRequest request = builder.build();

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
        String payload = work.getString("payload");

        ReadableMap _worker = workers.get(worker);

        if(_worker==null) return;

        ReadableMap notification = _worker.getMap("notification");

        assert notification != null;
        Data inputData = new Data.Builder()
                .putString("worker", worker)
                .putString("payload", payload)
                .putString("title", notification.getString("title"))
                .putString("text", notification.getString("text"))
                .build();

        Constraints constraints = Parser.getConstraints(_worker.getMap("constraints"));
        WorkRequest.Builder builder = new OneTimeWorkRequest.Builder(BackgroundWorker.class)
                .setInputData(inputData);

        if(constraints!=null) builder.setConstraints(constraints);

        WorkRequest request = builder.build();
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
