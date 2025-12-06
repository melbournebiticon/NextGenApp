package com.finale.nextgen.sync;

import android.content.Context;

import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

public class SyncManager {

    public static void enqueueImmediateSubmissionSync(Context context) {
        Context appCtx = context.getApplicationContext();

        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        OneTimeWorkRequest work = new OneTimeWorkRequest.Builder(SubmissionSyncWorker.class)
                .setConstraints(constraints)
                .build();

        // Use a single unique name so we don't enqueue duplicates rapidly
        WorkManager.getInstance(appCtx)
                .enqueueUniqueWork("submission_sync", ExistingWorkPolicy.KEEP, work);
    }

    /**
     * Enqueue a WorkManager job to sync pending presence (QR scans) when network is available.
     * Uses a separate unique work name so presence syncs can be coalesced independently.
     */
    public static void enqueueImmediatePresenceSync(Context context) {
        Context appCtx = context.getApplicationContext();

        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        OneTimeWorkRequest work = new OneTimeWorkRequest.Builder(PresenceSyncWorker.class)
                .setConstraints(constraints)
                .build();

        WorkManager.getInstance(appCtx)
                .enqueueUniqueWork("presence_sync", ExistingWorkPolicy.KEEP, work);
    }
}