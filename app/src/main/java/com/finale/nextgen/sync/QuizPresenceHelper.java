package com.finale.nextgen.sync;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.finale.nextgen.offline.AppDatabase;
import com.finale.nextgen.offline.QuizEntity;
import com.finale.nextgen.offline.QuizPendingPresence;

import java.util.UUID;

import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

/**
 * QuizPresenceHelper - saves quiz-specific pending presence rows and enqueues QuizPresenceSyncWorker.
 *
 * Usage:
 *   QuizPresenceHelper.saveQuizPresenceLocallyAndEnqueue(context, quizId, studentId, optionalMeta);
 */
public class QuizPresenceHelper {
    private static final String TAG = "QuizPresenceHelper";
    public static final String ACTION_QUIZ_PRESENCE_SAVED = "com.finale.nextgen.action.QUIZ_PRESENCE_SAVED";

    public static void saveQuizPresenceLocallyAndEnqueue(Context ctx, String quizId, String studentId, ExamMetadata meta) {
        if (ctx == null || quizId == null || quizId.trim().isEmpty() || studentId == null || studentId.trim().isEmpty()) {
            Log.w(TAG, "Invalid args to saveQuizPresenceLocallyAndEnqueue");
            return;
        }
        final Context appCtx = ctx.getApplicationContext();

        new Thread(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(appCtx);

                QuizPendingPresence p = new QuizPendingPresence();
                p.id = UUID.randomUUID().toString();
                p.quizId = quizId;
                p.studentId = studentId;
                p.timestamp = System.currentTimeMillis();
                p.status = "PENDING";

                db.quizPendingPresenceDao().insert(p);
                Log.d(TAG, "Saved quiz pending presence locally: " + p.id + " quiz=" + quizId + " student=" + studentId);

                // optionally merge metadata into cached quiz row for immediate UI
                try {
                    QuizEntity qe = db.quizDao().getById(quizId);
                    if (qe == null) {
                        qe = new QuizEntity();
                        qe.quizId = quizId;
                        qe.quizName = meta != null ? (meta.examTitle != null ? meta.examTitle : "") : "";
                        qe.cachedAt = System.currentTimeMillis();
                        qe.present = true;
                        qe.active = true; // use 'active' field (exists on QuizEntity) instead of non-existent 'available'
                        db.quizDao().insert(qe);
                    } else {
                        qe.present = true;
                        qe.cachedAt = System.currentTimeMillis();
                        if (qe.quizName == null || qe.quizName.trim().isEmpty()) {
                            qe.quizName = meta != null ? (meta.examTitle != null ? meta.examTitle : qe.quizName) : qe.quizName;
                        }
                        db.quizDao().insert(qe);
                    }
                } catch (Exception ignore) { }

                // enqueue immediate one-time worker (unique name prevents duplicates)
                OneTimeWorkRequest w = new OneTimeWorkRequest.Builder(com.finale.nextgen.work.QuizPresenceSyncWorker.class).build();
                WorkManager.getInstance(appCtx).enqueueUniqueWork("quiz-presence-sync-immediate", ExistingWorkPolicy.KEEP, w);

                // broadcast for UI
                try {
                    Intent i = new Intent(ACTION_QUIZ_PRESENCE_SAVED);
                    i.putExtra("quizId", quizId);
                    i.putExtra("studentId", studentId);
                    appCtx.sendBroadcast(i);
                } catch (Exception ignore) { }

            } catch (Exception e) {
                Log.e(TAG, "Failed to save quiz pending presence: " + e.getMessage(), e);
            }
        }).start();
    }
}