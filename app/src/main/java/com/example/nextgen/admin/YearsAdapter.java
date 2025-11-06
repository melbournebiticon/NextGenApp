package com.example.nextgen.admin;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nextgen.R;
import com.google.firebase.database.DatabaseReference;

import java.util.ArrayList;

public class YearsAdapter extends RecyclerView.Adapter<YearsAdapter.ViewHolder> {

    ArrayList<YearModel> list;
    Context context;
    DatabaseReference dbRef;
    YearsActivity activity;

    public YearsAdapter(ArrayList<YearModel> list, YearsActivity activity, DatabaseReference dbRef) {
        this.list = list;
        this.activity = activity;
        this.context = activity;
        this.dbRef = dbRef;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_year, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        YearModel model = list.get(position);
        holder.name.setText(model.getName());

        holder.deleteBtn.setOnClickListener(v -> {
            new AlertDialog.Builder(context)
                    .setTitle("Delete Year")
                    .setMessage("Are you sure you want to delete " + model.getName() + "?")
                    .setPositiveButton("Yes", (dialog, which) -> {
                        dbRef.child(model.getId()).removeValue()
                                .addOnSuccessListener(aVoid -> {
                                    Toast.makeText(context, "Year deleted", Toast.LENGTH_SHORT).show();
                                })
                                .addOnFailureListener(e -> {
                                    Toast.makeText(context, "Failed to delete: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                });
                    })
                    .setNegativeButton("No", null)
                    .show();
        });

        holder.editBtn.setOnClickListener(v -> activity.showAddEditDialog(model.getId(), model.getName()));
    }

    @Override
    public int getItemCount() { return list.size(); }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView name;
        Button deleteBtn, editBtn;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.yearItemName);
            deleteBtn = itemView.findViewById(R.id.deleteYearBtn);
            editBtn = itemView.findViewById(R.id.editYearBtn);
        }
    }
}