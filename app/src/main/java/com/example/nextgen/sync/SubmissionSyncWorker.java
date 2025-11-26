package com.example.nextgen.sync;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.nextgen.offline.AppDatabase;
import com.example.nextgen.offline.PendingSubmission;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

/**
 * Worker that uploads pending submissions to Firebase when network is available.
 * Simplified approach: uses blocking CountDownLatch for Firebase callbacks because Worker runs on background thread.
 */
public class SubmissionSyncWorker extends Worker {

    private static final String TAG = "SubmissionSyncWorker";
    private final AppDatabase db;

    public SubmissionSyncWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        db = AppDatabase.getInstance(context);
    }

    @NonNull
    @Override
    public Result doWork() {
        List<PendingSubmission> pending = db.pendingSubmissionDao().getByStatus("PENDING");
        if (pending == null || pending.isEmpty()) return Result.success();

        for (PendingSubmission p : pending) {
            try {
                // mark SYNCING
                p.status = "SYNCING";
                db.pendingSubmissionDao().update(p);

                boolean success = uploadIfNeeded(p);
                if (success) {
                    db.pendingSubmissionDao().deleteById(p.clientSubmissionId);
                } else {
                    // transient failure - set back to PENDING and ask WorkManager to retry later
                    p.status = "PENDING";
                    db.pendingSubmissionDao().update(p);
                    return Result.retry();
                }
            } catch (Exception e) {
                Log.e(TAG, "Exception syncing submission: " + e.getMessage(), e);
                // set back to pending and retry
                p.status = "PENDING";
                db.pendingSubmissionDao().update(p);
                return Result.retry();
            }
        }
        return Result.success();
    }

    /**
     * Check if Scores/{studentId}/{examId} exists. If not, write the score.
     * Uses blocking waits (CountDownLatch) because this runs inside a Worker background thread.
     */
    private boolean uploadIfNeeded(PendingSubmission p) throws InterruptedException {
        final boolean[] exists = {false};
        final CountDownLatch latch = new CountDownLatch(1);

        DatabaseReference scoreRef = FirebaseDatabase.getInstance()
                .getReference("Scores")
                .child(p.studentId)
                .child(p.examId);

        scoreRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                exists[0] = snapshot.exists();
                latch.countDown();
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "check exist cancelled: " + error.getMessage());
                latch.countDown();
            }
        });

        latch.await();

        if (exists[0]) {
            Log.d(TAG, "Score already present for student=" + p.studentId + " exam=" + p.examId + " — removing local pending.");
            return true; // nothing to upload
        }

        // Prepare payload
        Map<String, Object> payload = new HashMap<>();
        payload.put("score", p.computedScore);
        payload.put("maxScore", p.maxScore);
        payload.put("timestamp", p.timestamp);
        payload.put("clientSubmissionId", p.clientSubmissionId);
        // You may optionally store answers / deductions / metadata under Attempts node

        final boolean[] writeSuccess = {false};
        final CountDownLatch writeLatch = new CountDownLatch(1);

        scoreRef.updateChildren(payload, (error, ref) -> {
            if (error == null) {
                writeSuccess[0] = true;
                Log.d(TAG, "Uploaded score for student=" + p.studentId + " exam=" + p.examId);
            } else {
                Log.e(TAG, "Failed to upload score: " + error.getMessage());
            }
            writeLatch.countDown();
        });

        // wait for completion (small timeout could be added if desired)
        writeLatch.await();
        return writeSuccess[0];
    }
}