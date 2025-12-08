package com.finale.nextgen.work;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.finale.nextgen.offline.AppDatabase;
import com.finale.nextgen.offline.QuizPendingSubmission;
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
 * QuizSyncWorker
 *
 * - Uploads pending quiz submissions from Room table quiz_pending_submissions to Firebase.
 * - Writes to QuizScores/{studentId}/{quizId} (modern path).
 * - Optionally mirrors to legacy Scores/{studentId}/{quizId} during migration.
 *
 * Notes:
 * - This runs on a background thread so Firebase callbacks are synchronized with CountDownLatch.
 * - The worker marks rows "SYNCING" while uploading and deletes them on success.
 */
public class QuizSyncWorker extends Worker {
    private static final String TAG = "QuizSyncWorker";
    private final AppDatabase db;

    // Set to true to ALSO write to legacy "Scores" node while you migrate clients/backend.
    private static final boolean WRITE_LEGACY_SCORES = false;

    public QuizSyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
        db = AppDatabase.getInstance(context);
    }

    @NonNull
    @Override
    public Result doWork() {
        List<QuizPendingSubmission> pending = null;
        try {
            pending = db.quizPendingSubmissionDao().getByStatus("PENDING");
        } catch (Exception e) {
            Log.e(TAG, "Failed to query quiz pending rows: " + e.getMessage(), e);
            return Result.retry();
        }

        if (pending == null || pending.isEmpty()) {
            Log.d(TAG, "No pending quiz submissions to sync.");
            return Result.success();
        }

        for (QuizPendingSubmission p : pending) {
            if (p == null) continue;
            try {
                // mark SYNCING
                p.status = "SYNCING";
                db.quizPendingSubmissionDao().update(p);

                boolean ok = uploadIfNeeded(p);
                if (ok) {
                    db.quizPendingSubmissionDao().deleteById(p.clientSubmissionId);
                    Log.d(TAG, "Deleted pending quiz submission: " + p.clientSubmissionId);
                } else {
                    // transient failure - set back to PENDING and ask WorkManager to retry later
                    p.status = "PENDING";
                    db.quizPendingSubmissionDao().update(p);
                    Log.w(TAG, "Transient failure uploading quiz pending submission: " + p.clientSubmissionId);
                    return Result.retry();
                }
            } catch (Exception e) {
                Log.e(TAG, "Exception while syncing quiz submission: " + e.getMessage(), e);
                try {
                    p.status = "PENDING";
                    db.quizPendingSubmissionDao().update(p);
                } catch (Exception ignored) {}
                return Result.retry();
            }
        }

        return Result.success();
    }

    /**
     * Upload pending submission p if missing.
     * Checks QuizScores first (modern). If missing, checks legacy Scores for compatibility.
     * Writes to QuizScores and optionally mirrors to Scores.
     */
    private boolean uploadIfNeeded(QuizPendingSubmission p) throws InterruptedException {
        if (p == null) return true;
        if (p.studentId == null || p.studentId.trim().isEmpty() || p.quizId == null || p.quizId.trim().isEmpty()) {
            Log.w(TAG, "Skipping upload: missing studentId or quizId for clientSubmissionId=" + p.clientSubmissionId);
            return true; // nothing to upload, but drop the row upstream if desired
        }

        final boolean[] exists = {false};
        final CountDownLatch checkLatch = new CountDownLatch(1);

        DatabaseReference modernRef = FirebaseDatabase.getInstance()
                .getReference("QuizScores")
                .child(p.studentId)
                .child(p.quizId);

        DatabaseReference legacyRef = FirebaseDatabase.getInstance()
                .getReference("Scores")
                .child(p.studentId)
                .child(p.quizId);

        // Check modern path
        modernRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                exists[0] = snapshot.exists();
                checkLatch.countDown();
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "modernRef check cancelled: " + error.getMessage());
                checkLatch.countDown();
            }
        });

        checkLatch.await();

        if (!exists[0]) {
            // check legacy path in case older clients already uploaded there
            final boolean[] legacyExists = {false};
            final CountDownLatch legacyLatch = new CountDownLatch(1);
            legacyRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                    legacyExists[0] = snapshot.exists();
                    legacyLatch.countDown();
                }
                @Override public void onCancelled(@NonNull DatabaseError error) {
                    Log.e(TAG, "legacyRef check cancelled: " + error.getMessage());
                    legacyLatch.countDown();
                }
            });
            legacyLatch.await();
            exists[0] = legacyExists[0];
        }

        if (exists[0]) {
            Log.d(TAG, "Already uploaded: student=" + p.studentId + " quiz=" + p.quizId);
            return true;
        }

        // Prepare payload
        Map<String, Object> payload = new HashMap<>();
        payload.put("score", p.computedScore);
        payload.put("maxScore", p.maxScore);
        payload.put("timestamp", p.timestamp);
        payload.put("clientSubmissionId", p.clientSubmissionId);
        if (p.deductions != null) payload.put("deductions", p.deductions);
        if (p.answersJson != null) payload.put("answersJson", p.answersJson);

        // Write to modern path (QuizScores)
        final boolean[] writeOk = {false};
        final CountDownLatch writeLatch = new CountDownLatch(1);

        modernRef.updateChildren(payload, (error, ref) -> {
            if (error == null) {
                writeOk[0] = true;
                Log.d(TAG, "Wrote to QuizScores for student=" + p.studentId + " quiz=" + p.quizId);
            } else {
                Log.e(TAG, "Failed writing QuizScores: " + error.getMessage());
            }
            writeLatch.countDown();
        });

        writeLatch.await();

        if (!writeOk[0]) {
            Log.w(TAG, "Failed to write to QuizScores for clientSubmissionId=" + p.clientSubmissionId);
            return false;
        }

        // Optionally mirror to legacy Scores (non-blocking but attempted)
        if (WRITE_LEGACY_SCORES) {
            final CountDownLatch mirrorLatch = new CountDownLatch(1);
            legacyRef.updateChildren(payload, (error, ref) -> {
                if (error == null) {
                    Log.d(TAG, "Mirrored to legacy Scores for student=" + p.studentId + " quiz=" + p.quizId);
                } else {
                    Log.w(TAG, "Mirror to legacy Scores failed: " + error.getMessage());
                }
                mirrorLatch.countDown();
            });
            try { mirrorLatch.await(); } catch (InterruptedException ignored) {}
        }

        return true;
    }
}