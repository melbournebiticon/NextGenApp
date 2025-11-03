package com.example.nextgen.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.nextgen.R;
import com.example.nextgen.model.YearModel;
import com.example.nextgen.admin.YearsActivity;
import com.google.firebase.database.DatabaseReference;
import java.util.ArrayList;

public class YearsAdapter extends RecyclerView.Adapter<YearsAdapter.ViewHolder> {

    private ArrayList<YearModel> list;
    private YearsActivity activity;
    private DatabaseReference dbRef;

    public YearsAdapter(ArrayList<YearModel> list, YearsActivity activity, DatabaseReference dbRef) {
        this.list = list;
        this.activity = activity;
        this.dbRef = dbRef;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_year, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        YearModel model = list.get(position);
        holder.yearName.setText(model.getName());

        holder.editBtn.setOnClickListener(v -> {
            activity.showAddEditDialog(model.getId(), model.getName());
        });

        holder.deleteBtn.setOnClickListener(v -> {
            if (model.getId() != null) {
                dbRef.child(model.getId()).removeValue();
            }
        });
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView yearName;
        Button editBtn, deleteBtn;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            yearName = itemView.findViewById(R.id.yearItemName);
            editBtn = itemView.findViewById(R.id.editYearBtn);
            deleteBtn = itemView.findViewById(R.id.deleteYearBtn);
        }
    }
}