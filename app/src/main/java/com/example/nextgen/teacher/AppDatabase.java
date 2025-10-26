package com.example.nextgen.teacher;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = { Exam.class, Question.class }, version = 8, exportSchema = false)
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
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, DB_NAME)
                            // Wipes DB if schema changed; good for dev/testing
                            .fallbackToDestructiveMigration()
                            // Allows queries on main thread; remove in production
                            .allowMainThreadQueries()
                            .build();
                }
            }
        }
        return INSTANCE;
    }


}
