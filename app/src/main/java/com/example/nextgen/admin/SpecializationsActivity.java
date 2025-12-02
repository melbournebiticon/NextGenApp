package com.example.nextgen.admin;

import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ImageView;



import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nextgen.R;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;



// FIXED: Tinanggal ang 'implements NavigationView.OnNavigationItemSelectedListener'
public class SpecializationsActivity extends AppCompatActivity {

    // FIXED: Tinanggal ang DrawerLayout variable
    private Toolbar toolbar;

    RecyclerView recyclerView;
    DatabaseReference dbRef;
    ArrayList<SpecializationModel> specializationList;
    SpecializationAdapter adapter;

    private LinearLayout emptyState;
    private TextView tvSpecializationCount;
    private FloatingActionButton addBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_specializations);

        initializeToolbarAndBackNavigation();

        ImageView btnBack = findViewById(R.id.btnBack);

        btnBack.setOnClickListener(v -> {
            // Go back to AdminActivity
            Intent intent = new Intent(this, AdminActivity.class);
            startActivity(intent);
            finish();
        });


        // UI INITIALIZATION
        addBtn = findViewById(R.id.addSpecializationFab);
        recyclerView = findViewById(R.id.specializationRecyclerView);
        emptyState = findViewById(R.id.emptyState);
        tvSpecializationCount = findViewById(R.id.tvSpecializationCount);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        specializationList = new ArrayList<>();
        dbRef = FirebaseDatabase.getInstance().getReference("Specializations");

        addBtn.setOnClickListener(v -> showAddEditDialog(null, null));

        loadSpecializations();
    }

    private void initializeToolbarAndBackNavigation() {
        // Setup Toolbar
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // I-set up ang 'Up' o 'Back' arrow
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            // Pag pinindot ang Toolbar arrow, tawagin ang onBackPressed()
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        // ✅ FIXED: Siguraduhin na AdminActivity ang target at mag-finish.
        Intent intent = new Intent(this, AdminActivity.class);



        startActivity(intent);
        finish();
    }

    private void loadSpecializations() {
        dbRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                specializationList.clear();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    String id = ds.getKey();
                    String name = ds.child("name").getValue(String.class);
                    specializationList.add(new SpecializationModel(id, name));
                }

                updateUI();

                adapter = new SpecializationAdapter(specializationList, SpecializationsActivity.this, dbRef);
                recyclerView.setAdapter(adapter);
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Toast.makeText(SpecializationsActivity.this, "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateUI() {
        int count = specializationList.size();
        tvSpecializationCount.setText(count + " specialization" + (count != 1 ? "s" : ""));
        tvSpecializationCount = findViewById(R.id.tvSpecializationCount);

        if (specializationList.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            emptyState.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    public void showAddEditDialog(String id, String currentName) {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_specialization);

        EditText etName = dialog.findViewById(R.id.etSpecializationName);
        Button btnSave = dialog.findViewById(R.id.btnSaveSpecialization);
        TextView tvDialogTitle = dialog.findViewById(R.id.tvDialogTitle);

        if (id != null) {
            tvDialogTitle.setText("Edit Specialization");
        } else {
            tvDialogTitle.setText("Add Specialization");
        }

        if (currentName != null) etName.setText(currentName);

        btnSave.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            if (name.isEmpty()) {
                etName.setError("Enter name");
                return;
            }

            if (id == null) {
                String newId = dbRef.push().getKey();
                dbRef.child(newId).child("name").setValue(name);
                Toast.makeText(this, "Specialization added successfully", Toast.LENGTH_SHORT).show();
            } else {
                dbRef.child(id).child("name").setValue(name);
                Toast.makeText(this, "Specialization updated successfully", Toast.LENGTH_SHORT).show();
            }

            dialog.dismiss();
        });

        Button btnCancel = dialog.findViewById(R.id.btnCancelSpecialization);
        btnCancel.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }
}
