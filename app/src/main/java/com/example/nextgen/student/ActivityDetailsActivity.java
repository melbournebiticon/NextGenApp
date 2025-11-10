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

        String title = getIntent().getStringExtra("title");
        String subjectCode = getIntent().getStringExtra("subjectCode");
        String subjectName = getIntent().getStringExtra("subjectName");
        String teacherName = getIntent().getStringExtra("teacherName");
        String description = getIntent().getStringExtra("description");
        String dueDate = getIntent().getStringExtra("dueDate"); // ✅ add this
        String activityId = getIntent().getStringExtra("activityId"); // dapat kasama sa intent

        android.util.Log.d("ActivityDetailsActivity", "🧩 Received activityId from Intent: " + activityId);
        // Header
        TextView tvActivityTitle = findViewById(R.id.tvActivityTitle);
        tvActivityTitle.setText(title != null ? title : "Activity Details");

        TabLayout tabLayout = findViewById(R.id.tabLayout);
        ViewPager2 viewPager = findViewById(R.id.viewPager);

        ActivityDetailsPagerAdapter adapter = new ActivityDetailsPagerAdapter(
                this, subjectCode, subjectName, teacherName, description, dueDate, activityId // ✅ pass dueDate
        );
        viewPager.setAdapter(adapter);

        new TabLayoutMediator(tabLayout, viewPager,
                (tab, position) -> tab.setText(position == 0 ? "Details" : "My Work")
        ).attach();
    }

}
