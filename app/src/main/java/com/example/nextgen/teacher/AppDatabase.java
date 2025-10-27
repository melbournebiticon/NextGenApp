package com.example.nextgen.teacher;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(entities = { Exam.class, Question.class }, version = 9, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    private static final String DB_NAME = "app_database.db";
    private static volatile AppDatabase INSTANCE;

    // === DAO Access ===
    public abstract ExamDao examDao();
    public abstract QuestionDao questionDao();
    // Keep your other 6 DAOs here if you have them
    // public abstract OtherDao1 otherDao1();
    // public abstract OtherDao2 otherDao2();
    // ...

    // === Singleton Instance ===
    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {

                    // Example Migration from version 7 -> 8
                    Migration MIGRATION_7_8 = new Migration(7, 8) {
                        @Override
                        public void migrate(@NonNull SupportSQLiteDatabase database) {
                            // For example, if you added a column to Exam table:
                            // database.execSQL("ALTER TABLE Exam ADD COLUMN newColumn TEXT");
                            // Keep empty if you want destructive migration instead
                        }
                    };

                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, DB_NAME)
                            // Fallback if no migration is provided
                            .fallbackToDestructiveMigration()
                            // Allows queries on main thread; remove in production
                            .allowMainThreadQueries()
                            .addMigrations(MIGRATION_7_8) // Add any future migrations here
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
