    package com.finale.nextgen.offline;

    import android.content.Context;

    import androidx.annotation.NonNull;
    import androidx.room.Database;
    import androidx.room.Room;
    import androidx.room.RoomDatabase;
    import androidx.room.migration.Migration;
    import androidx.sqlite.db.SupportSQLiteDatabase;

    /**
     * AppDatabase updated to include QuizPendingPresence table (quiz_pending_presences).
     * Bumped version to 8 and added MIGRATION_7_8 to create quiz_pending_presences.
     *
     * Keep your existing migrations (3->4, 4->5, 5->6, 6->7) as shown below.
     */
    @Database(
            entities = {
                    ExamEntity.class,
                    QuestionEntity.class,
                    StudentAnswerEntity.class,
                    PendingSubmission.class,
                    PendingPresence.class,
                    QuizEntity.class,
                    QuizPendingSubmission.class,
                    QuizPendingPresence.class     // <-- new
            },
            version = 8,
            exportSchema = true
    )
    public abstract class AppDatabase extends RoomDatabase {

        private static AppDatabase INSTANCE;

        public abstract ExamDao examDao();
        public abstract QuestionDao questionDao();
        public abstract StudentAnswerDao answerDao();
        public abstract PendingSubmissionDao pendingSubmissionDao();
        public abstract PendingPresenceDao pendingPresenceDao();
        public abstract com.finale.nextgen.offline.QuizDao quizDao();
        public abstract com.finale.nextgen.offline.QuizPendingSubmissionDao quizPendingSubmissionDao();

        // NEW DAO for quiz pending presences
        public abstract com.finale.nextgen.offline.QuizPendingPresenceDao quizPendingPresenceDao();

        // Migration 3 -> 4
        public static final Migration MIGRATION_3_4 = new Migration(3, 4) {
            @Override
            public void migrate(@NonNull SupportSQLiteDatabase database) {
                database.execSQL("ALTER TABLE pending_submissions ADD COLUMN studentName TEXT");
                database.execSQL("ALTER TABLE pending_submissions ADD COLUMN profileImage TEXT");
                database.execSQL("ALTER TABLE pending_submissions ADD COLUMN subjectName TEXT");
                database.execSQL("ALTER TABLE pending_submissions ADD COLUMN teacherName TEXT");
                database.execSQL("ALTER TABLE pending_submissions ADD COLUMN subjectCode TEXT");
                database.execSQL("ALTER TABLE pending_submissions ADD COLUMN deductions INTEGER NOT NULL DEFAULT 0");
            }
        };

        // Migration 4 -> 5
        public static final Migration MIGRATION_4_5 = new Migration(4, 5) {
            @Override
            public void migrate(@NonNull SupportSQLiteDatabase database) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `pending_presences` (" +
                        "`id` TEXT NOT NULL, " +
                        "`examId` TEXT, " +
                        "`studentId` TEXT, " +
                        "`timestamp` INTEGER NOT NULL, " +
                        "`status` TEXT, " +
                        "PRIMARY KEY(`id`))");
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_pending_presences_examId_studentId` ON `pending_presences` (`examId`, `studentId`)");
            }
        };

        // Migration 5 -> 6: cached_quizzes
        public static final Migration MIGRATION_5_6 = new Migration(5, 6) {
            @Override
            public void migrate(@NonNull SupportSQLiteDatabase database) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `cached_quizzes` (" +
                        "`quizId` TEXT NOT NULL, " +
                        "`quizName` TEXT, " +
                        "`teacherName` TEXT, " +
                        "`subjectName` TEXT, " +
                        "`courseName` TEXT, " +
                        "`sectionName` TEXT, " +
                        "`specializationName` TEXT, " +
                        "`yearName` TEXT, " +
                        "`scheduledAt` INTEGER, " +
                        "`durationMinutes` INTEGER, " +
                        "`availableAt` INTEGER, " +
                        "`active` INTEGER, " +
                        "`present` INTEGER, " +
                        "`cachedAt` INTEGER, " +
                        "PRIMARY KEY(`quizId`))");
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_quizzes_cachedAt` ON `cached_quizzes` (`cachedAt`)");
            }
        };

        // Migration 6 -> 7: add quiz_pending_submissions
        public static final Migration MIGRATION_6_7 = new Migration(6, 7) {
            @Override
            public void migrate(@NonNull SupportSQLiteDatabase database) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `quiz_pending_submissions` (" +
                        "`clientSubmissionId` TEXT NOT NULL, " +
                        "`quizId` TEXT, " +
                        "`studentId` TEXT, " +
                        "`computedScore` INTEGER NOT NULL, " +
                        "`maxScore` INTEGER NOT NULL, " +
                        "`timestamp` INTEGER NOT NULL, " +
                        "`status` TEXT, " +
                        "`deductions` TEXT, " +
                        "`answersJson` TEXT, " +
                        "PRIMARY KEY(`clientSubmissionId`))");
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_quiz_pending_submissions_status` ON `quiz_pending_submissions` (`status`)");
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_quiz_pending_submissions_quiz_student` ON `quiz_pending_submissions` (`quizId`, `studentId`)");
            }
        };

        // Migration 7 -> 8: create quiz_pending_presences table
        public static final Migration MIGRATION_7_8 = new Migration(7, 8) {
            @Override
            public void migrate(@NonNull SupportSQLiteDatabase database) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `quiz_pending_presences` (" +
                        "`id` TEXT NOT NULL, " +
                        "`quizId` TEXT, " +
                        "`studentId` TEXT, " +
                        "`timestamp` INTEGER NOT NULL, " +
                        "`status` TEXT, " +
                        "PRIMARY KEY(`id`))");
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_quiz_pending_presences_status` ON `quiz_pending_presences` (`status`)");
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_quiz_pending_presences_quiz_student` ON `quiz_pending_presences` (`quizId`, `studentId`)");
            }
        };

        public static synchronized AppDatabase getInstance(Context ctx) {
            if (INSTANCE == null) {
                INSTANCE = Room.databaseBuilder(
                                ctx.getApplicationContext(),
                                AppDatabase.class,
                                "offline_exam_db"
                        )
                        .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                        .build();
            }
            return INSTANCE;
        }
    }