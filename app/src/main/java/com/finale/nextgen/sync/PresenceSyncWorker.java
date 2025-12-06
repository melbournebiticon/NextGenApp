package com.finale.nextgen.sync;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.finale.nextgen.offline.AppDatabase;
import com.finale.nextgen.offline.PendingPresence;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class PresenceSyncWorker extends Worker {
    private static final String TAG = "PresenceSyncWorker";
    private static final long FIREBASE_CALLBACK_TIMEOUT_SEC = 25L;
    private final AppDatabase db;

    public PresenceSyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
        db = AppDatabase.getInstance(context.getApplicationContext());
    }

    @NonNull
    @Override
    public Result doWork() {
        // Ensure user signed-in (if your rules require auth)
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            Log.w(TAG, "No Firebase user signed in — retrying later.");
            return Result.retry();
        }

        List<PendingPresence> pending = db.pendingPresenceDao().getByStatus("PENDING");
        if (pending == null || pending.isEmpty()) return Result.success();

        boolean sawTransientFailure = false;

        for (PendingPresence p : pending) {
            try {
                db.pendingPresenceDao().updateStatus(p.id, "SYNCING");

                boolean ok = uploadPresence(p);
                if (ok) {
                    db.pendingPresenceDao().deleteById(p.id);
                    Log.d(TAG, "Synced presence " + p.id);
                } else {
                    // transient failure -> reset to PENDING so it'll retry
                    db.pendingPresenceDao().updateStatus(p.id, "PENDING");
                    sawTransientFailure = true;
                }
            } catch (Exception e) {
                Log.e(TAG, "Exception while syncing presence: " + e.getMessage(), e);
                try { db.pendingPresenceDao().updateStatus(p.id, "PENDING"); } catch (Exception ignored){ }
                sawTransientFailure = true;
            }
        }

        return sawTransientFailure ? Result.retry() : Result.success();
    }

    private boolean uploadPresence(PendingPresence p) throws InterruptedException {
        final boolean[] success = {false};
        final CountDownLatch latch = new CountDownLatch(1);

        DatabaseReference ref = FirebaseDatabase.getInstance()
                .getReference("ExamStudents")
                .child(p.examId)
                .child(p.studentId)
                .child("present");

        ref.setValue(true, (error, r) -> {
            if (error == null) {
                success[0] = true;
                Log.d(TAG, "Set present true for exam=" + p.examId + " student=" + p.studentId);
            } else {
                Log.e(TAG, "Failed to set present: " + error.getMessage());
                // treat permission denied as permanent failure
                if (error.getCode() == DatabaseError.PERMISSION_DENIED) {
                    // mark as FAILED so it won't retry forever
                    try { db.pendingPresenceDao().updateStatus(p.id, "FAILED"); } catch (Exception ignored) {}
                }
            }
            latch.countDown();
        });

        // wait with timeout
        boolean arrived = latch.await(FIREBASE_CALLBACK_TIMEOUT_SEC, TimeUnit.SECONDS);
        if (!arrived) {
            Log.w(TAG, "Timeout while writing presence for " + p.id);
            return false;
        }
        return success[0];
    }
}