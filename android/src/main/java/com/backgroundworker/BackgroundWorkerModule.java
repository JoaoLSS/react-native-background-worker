package com.backgroundworker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.RequiresApi;
import androidx.core.os.HandlerCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.WorkRequest;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class BackgroundWorkerModule extends ReactContextBaseJavaModule {

    static ReactApplicationContext context;
    private HashMap<String, Observer<WorkInfo>> observers = new HashMap<>();
    private HashMap<String, ReadableMap> workers = new HashMap<>();

    public BackgroundWorkerModule(ReactApplicationContext reactContext) {
        super(reactContext);
        BackgroundWorkerModule.context = reactContext;
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String worker = intent.getStringExtra("worker");
                Bundle extras = intent.getExtras();
                BackgroundWorkerModule.this.getReactApplicationContext().getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                        .emit(worker, extras == null ? null : Arguments.fromBundle(extras));
            }
        };
        LocalBroadcastManager.getInstance(context).registerReceiver(receiver, new IntentFilter("DO-WORK"));
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
            if(constraints != null) builder.setConstraints(constraints);

            PeriodicWorkRequest request = builder.build();

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(name, ExistingPeriodicWorkPolicy.REPLACE, request);

        }

    }

    @ReactMethod
    public void result(String id, String result, String workflowResult) {
        Intent intent = new Intent(id + "result");
        intent.putExtra("result", result);
        intent.putExtra("workflowResult", workflowResult);
        LocalBroadcastManager.getInstance(this.getReactApplicationContext()).sendBroadcast(intent);
    }

    @ReactMethod
    private void enqueue(ReadableMap work, Callback sendId) {

        String worker = work.getString("worker");
        ReadableMap _worker = workers.get(worker);

        if(_worker==null) return;
        ReadableMap notification = _worker.getMap("notification");

        if(notification==null) return;
        Data inputData = new Data.Builder()
                .putAll(work.toHashMap())
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
            if(info == null) {
                sendInfo.reject("404", "Work Not Found");
                return;
            }
            WritableMap _info = Arguments.fromBundle(Parser.getWorkInfo(info));
            sendInfo.resolve(_info);
        }
        catch(Exception e) {
            sendInfo.reject(e);
        }
    }

    @ReactMethod
    public void registerListener(final String id) {
        if(observers.containsKey(id)) return;
        final LiveData<WorkInfo> data = WorkManager.getInstance(this.getReactApplicationContext()).getWorkInfoByIdLiveData(UUID.fromString(id));
        final Observer<WorkInfo> observer = new Observer<WorkInfo>() {
            @Override
            public void onChanged(WorkInfo workInfo) {
                context.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                        .emit(id+"info", Arguments.fromBundle(Parser.getWorkInfo(workInfo)));
            }
        };
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                data.observeForever(observer);
                observers.put(id, observer);
            }
        });
        WorkInfo info = data.getValue();
        if(info!=null) context.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(id+"info", Arguments.fromBundle(Parser.getWorkInfo(info)));
    }

    @ReactMethod
    public void removeListener(final String id) {
        final Observer<WorkInfo> observer = observers.get(id);
        if(observer==null) return;
        final LiveData<WorkInfo> data = WorkManager.getInstance(this.getReactApplicationContext()).getWorkInfoByIdLiveData(UUID.fromString(id));
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            @Override public void run() {
                data.removeObserver(observer);
            }
        });
    }

    @ReactMethod
    public void startHeadlessJS(ReadableMap work) {
        Intent headlessJS = new Intent(BackgroundWorkerModule.this.getReactApplicationContext(), BackgroundWorkerService.class);
        Bundle extras = Arguments.toBundle(work);
        if(extras != null) headlessJS.putExtras(extras);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) BackgroundWorkerModule.this.getReactApplicationContext().startForegroundService(headlessJS);
        else BackgroundWorkerModule.this.getReactApplicationContext().startService(headlessJS);
    }

}
