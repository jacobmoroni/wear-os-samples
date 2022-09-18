package com.samsung.sdc21.stepcount;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.libraries.healthdata.HealthDataClient;
import com.google.android.libraries.healthdata.HealthDataService;
import com.google.android.libraries.healthdata.data.ReadAggregatedDataResponse;
import com.google.android.libraries.healthdata.permission.Permission;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.samsung.sdc21.stepcount.databinding.ActivityMainBinding;

import java.util.Set;

import javax.annotation.ParametersAreNonnullByDefault;

public class MainActivity extends Activity {
    public static final String APP_TAG = "StepCount";
    private StepsReader stepsReader = null;
    private Permissions permissions = null;

    private Button refreshStepsButton;
    private TextView editHealthStepsCount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ActivityMainBinding binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        refreshStepsButton = binding.btnRefresh;
        editHealthStepsCount = binding.editHealthStepsCount;

        if (!HealthDataService.isHealthDataApiSupported()) {
            Toast.makeText(
                    this,
                    "Health Platform not available, make sure you're on Samsung device running Android"
                            + " Watch 4 and above",
                    Toast.LENGTH_LONG)
                    .show();
            finish();
        }

        HealthDataClient healthDataClient = HealthDataService.getClient(this);
        stepsReader = new StepsReader(healthDataClient);
        permissions = new Permissions(healthDataClient);

        readStepsWithPermissionsCheck();
    }

    public void onRefresh(View view) {
        readStepsWithPermissionsCheck();
    }

    private void permissionsExceptionHandler(PermissionsException exception) {
        Log.e(APP_TAG, "PermissionsException with message: " + exception.getMessage());
        MainActivity.this.runOnUiThread(() -> Toast.makeText(
                MainActivity.this,
                R.string.read_permissions_error,
                Toast.LENGTH_LONG)
                .show());
    }

    private void onPermissionsFailureHandler(Throwable t) {
        Log.e(APP_TAG, "Callback failed with message: " + t.toString());
        MainActivity.this.runOnUiThread(() -> Toast.makeText(
                MainActivity.this,
                R.string.read_permissions_error,
                Toast.LENGTH_LONG)
                .show());
    }

    private void readStepsWithPermissionsCheck() {
        try {
            ListenableFuture<Set<Permission>> permissionFuture = permissions.getGrantedPermissions();
            Futures.addCallback(permissionFuture, new FutureCallback<Set<Permission>>() {
                        @Override
                        public void onSuccess(@Nullable Set<Permission> result) {
                            if (permissions.arePermissionsGranted(result)) {
                                Log.d(APP_TAG, "All permissions granted. Read steps.");
                                readSteps();
                            } else {
                                Log.d(APP_TAG, "Permissions not granted. Request Permissions.");
                                readStepsWithRequestPermissions();
                            }
                        }

                        @Override
                        @ParametersAreNonnullByDefault
                        public void onFailure(Throwable t) {
                            onPermissionsFailureHandler(t);
                        }
                    },
                    ContextCompat.getMainExecutor(this /*context*/));
        } catch (PermissionsException exception) {
            permissionsExceptionHandler(exception);
        }
    }

    private void readStepsWithRequestPermissions() {
        try {
            ListenableFuture<Set<Permission>> requestPermissionFuture = permissions.requestPermissions();
            Futures.addCallback(requestPermissionFuture, new FutureCallback<Set<Permission>>() {
                        @Override
                        public void onSuccess(@Nullable Set<Permission> result) {
                            if (permissions.arePermissionsGranted(result)) {
                                Log.d(APP_TAG, "All permissions granted. Read steps.");
                                readSteps();
                            } else {
                                Log.e(APP_TAG, "Permissions not granted. Can't read steps.");
                            }
                        }

                        @Override
                        @ParametersAreNonnullByDefault
                        public void onFailure(Throwable t) {
                            onPermissionsFailureHandler(t);
                        }
                    },
                    ContextCompat.getMainExecutor(this /*context*/));
        } catch (PermissionsException exception) {
            permissionsExceptionHandler(exception);
        }
    }

    private void readSteps() {
        try {
            refreshStepsButton.setEnabled(false);
            ListenableFuture<ReadAggregatedDataResponse> readFuture = stepsReader.readAggregatedData();

            Futures.addCallback(readFuture, new FutureCallback<ReadAggregatedDataResponse>() {
                        @Override
                        public void onSuccess(@Nullable ReadAggregatedDataResponse result) {
                            long steps = stepsReader.readStepsFromAggregatedDataResponse(result);
                            final String stepsStr = Long.toString(steps);
                            Log.d(APP_TAG, "Today steps count: " + steps);
                            runOnUiThread(() -> editHealthStepsCount.setText(stepsStr));
                            runOnUiThread(() -> refreshStepsButton.setEnabled(true));
                        }

                        @Override
                        @ParametersAreNonnullByDefault
                        public void onFailure(Throwable t) {
                            Log.e(APP_TAG, "readAggregatedData Callback failed with message: " + t);
                            MainActivity.this.runOnUiThread(() -> Toast.makeText(
                                    MainActivity.this,
                                    R.string.read_steps_failed,
                                    Toast.LENGTH_LONG)
                                    .show());
                            runOnUiThread(() -> refreshStepsButton.setEnabled(true));
                        }
                    },
                    ContextCompat.getMainExecutor(this /*context*/));
        } catch (StepsReaderException exception) {
            Log.e(APP_TAG, "StepsReaderException with message: " + exception.getMessage());
            MainActivity.this.runOnUiThread(() -> Toast.makeText(
                    MainActivity.this,
                    R.string.read_steps_failed,
                    Toast.LENGTH_LONG)
                    .show());
            runOnUiThread(() -> refreshStepsButton.setEnabled(true));
        }
    }
}