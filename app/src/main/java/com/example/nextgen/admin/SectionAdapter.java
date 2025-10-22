package com.example.nextgen.admin;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nextgen.R;

import java.util.List;

public class SectionAdapter extends RecyclerView.Adapter<SectionAdapter.SectionViewHolder> {

    private final List<SectionModel> sectionList;
    private final OnSectionActionListener listener;

    public interface OnSectionActionListener {
        void onEdit(SectionModel section);
        void onDelete(SectionModel section);
    }

    public SectionAdapter(List<SectionModel> sectionList, OnSectionActionListener listener) {
        this.sectionList = sectionList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public SectionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_section, parent, false);
        return new SectionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SectionViewHolder holder, int position) {
        SectionModel section = sectionList.get(position);
        holder.tvSectionName.setText(section.name);
        holder.tvSpecYear.setText(section.specializationName + " - " + section.yearName);

        // Edit button click
        holder.btnEdit.setOnClickListener(v -> {
            if (listener != null) listener.onEdit(section);
        });

        // Delete button click
        holder.btnDelete.setOnClickListener(v -> {
            if (listener != null) listener.onDelete(section);
        });
    }

    @Override
    public int getItemCount() {
        return sectionList.size();
    }

    public static class SectionViewHolder extends RecyclerView.ViewHolder {
        TextView tvSectionName, tvSpecYear;
        ImageButton btnEdit, btnDelete;

        public SectionViewHolder(@NonNull View itemView) {
            super(itemView);
            tvSectionName = itemView.findViewById(R.id.tvSectionName);
            tvSpecYear = itemView.findViewById(R.id.tvSpecYear);
            btnEdit = itemView.findViewById(R.id.btnEdit);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }
    }
}
