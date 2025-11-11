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

    public static ActivityDetailsFragment newInstance(
            String code, String name, String teacher, String desc,
            String dueDate, String mainTerm, String subTerm) {

        ActivityDetailsFragment fragment = new ActivityDetailsFragment();
        Bundle args = new Bundle();
        args.putString("code", code);
        args.putString("name", name);
        args.putString("teacher", teacher);
        args.putString("desc", desc);
        args.putString("dueDate", dueDate);
        args.putString("mainTerm", mainTerm);
        args.putString("subTerm", subTerm);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_activity_details, container, false);

        TextView tvCodeName = view.findViewById(R.id.tvCodeName);
        TextView tvTeacher = view.findViewById(R.id.tvTeacher);
        TextView tvDesc = view.findViewById(R.id.tvDesc);
        TextView tvDeadline = view.findViewById(R.id.tvDeadline);
        TextView tvTerm = view.findViewById(R.id.tvTerm); // 🆕 new TextView for term info

        Bundle args = getArguments();
        if (args != null) {
            String code = args.getString("code", "N/A");
            String name = args.getString("name", "N/A");
            String teacher = args.getString("teacher", "N/A");
            String desc = args.getString("desc", "N/A");
            String dueDate = args.getString("dueDate", "N/A");
            String mainTerm = args.getString("mainTerm", "N/A");
            String subTerm = args.getString("subTerm", "N/A");

            tvCodeName.setText("Course: " + code + " - " + name);
            tvTeacher.setText("Instructor: " + teacher);
            tvDesc.setText(desc);
            tvDeadline.setText("Deadline: " + formatDeadline(dueDate));
            tvTerm.setText(mainTerm + " - " + subTerm);
        }

        return view;
    }

    private String formatDeadline(String rawDate) {
        try {
            SimpleDateFormat inputFormat;
            if (rawDate.contains(":")) {
                inputFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
            } else {
                inputFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            }

            Date date = inputFormat.parse(rawDate);
            SimpleDateFormat outputFormat =
                    new SimpleDateFormat("MMMM dd, yyyy (EEEE) 'at' h:mm a", Locale.getDefault());
            return outputFormat.format(date);
        } catch (Exception e) {
            e.printStackTrace();
            return rawDate;
        }
    }
}
