package com.finale.nextgen.student;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.finale.nextgen.R;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ActivityDetailsFragment extends Fragment {

    /**
     * Factory method to create a new instance of this fragment
     * passing all necessary details including maxScore.
     */
    public static ActivityDetailsFragment newInstance(
            String code,
            String name,
            String teacher,
            String desc,
            String dueDate,
            String mainTerm,
            String subTerm,
            String maxScore // ✅ Added maxScore
    ) {
        ActivityDetailsFragment fragment = new ActivityDetailsFragment();
        Bundle args = new Bundle();
        args.putString("code", code);
        args.putString("name", name);
        args.putString("teacher", teacher);
        args.putString("desc", desc);
        args.putString("dueDate", dueDate);
        args.putString("mainTerm", mainTerm);
        args.putString("subTerm", subTerm);
        args.putString("maxScore", maxScore); // ✅ Put maxScore in arguments
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_activity_details, container, false);

        // Map TextViews from XML
        TextView tvTeacher = view.findViewById(R.id.tvTeacher);
        TextView tvDesc = view.findViewById(R.id.tvDesc);
        TextView tvDeadline = view.findViewById(R.id.tvDeadline);
        TextView tvTerm = view.findViewById(R.id.tvTerm);
        TextView tvMaxScore = view.findViewById(R.id.tvMaxScore); // ✅ Max Score TextView

        Bundle args = getArguments();
        if (args != null) {
            String teacher = args.getString("teacher", "N/A");
            String desc = args.getString("desc", "N/A");
            String dueDate = args.getString("dueDate", "N/A");
            String mainTerm = args.getString("mainTerm", "N/A");
            String subTerm = args.getString("subTerm", "N/A");
            String maxScore = args.getString("maxScore", "100"); // Default to 100

            // Set instructor
            tvTeacher.setText("Instructor: " + teacher);

            // Set term
            tvTerm.setText(mainTerm + " - " + subTerm);

            // Set formatted deadline
            tvDeadline.setText(formatDeadline(dueDate));

            // Set description
            tvDesc.setText(desc);

            // Set max score
            if (tvMaxScore != null) {
                tvMaxScore.setText(maxScore + " Points");
            }
        }

        return view;
    }

    /**
     * Format a raw date string into a human-readable format:
     * Example: November 4, 2025 (Tuesday) at 11:59 PM
     */
    private String formatDeadline(String rawDate) {
        try {
            SimpleDateFormat inputFormat;
            // Check if time exists
            if (rawDate.contains(":")) {
                inputFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
            } else {
                inputFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            }

            Date date = inputFormat.parse(rawDate);

            SimpleDateFormat outputFormat = new SimpleDateFormat(
                    "MMMM dd, yyyy (EEEE) 'at' h:mm a", Locale.getDefault()
            );

            return date != null ? outputFormat.format(date) : rawDate;
        } catch (Exception e) {
            e.printStackTrace();
            return rawDate; // Fallback to raw string if parsing fails
        }
    }
}
