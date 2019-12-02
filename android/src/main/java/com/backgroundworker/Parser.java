package com.backgroundworker;

import android.os.Bundle;

import androidx.work.Constraints;
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

    static Constraints getConstraints(@Nullable ReadableMap constraints) {

        if(constraints==null) return null;

        final NetworkType networkType = getNetworkType(constraints.hasKey("network") ? constraints.getString("network") : "notRequired");

        final boolean requiresCharging      = constraints.hasKey("battery") && constraints.getString("battery").equals("charging");
        final boolean requiresDeviceIdle    = constraints.hasKey("idle"   ) && constraints.getString("idle").equals("idle");
        final boolean requiresStorageNotLow = constraints.hasKey("storage") && constraints.getString("storage").equals("notLow");
        final boolean requiresBatteryNotLow = constraints.hasKey("battery") && constraints.getString("battery").equals("notLow");

        return new Constraints.Builder()
                .setRequiredNetworkType(networkType)
                .setRequiresCharging(requiresCharging)
                .setRequiresDeviceIdle(requiresDeviceIdle)
                .setRequiresStorageNotLow(requiresStorageNotLow)
                .setRequiresBatteryNotLow(requiresBatteryNotLow)
                .build();

    }

    private static String getWorkState(WorkInfo.State state) {
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
        _info.putInt("attemptCount", info.getRunAttemptCount());
        _info.putString("value", info.getOutputData().getString("value"));

        return _info;
    }

}
