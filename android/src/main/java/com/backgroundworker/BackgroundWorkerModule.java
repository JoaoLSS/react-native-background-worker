package com.backgroundworker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
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
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

public class BackgroundWorkerModule extends ReactContextBaseJavaModule {

    static ReactApplicationContext context;

    static final String TAG = "RNBW";

    private HashMap<String, Observer<WorkInfo>> observers = new HashMap<>();

    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            Log.d(TAG, "sending event to JS");

            String worker = intent.getStringExtra("worker");
            String id = intent.getStringExtra("id");
            String payload = intent.getStringExtra("payload");

            Bundle extras = new Bundle();

            extras.putString("id", id);
            extras.putString("payload", payload);

            BackgroundWorkerModule.this.getReactApplicationContext().getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                    .emit(worker, Arguments.fromBundle(extras));
        }
    };

    private HashMap<String, ReadableMap> workers = new HashMap<>();

    public BackgroundWorkerModule(ReactApplicationContext reactContext) {
        super(reactContext);
        BackgroundWorkerModule.context = reactContext;
        LocalBroadcastManager.getInstance(context).registerReceiver(this.receiver, new IntentFilter("DO-WORK"));
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
        try {
            WorkInfo info = WorkManager.getInstance(this.getReactApplicationContext()).getWorkInfoById(UUID.fromString(id)).get();
            WritableMap _info = Arguments.fromBundle(Parser.getWorkInfo(info));
            sendInfo.resolve(_info);
        }
        catch(Exception e) {
            sendInfo.reject(e);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.P)
    @ReactMethod
    public void registerListener(final String id) {
        Log.d(TAG, "registering listener");
        if(observers.containsKey(id)) return;
        final LiveData<WorkInfo> data = WorkManager.getInstance(this.getReactApplicationContext()).getWorkInfoByIdLiveData(UUID.fromString(id));
        final Observer<WorkInfo> observer = new Observer<WorkInfo>() {
            @Override
            public void onChanged(WorkInfo workInfo) {
                context.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                        .emit(id+"info", Arguments.fromBundle(Parser.getWorkInfo(workInfo)));
            }
        };
        Handler.createAsync(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                WorkInfo info = data.getValue();
                data.observeForever(observer);
                observers.put(id, observer);
            }
        });
    }

    @RequiresApi(api = Build.VERSION_CODES.P)
    @ReactMethod
    public void removeListener(final String id) {
        Log.d(TAG, "removing listener");
        final Observer<WorkInfo> observer = observers.get(id);
        if(observer==null) return;
        final LiveData<WorkInfo> data = WorkManager.getInstance(this.getReactApplicationContext()).getWorkInfoByIdLiveData(UUID.fromString(id));
        Handler.createAsync(Looper.getMainLooper()).post(new Runnable() { @Override public void run() { data.removeObserver(observer); } });
    }

    @ReactMethod
    public void startHeadlessJS(ReadableMap work) {

        Log.d(TAG, "starting Headless JS");

        String id = work.getString("id");
        String worker = work.getString("worker");
        String payload = work.getString("payload");

        Intent headlessJS = new Intent(BackgroundWorkerModule.this.getReactApplicationContext(), BackgroundWorkerService.class);

        headlessJS.putExtra("id", id);
        headlessJS.putExtra("worker", worker);
        headlessJS.putExtra("payload", payload);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) BackgroundWorkerModule.this.getReactApplicationContext().startForegroundService(headlessJS);
        else BackgroundWorkerModule.this.getReactApplicationContext().startService(headlessJS);
    }

}
