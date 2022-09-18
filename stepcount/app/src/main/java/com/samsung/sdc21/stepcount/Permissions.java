package com.samsung.sdc21.stepcount;

import androidx.annotation.Nullable;

import com.google.android.libraries.healthdata.HealthDataClient;
import com.google.android.libraries.healthdata.data.IntervalDataTypes;
import com.google.android.libraries.healthdata.permission.AccessType;
import com.google.android.libraries.healthdata.permission.Permission;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.HashSet;
import java.util.Set;

public class Permissions {
    private final HealthDataClient healthDataClient;
    private final Set<Permission> permissions = new HashSet<>();

    public Permissions(HealthDataClient healthDataClient) {
        this.healthDataClient = healthDataClient;

        Permission stepsReadPermission = Permission.builder()
                .setDataType(IntervalDataTypes.STEPS)
                .setAccessType(AccessType.READ)
                .build();
        permissions.add(stepsReadPermission);
    }

    public ListenableFuture<Set<Permission>> getGrantedPermissions() throws PermissionsException {
        if (healthDataClient == null) {
            throw new PermissionsException("health client is null");
        }
        return healthDataClient.getGrantedPermissions(permissions);
    }


    public ListenableFuture<Set<Permission>> requestPermissions() throws PermissionsException {
        if (healthDataClient == null) {
            throw new PermissionsException("health client is null");
        }
        return healthDataClient.requestPermissions(permissions);
    }

    public boolean arePermissionsGranted(@Nullable Set<Permission> result) {
        if (result == null) {
            return false;
        }

        return result.containsAll(permissions);
    }
}
