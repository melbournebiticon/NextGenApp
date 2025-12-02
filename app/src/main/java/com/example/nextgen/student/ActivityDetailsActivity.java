package com.example.nextgen.student;

import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.example.nextgen.R;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

public class ActivityDetailsActivity extends AppCompatActivity {

    private String activityId;
    private String title;
    private String subjectCode;
    private String subjectName;
    private String teacherName;
    private String description;
    private String dueDate;
    private String mainTerm;
    private String subTerm;
    private String maxScore; // Max Score passed from ActivitiesAdapter

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_activity_details);

        // Get data from Intent
        title = getIntent().getStringExtra("title");
        subjectCode = getIntent().getStringExtra("subjectCode");
        subjectName = getIntent().getStringExtra("subjectName");
        teacherName = getIntent().getStringExtra("teacherName");
        description = getIntent().getStringExtra("description");
        dueDate = getIntent().getStringExtra("dueDate");
        activityId = getIntent().getStringExtra("activityId");
        mainTerm = getIntent().getStringExtra("mainTerm");
        subTerm = getIntent().getStringExtra("subTerm");
        maxScore = getIntent().getStringExtra("maxScore"); // ✅ Max Score

        // Debug log
        Log.d("ActivityDetailsActivity", "Activity ID: " + activityId);
        Log.d("ActivityDetailsActivity", "Max Score: " + maxScore);

        // Header Title
        TextView tvActivityTitle = findViewById(R.id.tvActivityTitle);
        tvActivityTitle.setText(title != null ? title : "Activity Details");

        // Back button
        ImageView btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());

        // Tabs + ViewPager
        TabLayout tabLayout = findViewById(R.id.tabLayout);
        ViewPager2 viewPager = findViewById(R.id.viewPager);

        // Adapter → pass all necessary data including maxScore
        ActivityDetailsPagerAdapter adapter = new ActivityDetailsPagerAdapter(
                this,
                subjectCode != null ? subjectCode : "N/A",
                subjectName != null ? subjectName : "N/A",
                teacherName != null ? teacherName : "N/A",
                description != null ? description : "N/A",
                dueDate != null ? dueDate : "N/A",
                activityId != null ? activityId : "N/A",
                mainTerm != null ? mainTerm : "N/A",
                subTerm != null ? subTerm : "N/A",
                maxScore != null ? maxScore : "0" // pass maxScore safely
        );

        viewPager.setAdapter(adapter);

        // Connect tabs with ViewPager
        new TabLayoutMediator(tabLayout, viewPager,
                (tab, position) -> tab.setText(position == 0 ? "Details" : "My Work")
        ).attach();

        adapter.setActivityId(activityId); // Optional: set activityId in adapter
    }
}
