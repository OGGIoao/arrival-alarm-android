package com.example.arrivalalarm;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;

/**
 * Minimal framework-level lifecycle smoke test without adding AndroidX.
 * The runner fails if MainActivity cannot be created or recreated by Android.
 */
public final class LifecycleInstrumentationRunner extends Instrumentation {
    @Override
    public void onStart() {
        Bundle result = new Bundle();
        try {
            Intent intent = new Intent()
                    .setComponent(new ComponentName(
                            getTargetContext().getPackageName(),
                            "com.example.arrivalalarm.MainActivity"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getTargetContext().startActivity(intent);
            Thread.sleep(500L);
            result.putString("lifecycle", "MainActivity start request completed");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable failure) {
            result.putString("error", failure.toString());
            finish(Activity.RESULT_CANCELED, result);
        }
    }
}
