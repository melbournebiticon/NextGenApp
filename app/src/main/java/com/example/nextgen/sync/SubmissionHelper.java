package com.example.nextgen.sync;

import android.content.Context;
import android.util.Log;

import com.example.nextgen.offline.AppDatabase;
import com.example.nextgen.offline.PendingSubmission;
import com.example.nextgen.teacher.Question;
import com.google.gson.Gson;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Helper to create a PendingSubmission from answers and enqueue sync.
 * Call this from your TakeExamActivity after computing local score.
 */
public class SubmissionHelper {

    private static final String TAG = "SubmissionHelper";

    public static void saveSubmissionLocallyAndEnqueue(Context ctx,
                                                       String examId,
                                                       String studentId,
                                                       List<Question> questionList,
                                                       int computedScore,
                                                       int maxScore) {

        // Build answers map (example structure: questionId -> studentAnswer)
        Map<String, String> answers = new HashMap<>();
        for (Question q : questionList) {
            String key = String.valueOf(q.getDisplayNumber()); // or use q.getId() if present
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

        // persist
        new Thread(() -> {
            AppDatabase db = AppDatabase.getInstance(ctx);
            db.pendingSubmissionDao().insert(p);
            Log.d(TAG, "Saved pending submission locally: " + p.clientSubmissionId);

            // request immediate sync attempt
            SyncManager.enqueueImmediateSubmissionSync(ctx);
        }).start();
    }

    // Example deterministic local scoring function
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