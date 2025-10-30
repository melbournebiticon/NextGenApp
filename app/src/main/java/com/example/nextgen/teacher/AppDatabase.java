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
 * Handles Exam and Question tables with migration support.
 */
@Database(entities = { Exam.class, Question.class }, version = 11, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    private static final String DB_NAME = "app_database.db";
    private static volatile AppDatabase INSTANCE;

    // === DAO Access ===
    public abstract ExamDao examDao();
    public abstract QuestionDao questionDao();

    // === Singleton Instance ===
    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {

                    // ✅ Migration from version 9 → 10
                    Migration MIGRATION_9_10 = new Migration(9, 10) {
                        @Override
                        public void migrate(@NonNull SupportSQLiteDatabase database) {
                            // 🔹 Add displayNumber column if it doesn’t exist yet
                            database.execSQL(
                                    "ALTER TABLE questions ADD COLUMN displayNumber INTEGER NOT NULL DEFAULT 0"
                            );

                            // 🔹 Optional future-proofing for Exam table
                            database.execSQL(
                                    "ALTER TABLE exams ADD COLUMN durationMinutes INTEGER NOT NULL DEFAULT 0"
                            );
                        }
                    };

                    INSTANCE = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    DB_NAME
                            )
                            // 🚀 Automatically reset if no valid migration found
                            .fallbackToDestructiveMigration()
                            // ⚠️ For dev/testing only (safe here)
                            .allowMainThreadQueries()
                            // ✅ Migration support
                            .addMigrations(MIGRATION_9_10)
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
