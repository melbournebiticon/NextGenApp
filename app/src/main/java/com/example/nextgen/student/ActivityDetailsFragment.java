package com.example.nextgen.student;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.nextgen.R;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ActivityDetailsFragment extends Fragment {

    // ✅ FIX 1: Tanggapin ang Max Score mula sa ActivityDetailsPagerAdapter
    public static ActivityDetailsFragment newInstance(
            String code, String name, String teacher, String desc,
            String dueDate, String mainTerm, String subTerm, String maxScore) { // 🏆 IDINAGDAG ANG MAXSCORE

        ActivityDetailsFragment fragment = new ActivityDetailsFragment();
        Bundle args = new Bundle();
        args.putString("code", code);
        args.putString("name", name);
        args.putString("teacher", teacher);
        args.putString("desc", desc);
        args.putString("dueDate", dueDate);
        args.putString("mainTerm", mainTerm);
        args.putString("subTerm", subTerm);
        args.putString("maxScore", maxScore); // ✅ Ilagay sa Arguments
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_activity_details, container, false);

        // Map the existing TextViews
        TextView tvTeacher = view.findViewById(R.id.tvTeacher);
        TextView tvDesc = view.findViewById(R.id.tvDesc);
        TextView tvDeadline = view.findViewById(R.id.tvDeadline);
        TextView tvTerm = view.findViewById(R.id.tvTerm);

        // 🏆 CRITICAL FIX 2: I-map ang TextView gamit ang TAMANG ID mula sa XML
        TextView tvMaxScore = view.findViewById(R.id.tvMaxScore);

        Bundle args = getArguments();
        if (args != null) {
            String teacher = args.getString("teacher", "N/A");
            String desc = args.getString("desc", "N/A");
            String dueDate = args.getString("dueDate", "N/A");
            String mainTerm = args.getString("mainTerm", "N/A");
            String subTerm = args.getString("subTerm", "N/A");
            String maxScore = args.getString("maxScore", "100"); // ✅ Kunin ang Max Score

            // 2. METADATA ROWS: Set the raw data for the icon-driven rows.
            tvTeacher.setText("Instructor: " + teacher);

            // The XML layout provides the "Term:" label and icon.
            tvTerm.setText(mainTerm + " - " + subTerm);

            // 3. DEADLINE: Format and set. The XML layout provides the "Deadline:" icon.
            tvDeadline.setText(formatDeadline(dueDate));

            // 4. DESCRIPTION
            tvDesc.setText(desc);

            // 🏆 FINAL FIX 3: I-set ang tamang Max Score sa TextView
            if (tvMaxScore != null) {
                tvMaxScore.setText(maxScore + " Points");
            }
        }

        return view;
    }

    /**
     * Formats the raw date string into "Month DD, YYYY (Day of Week) at H:MM AM/PM".
     * Example: November 4, 2025 (Tuesday) at 11:59 PM
     */
    private String formatDeadline(String rawDate) {
        try {
            SimpleDateFormat inputFormat;
            // Detect if the string includes time (HH:mm)
            if (rawDate.contains(":")) {
                inputFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
            } else {
                inputFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            }

            Date date = inputFormat.parse(rawDate);
            // Updated output format to match common presentation style
            SimpleDateFormat outputFormat =
                    new SimpleDateFormat("MMMM dd, yyyy (EEEE) 'at' h:mm a", Locale.getDefault());
            return outputFormat.format(date);
        } catch (Exception e) {
            e.printStackTrace();
            return rawDate;
        }
    }
}