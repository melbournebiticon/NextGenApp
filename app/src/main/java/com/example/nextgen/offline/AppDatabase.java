package com.example.nextgen.offline;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

/**
 * AppDatabase updated to include QuizEntity (cached_quizzes) and migration 5 -> 6.
 * Bumped version to 6.
 */
@Database(
        entities = {
                ExamEntity.class,
                QuestionEntity.class,
                StudentAnswerEntity.class,
                PendingSubmission.class,
                PendingPresence.class,
                QuizEntity.class            // <-- added cached_quizzes entity
        },
        version = 6,                    // bumped from 5 -> 6
        exportSchema = true
)
public abstract class AppDatabase extends RoomDatabase {

    private static AppDatabase INSTANCE;

    public abstract ExamDao examDao();
    public abstract QuestionDao questionDao();
    public abstract StudentAnswerDao answerDao();
    public abstract PendingSubmissionDao pendingSubmissionDao();
    public abstract PendingPresenceDao pendingPresenceDao(); // <-- existing
    public abstract com.example.nextgen.offline.QuizDao quizDao(); // <-- new DAO for cached_quizzes

    // Migration 3 -> 4: add new nullable TEXT metadata columns and deductions INTEGER default 0
    public static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // Add nullable TEXT columns for metadata
            database.execSQL("ALTER TABLE pending_submissions ADD COLUMN studentName TEXT");
            database.execSQL("ALTER TABLE pending_submissions ADD COLUMN profileImage TEXT");
            database.execSQL("ALTER TABLE pending_submissions ADD COLUMN subjectName TEXT");
            database.execSQL("ALTER TABLE pending_submissions ADD COLUMN teacherName TEXT");
            database.execSQL("ALTER TABLE pending_submissions ADD COLUMN subjectCode TEXT");
            // Add deductions column with default 0 so existing rows are valid
            database.execSQL("ALTER TABLE pending_submissions ADD COLUMN deductions INTEGER NOT NULL DEFAULT 0");
        }
    };

    // Migration 4 -> 5: create pending_presences table (already present in your file)
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

    // Migration 5 -> 6: create cached_quizzes table for QuizEntity
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

    public static synchronized AppDatabase getInstance(Context ctx) {
        if (INSTANCE == null) {
            INSTANCE = Room.databaseBuilder(
                            ctx.getApplicationContext(),
                            AppDatabase.class,
                            "offline_exam_db"
                    )
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    // If during development you prefer to avoid writing migrations, you can temporarily uncomment:
                    // .fallbackToDestructiveMigration()
                    .build();
        }
        return INSTANCE;
    }
}