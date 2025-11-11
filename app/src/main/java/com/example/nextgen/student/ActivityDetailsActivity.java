package com.example.nextgen.student;

import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import android.widget.TextView;

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

        // 🆕 These two will come from Firebase later, but for now we’ll make sure they’re received if available
        String mainTerm = getIntent().getStringExtra("mainTerm");   // e.g., "1st Term"
        String subTerm = getIntent().getStringExtra("subTerm");     // e.g., "Prelim"

        android.util.Log.d("ActivityDetailsActivity", "🧩 Received activityId: " + activityId);

        // Header
        TextView tvActivityTitle = findViewById(R.id.tvActivityTitle);
        tvActivityTitle.setText(title != null ? title : "Activity Details");

        // Setup tabs + pager
        TabLayout tabLayout = findViewById(R.id.tabLayout);
        ViewPager2 viewPager = findViewById(R.id.viewPager);

        // 🟩 Pass all required data to adapter, including new term values
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
