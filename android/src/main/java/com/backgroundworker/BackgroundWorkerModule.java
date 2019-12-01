
package com.backgroundworker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.RequiresApi;
import androidx.core.os.HandlerCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.Operation;
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

import org.json.JSONObject;

import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;

public class BackgroundWorkerModule extends ReactContextBaseJavaModule {

    static ReactApplicationContext context;
    private HashMap<String, ReadableMap> queuedWorkers = new HashMap<>();
    private HashMap<String, Constraints> queuedConstraints = new HashMap<>();

    BackgroundWorkerModule(ReactApplicationContext reactContext) {
        super(reactContext);
        context = reactContext;
    }

    @Nonnull
    @Override
    public String getName() {
        return "BackgroundWorker";
    }

    @ReactMethod
    public void registerWorker(ReadableMap worker, ReadableMap constraints, Promise p) {

        String type = worker.getString("type");
        String name = worker.getString("name");

        if(name == null || type == null) {
            p.reject("ERROR", "missing worker info");
            return;
        }

        if(type.equals("queued")) {

            Constraints _constraints = Parser.getConstraints(constraints);
            if(_constraints!=null) queuedConstraints.put(name, _constraints);

            queuedWorkers.put(name, worker);
            p.resolve(null);
            return;

        }
        if(type.equals("periodic")) {

            int repeatInterval = worker.getInt("repeatInterval");
            Constraints _constraints = Parser.getConstraints(constraints);

            PeriodicWorkRequest.Builder builder = new PeriodicWorkRequest.Builder(BackgroundWorker.class, Math.max(15, repeatInterval), TimeUnit.MINUTES);
            if(_constraints!=null) builder.setConstraints(_constraints);

            Data inputData = new Data.Builder()
                    .putAll(worker.toHashMap())
                    .build();

            builder.setInputData(inputData);

            PeriodicWorkRequest request = builder.build();

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(name, ExistingPeriodicWorkPolicy.REPLACE, request);
            p.resolve(request.getId().toString());
            return;

        }
        p.reject("ERROR","incompatible worker type");

    }

    @ReactMethod
    public void enqueue(String worker, String payload, Promise p) {

        ReadableMap _worker = queuedWorkers.get(worker);
        Constraints _constraints = queuedConstraints.get(worker);

        if(_worker==null) {
            p.reject("ERROR", "worker not registered");
            return;
        }

        Data inputData = new Data.Builder()
                .putAll(_worker.toHashMap())
                .putString("payload", payload)
                .build();

        OneTimeWorkRequest.Builder builder = new OneTimeWorkRequest.Builder(BackgroundWorker.class)
                .setInputData(inputData);

        if(_constraints!=null) builder.setConstraints(_constraints);

        WorkRequest request = builder.build();

        p.resolve(request.getId().toString());

        WorkManager.getInstance(context).enqueue(request);

    }

    @ReactMethod
    public void startHeadlessTask(ReadableMap workConfiguration) {
        Intent headlessIntent = new Intent(context, BackgroundWorkerService.class);
        Bundle extras = Arguments.toBundle(workConfiguration);
        if(extras!=null) headlessIntent.putExtras(extras);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O)
            BackgroundWorkerModule.this.getReactApplicationContext().startForegroundService(headlessIntent);
        else BackgroundWorkerModule.this.getReactApplicationContext().startService(headlessIntent);
    }

    @ReactMethod
    public void result(String id, String value, String result) {
        Intent intent = new Intent(id + "result");
        intent.putExtra("result", result);
        intent.putExtra("value", value);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }

}