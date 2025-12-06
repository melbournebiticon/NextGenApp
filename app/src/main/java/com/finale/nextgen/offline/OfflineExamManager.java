package com.finale.nextgen.offline;

import android.content.Context;
import android.util.Log;

import java.util.List;

/**
 * Handles offline exam saving & loading
 * so activities don't duplicate database code.
 */
public class OfflineExamManager {

    private final AppDatabase db;

    public OfflineExamManager(Context context) {
        db = AppDatabase.getInstance(context);
    }

    /** ---------------------------------------------------
     *  SAVE QUESTIONS TO OFFLINE (REPLACES OLD DATA)
     * --------------------------------------------------- */
    public void saveQuestions(String examId, List<QuestionEntity> questions) {
        new Thread(() -> {
            try {
                // Clear old cached questions for this exam
                db.questionDao().deleteByExamId(examId);

                // Insert new ones
                db.questionDao().insertAll(questions);

                Log.d("OfflineExamManager", "Saved " + questions.size() + " questions for examId=" + examId);
            } catch (Exception e) {
                Log.e("OfflineExamManager", "Error saving questions: ", e);
            }
        }).start();
    }

    /** ---------------------------------------------------
     *  LOAD QUESTIONS FROM OFFLINE
     * --------------------------------------------------- */
    public List<QuestionEntity> loadQuestions(String examId) {
        try {
            List<QuestionEntity> result = db.questionDao().getQuestionsByExamId(examId);
            Log.d("OfflineExamManager", "Loaded " + result.size() + " cached questions for examId=" + examId);
            return result;
        } catch (Exception e) {
            Log.e("OfflineExamManager", "Error loading questions: ", e);
            return null;
        }
    }

    /** ---------------------------------------------------
     *  CHECK IF EXAM HAS OFFLINE QUESTIONS
     * --------------------------------------------------- */
    public boolean hasCachedQuestions(String examId) {
        try {
            int count = db.questionDao().countByExamId(examId);
            Log.d("OfflineExamManager", "ExamId=" + examId + " cacheCount=" + count);
            return count > 0;
        } catch (Exception e) {
            Log.e("OfflineExamManager", "Error checking cache: ", e);
            return false;
        }
    }
}
