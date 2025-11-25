package com.example.nextgen.student;

import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.example.nextgen.R;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

public class ActivityDetailsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_activity_details);

        // ✅ Get all data passed from intent
        String title = getIntent().getStringExtra("title");
        String subjectCode = getIntent().getStringExtra("subjectCode");
        String subjectName = getIntent().getStringExtra("subjectName");
        String teacherName = getIntent().getStringExtra("teacherName");
        String description = getIntent().getStringExtra("description");
        String dueDate = getIntent().getStringExtra("dueDate");
        String activityId = getIntent().getStringExtra("activityId");
        String mainTerm = getIntent().getStringExtra("mainTerm");   // e.g., "1st Term"
        String subTerm = getIntent().getStringExtra("subTerm");     // e.g., "Prelim"

        android.util.Log.d("ActivityDetailsActivity", "🧩 Received activityId: " + activityId);

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
        ActivityDetailsPagerAdapter adapter = new ActivityDetailsPagerAdapter(
                this,
                subjectCode,
                subjectName,
                teacherName,
                description,
                dueDate,
                activityId,
                mainTerm != null ? mainTerm : "N/A",
                subTerm != null ? subTerm : "N/A"
        );

        viewPager.setAdapter(adapter);

        new TabLayoutMediator(tabLayout, viewPager,
                (tab, position) -> tab.setText(position == 0 ? "Details" : "My Work")
        ).attach();
    }
}
