package com.backgroundworker;

import android.os.Bundle;
import android.util.Log;

import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.NetworkType;
import androidx.work.WorkInfo;

import com.facebook.react.bridge.ReadableMap;

import javax.annotation.Nullable;

public class Parser {

    static private NetworkType getNetworkType(String networkType) {

        switch(networkType) {
            case "connected" :  return NetworkType.CONNECTED;
            case "metered":     return NetworkType.METERED;
            case "notRoaming":  return NetworkType.NOT_ROAMING;
            case "unmetered":   return NetworkType.UNMETERED;
            default:            return NetworkType.NOT_REQUIRED;
        }

    }

    static public Constraints getConstraints(@Nullable  ReadableMap constraints) {

        if(constraints==null) return null;

        final NetworkType networkType = getNetworkType(constraints.hasKey("network") ? constraints.getString("network") : "notRequired");

        final boolean requiresCharging      = constraints.hasKey("battery") && constraints.getString("battery").equals("charging");
        final boolean requiresDeviceIdle    = constraints.hasKey("idle"   ) && constraints.getBoolean("idle");
        final boolean requiresStorageNotLow = constraints.hasKey("storage") && constraints.getBoolean("storage");
        final boolean requiresBatteryNotLow = constraints.hasKey("battery") && constraints.getString("battery").equals("notLow");

        Log.d("networkType"             , networkType.toString());
        Log.d("requiresCharging"        , requiresCharging      ? "true" : "false");
        Log.d("requiresDeviceIdle"      , requiresDeviceIdle    ? "true" : "false");
        Log.d("requiresStorageNotLow"   , requiresStorageNotLow ? "true" : "false");
        Log.d("requiresBatteryNotLow"   , requiresBatteryNotLow ? "true" : "false");

        return new Constraints.Builder()
                .setRequiredNetworkType(networkType)
                .setRequiresCharging(requiresCharging)
                .setRequiresDeviceIdle(requiresDeviceIdle)
                .setRequiresStorageNotLow(requiresStorageNotLow)
                .setRequiresBatteryNotLow(requiresBatteryNotLow)
                .build();

    }

    static private String getWorkState(WorkInfo.State state) {
        switch (state) {
            case FAILED: return "failed";
            case BLOCKED: return "blocked";
            case RUNNING: return "running";
            case ENQUEUED: return "enqueued";
            case CANCELLED: return "cancelled";
            case SUCCEEDED: return "succeeded";
            default: return "unknown";
        }
    }

    static public Bundle getWorkInfo(WorkInfo info) {

        Bundle _info = new Bundle();

        _info.putString("state", getWorkState(info.getState()));
        _info.putInt("attempts", info.getRunAttemptCount());
        _info.putString("outputData", info.getOutputData().getString("outputData"));

        return _info;
    }

}
