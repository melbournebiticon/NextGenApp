package com.example.nextgen.teacher;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

/**
 * Central Room database for the teacher module.
 * Handles Exam, Question, and Quiz tables with migration support.
 */
@Database(entities = { Exam.class, Question.class, Quiz.class }, version = 16, exportSchema = true)
public abstract class AppDatabase extends RoomDatabase {

    private static final String DB_NAME = "app_database.db";
    private static volatile AppDatabase INSTANCE;

    // === DAO Access ===
    public abstract ExamDao examDao();
    public abstract QuestionDao questionDao();
    public abstract QuizDao quizDao();

    // === Singleton Instance ===
    public static AppDatabase getInstance(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    DB_NAME
                            )
                            // Migrations can be chained here
                            .addMigrations(MIGRATION_15_16)
                            // Use fallback only if you accept destructive reset
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }

    // === Migration Example: v15 → v16 (Add Quiz Table) ===
    private static final Migration MIGRATION_15_16 = new Migration(15, 16) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // Create quiz_table if not exists
            database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `quiz_table` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`firebaseKey` TEXT, " +
                            "`quizName` TEXT, " +
                            "`subject` TEXT, " +
                            "`section` TEXT, " +
                            "`durationMinutes` INTEGER NOT NULL DEFAULT 15, " +
                            "`scheduledAt` INTEGER NOT NULL DEFAULT 0, " +
                            "`isActive` INTEGER NOT NULL DEFAULT 0, " +
                            "`teacherId` TEXT" +
                            ")"
            );
        }
    };
}
