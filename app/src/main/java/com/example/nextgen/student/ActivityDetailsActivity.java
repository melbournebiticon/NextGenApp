package com.example.nextgen.student;

import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import android.util.Log;

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
    private String maxScore; // Variable to hold the Max Score

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_activity_details);

        // ✅ Get all data passed from intent
        title = getIntent().getStringExtra("title");
        subjectCode = getIntent().getStringExtra("subjectCode");
        subjectName = getIntent().getStringExtra("subjectName");
        teacherName = getIntent().getStringExtra("teacherName");
        description = getIntent().getStringExtra("description");
        dueDate = getIntent().getStringExtra("dueDate");
        activityId = getIntent().getStringExtra("activityId");
        mainTerm = getIntent().getStringExtra("mainTerm");
        subTerm = getIntent().getStringExtra("subTerm");

        // ✅ Retrieve the Max Score value from the incoming Intent
        maxScore = getIntent().getStringExtra("maxScore");

        // Log the received values for debugging
        Log.d("ActivityDetailsActivity", "🧩 Received activityId: " + activityId);
        Log.d("ActivityDetailsActivity", "🏆 Received maxScore: " + maxScore);

        // Header Title
        TextView tvActivityTitle = findViewById(R.id.tvActivityTitle);
        tvActivityTitle.setText(title != null ? title : "Activity Details");

        // Back Button
        ImageView btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish()); // Close activity on back press

        // Setup tabs + pager
        TabLayout tabLayout = findViewById(R.id.tabLayout);
        ViewPager2 viewPager = findViewById(R.id.viewPager);

        // Adapter
        // ✅ Pass the retrieved maxScore to the PagerAdapter constructor
        ActivityDetailsPagerAdapter adapter = new ActivityDetailsPagerAdapter(
                this,
                subjectCode,
                subjectName,
                teacherName,
                description,
                dueDate,
                activityId,
                mainTerm != null ? mainTerm : "N/A",
                subTerm != null ? subTerm : "N/A",
                maxScore != null ? maxScore : "0" // Pass the maxScore, defaulting to "0" if null
        );

        viewPager.setAdapter(adapter);

        // Attach tabs with ViewPager
        new TabLayoutMediator(tabLayout, viewPager,
                (tab, position) -> tab.setText(position == 0 ? "Details" : "My Work")
        ).attach();

        adapter.setActivityId(activityId);
    }
}