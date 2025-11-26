package com.example.nextgen.offline;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import android.content.Context;

@Database(
        entities = {
                ExamEntity.class,
                QuestionEntity.class,
                StudentAnswerEntity.class
        },
        version = 2
)
public abstract class AppDatabase extends RoomDatabase {

    private static AppDatabase INSTANCE;

    public abstract ExamDao examDao();
    public abstract QuestionDao questionDao();
    public abstract StudentAnswerDao answerDao();

    public static synchronized AppDatabase getInstance(Context ctx) {
        if (INSTANCE == null) {
            INSTANCE = Room.databaseBuilder(
                    ctx.getApplicationContext(),
                    AppDatabase.class,
                    "offline_exam_db"
            ).fallbackToDestructiveMigration().build();
        }
        return INSTANCE;
    }
}
