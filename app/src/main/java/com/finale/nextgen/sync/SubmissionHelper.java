package com.finale.nextgen.sync;

import android.content.Context;
import android.util.Log;

import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.finale.nextgen.offline.AppDatabase;
import com.finale.nextgen.offline.ExamEntity;
import com.finale.nextgen.offline.PendingSubmission;
import com.finale.nextgen.offline.QuizPendingSubmission;
import com.finale.nextgen.teacher.Question;
import com.google.gson.Gson;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;


public class SubmissionHelper {

    private static final String TAG = "SubmissionHelper";

    public static void saveSubmissionLocallyAndEnqueue(Context ctx,
                                                       String examId,
                                                       String studentId,
                                                       List<Question> questionList,
                                                       int computedScore,
                                                       int maxScore) {

        // existing exam flow (unchanged)
        final Context appCtx = ctx.getApplicationContext();

        // Build answers map (questionDisplayNumber -> answer)
        Map<String, String> answers = new HashMap<>();
        for (Question q : questionList) {
            String key = String.valueOf(q.getDisplayNumber() == 0 ? UUID.randomUUID().toString() : q.getDisplayNumber());
            answers.put(key, q.getStudentAnswer() == null ? "" : q.getStudentAnswer());
        }

        PendingSubmission p = new PendingSubmission();
        p.clientSubmissionId = UUID.randomUUID().toString();
        p.examId = examId;
        p.studentId = studentId;
        p.answersJson = new Gson().toJson(answers);
        p.computedScore = computedScore;
        p.maxScore = maxScore;
        p.timestamp = System.currentTimeMillis();
        p.status = "PENDING";
        p.deductions = 0;

        // Try to populate optional metadata from cached ExamEntity (fast, local)
        new Thread(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(appCtx);
                try {
                    ExamEntity exam = db.examDao().getExamById(examId);
                    if (exam != null) {
                        p.subjectName = exam.examTitle != null ? exam.examTitle : exam.courseName;
                        p.teacherName = exam.teacherName;
                        p.subjectCode = exam.courseName; // adjust to real field if available
                    }
                } catch (Exception ignored) {
                    // continue even if exam not present
                }

                db.pendingSubmissionDao().insert(p);
                Log.d(TAG, "Saved pending submission locally: " + p.clientSubmissionId);

                // request immediate sync attempt (use WorkManager enqueuing existing worker)
                SyncManager.enqueueImmediateSubmissionSync(appCtx);

            } catch (Exception e) {
                Log.e(TAG, "Failed to save pending submission: " + e.getMessage(), e);
            }
        }).start();
    }

    /**
     * New: save quiz submission locally into quiz_pending_submissions and enqueue QuizSyncWorker.
     *
     * Call this from your quiz UI (TakeQuizActivity) instead of the exam method.
     */
    public static void saveQuizSubmissionLocallyAndEnqueue(Context ctx,
                                                           String quizId,
                                                           String studentId,
                                                           List<Question> questionList,
                                                           int computedScore,
                                                           int maxScore) {

        final Context appCtx = ctx.getApplicationContext();

        // Build answers map (questionDisplayNumber -> answer)
        Map<String, String> answers = new HashMap<>();
        for (Question q : questionList) {
            String key = String.valueOf(q.getDisplayNumber() == 0 ? UUID.randomUUID().toString() : q.getDisplayNumber());
            answers.put(key, q.getStudentAnswer() == null ? "" : q.getStudentAnswer());
        }

        QuizPendingSubmission p = new QuizPendingSubmission();
        p.clientSubmissionId = UUID.randomUUID().toString();
        p.quizId = quizId;
        p.studentId = studentId;
        p.answersJson = new Gson().toJson(answers);
        p.computedScore = computedScore;
        p.maxScore = maxScore;
        p.timestamp = System.currentTimeMillis();
        p.status = "PENDING";
        p.deductions = null;

        // Save and enqueue quiz sync
        new Thread(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(appCtx);
                db.quizPendingSubmissionDao().insert(p);
                Log.d(TAG, "Saved quiz pending submission locally: " + p.clientSubmissionId);

                // Enqueue QuizSyncWorker for immediate attempt
                OneTimeWorkRequest w = new OneTimeWorkRequest.Builder(com.finale.nextgen.work.QuizSyncWorker.class).build();
                WorkManager.getInstance(appCtx)
                        .enqueueUniqueWork("quiz-sync-immediate", ExistingWorkPolicy.KEEP, w);

            } catch (Exception e) {
                Log.e(TAG, "Failed to save quiz pending submission: " + e.getMessage(), e);
            }
        }).start();
    }

    // Deterministic local scoring function (unchanged)
    public static int computeLocalScore(List<Question> questions) {
        int correct = 0;
        for (Question q : questions) {
            String studentAns = q.getStudentAnswer();
            String correctAns = q.getCorrectAnswer();
            if (studentAns != null && correctAns != null && studentAns.equalsIgnoreCase(correctAns.trim())) {
                correct++;
            }
        }
        return correct;
    }
}