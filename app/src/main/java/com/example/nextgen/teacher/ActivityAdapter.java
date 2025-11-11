package com.example.nextgen.teacher;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nextgen.R;

import java.util.List;


public class ActivityAdapter extends RecyclerView.Adapter<ActivityAdapter.ViewHolder> {

    public interface OnActivityClick {
        void onClick(ActivityModel activity);
    }

    private List<ActivityModel> list;
    private OnActivityClick listener;

    public ActivityAdapter(List<ActivityModel> list, OnActivityClick listener) {
        this.list = list;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_activity_teacher, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ActivityModel activity = list.get(position);
        holder.tvTitle.setText(activity.getTitle());
        holder.tvSubject.setText(activity.getSubjectCode() + " - " + activity.getSubject());
        holder.itemView.setOnClickListener(v -> listener.onClick(activity));
    }

    @Override
    public int getItemCount() { return list.size(); }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvSubject;
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvActivityTitle);
            tvSubject = itemView.findViewById(R.id.tvActivitySubject);
        }
    }
}
