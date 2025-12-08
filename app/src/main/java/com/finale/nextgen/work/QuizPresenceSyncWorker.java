package com.finale.nextgen.work;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.finale.nextgen.offline.AppDatabase;
import com.finale.nextgen.offline.QuizPendingPresence;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import com.google.firebase.database.ValueEventListener;

import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * Worker that uploads quiz pending presence rows to QuizStudents/{quizId}/{studentId}/present = true.
 * Optionally mirrors to ExamStudents if you need compatibility.
 */
public class QuizPresenceSyncWorker extends Worker {
    private static final String TAG = "QuizPresenceSyncWorker";
    private final AppDatabase db;
    private static final boolean WRITE_LEGACY_EXAMSTUDENTS = false; // set true only temporarily if needed

    public QuizPresenceSyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
        db = AppDatabase.getInstance(context.getApplicationContext());
    }

    @NonNull
    @Override
    public Result doWork() {
        List<QuizPendingPresence> pending;
        try {
            pending = db.quizPendingPresenceDao().getByStatus("PENDING");
        } catch (Exception e) {
            Log.e(TAG, "Failed to query quiz pending presences: " + e.getMessage(), e);
            return Result.retry();
        }

        if (pending == null || pending.isEmpty()) {
            Log.d(TAG, "No quiz pending presences to sync.");
            return Result.success();
        }

        for (QuizPendingPresence p : pending) {
            if (p == null) continue;
            try {
                db.quizPendingPresenceDao().updateStatus(p.id, "SYNCING");

                boolean ok = uploadIfNeeded(p);
                if (ok) {
                    db.quizPendingPresenceDao().deleteById(p.id);
                    Log.d(TAG, "Deleted quiz pending presence: " + p.id);
                } else {
                    db.quizPendingPresenceDao().updateStatus(p.id, "PENDING");
                    return Result.retry();
                }
            } catch (Exception e) {
                Log.e(TAG, "Exception while syncing quiz presence: " + e.getMessage(), e);
                try { db.quizPendingPresenceDao().updateStatus(p.id, "PENDING"); } catch (Exception ignored) {}
                return Result.retry();
            }
        }

        return Result.success();
    }

    private boolean uploadIfNeeded(QuizPendingPresence p) throws InterruptedException {
        if (p == null || p.quizId == null || p.quizId.trim().isEmpty() || p.studentId == null || p.studentId.trim().isEmpty()) {
            Log.w(TAG, "Skipping invalid pending presence: " + (p != null ? p.id : "null"));
            return true;
        }

        final boolean[] exists = {false};
        final CountDownLatch checkLatch = new CountDownLatch(1);

        DatabaseReference modernRef = FirebaseDatabase.getInstance()
                .getReference("QuizStudents")
                .child(p.quizId)
                .child(p.studentId)
                .child("present");

        DatabaseReference legacyRef = FirebaseDatabase.getInstance()
                .getReference("ExamStudents")
                .child(p.quizId)
                .child(p.studentId)
                .child("present");

        // check modern path
        modernRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                exists[0] = snapshot.exists() && Boolean.TRUE.equals(snapshot.getValue(Boolean.class));
                checkLatch.countDown();
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "modern check cancelled: " + error.getMessage());
                checkLatch.countDown();
            }
        });

        checkLatch.await();

        if (!exists[0]) {
            final boolean[] legacyExists = {false};
            final CountDownLatch legacyLatch = new CountDownLatch(1);
            legacyRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                    legacyExists[0] = snapshot.exists() && Boolean.TRUE.equals(snapshot.getValue(Boolean.class));
                    legacyLatch.countDown();
                }
                @Override public void onCancelled(@NonNull DatabaseError error) {
                    Log.e(TAG, "legacy check cancelled: " + error.getMessage());
                    legacyLatch.countDown();
                }
            });
            legacyLatch.await();
            exists[0] = legacyExists[0];
        }

        if (exists[0]) {
            Log.d(TAG, "Presence already uploaded for quiz=" + p.quizId + " student=" + p.studentId);
            return true;
        }

        final boolean[] writeOk = {false};
        final CountDownLatch writeLatch = new CountDownLatch(1);

        modernRef.setValue(true, (error, ref) -> {
            if (error == null) {
                writeOk[0] = true;
                Log.d(TAG, "Wrote present=true to QuizStudents for quiz=" + p.quizId + " student=" + p.studentId);
            } else {
                Log.e(TAG, "Failed to write QuizStudents presence: " + error.getMessage());
            }
            writeLatch.countDown();
        });

        writeLatch.await();

        if (!writeOk[0]) return false;

        if (WRITE_LEGACY_EXAMSTUDENTS) {
            final CountDownLatch mirrorLatch = new CountDownLatch(1);
            legacyRef.setValue(true, (error, ref) -> {
                if (error == null) {
                    Log.d(TAG, "Mirrored presence to ExamStudents for quiz=" + p.quizId + " student=" + p.studentId);
                } else {
                    Log.w(TAG, "Mirror to ExamStudents failed: " + error.getMessage());
                }
                mirrorLatch.countDown();
            });
            try { mirrorLatch.await(); } catch (InterruptedException ignored) {}
        }

        return true;
    }
}