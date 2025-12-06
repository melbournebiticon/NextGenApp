package com.example.nextgen.teacher;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.*;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import com.example.nextgen.R;
import java.io.InputStream;
import java.util.*;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.example.nextgen.offline.QuestionEntity;

/**
 * GenerateQuizActivity
 *
 * This activity mirrors the behavior of GenerateQuestionsActivity but is scoped for quizzes.
 * Fix: Accepts "quizName" (from database) as well as "quizTitle" / "examTitle" from Intent extras.
 */
public class GenerateQuizActivity extends AppCompatActivity {

    private static final int PICK_FILE_REQUEST = 201;

    private TextView tvQuizInfo;
    private Spinner spQuestionType;
    private LinearLayout layoutMultipleChoice, layoutTrueFalse, layoutMatching;
    private LinearLayout layoutMCFields, layoutTFFields, layoutMatchingFields;
    private EditText etNumMC, etNumTF, etNumMatching;
    private CheckBox cbMC, cbTF, cbMatching;
    private Button btnSaveQuestions, btnImportQuestions;
    private RecyclerView rvQuestions;
    private QuestionAdapter adapter;
    private List<Question> questionList = new ArrayList<>();
    private String quizId;
    private String quizTitle;
    private Question editingQuestion = null;

    private FloatingActionButton fabImportQuestions;
    private LinearLayout editingContainer = null;
    private DatabaseReference database;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // NOTE: reuse the same layout as the exam version if desired.
        setContentView(R.layout.activity_generate_questions);

        // Accept both "quizId" and (for compatibility) "examId" if caller hasn't been updated.
        Intent intent = getIntent();
        quizId = intent.getStringExtra("quizId");
        if (quizId == null || quizId.isEmpty()) {
            // compatibility fallback: some callers might still pass "examId"
            quizId = intent.getStringExtra("examId");
        }

        // Read quiz title/name from multiple possible keys:
        // Prefer "quizName" (matches DB), then "quizTitle", then "examTitle" (compat).
        quizTitle = intent.getStringExtra("quizName");
        if (quizTitle == null || quizTitle.isEmpty()) {
            quizTitle = intent.getStringExtra("quizTitle");
        }
        if (quizTitle == null || quizTitle.isEmpty()) {
            quizTitle = intent.getStringExtra("examTitle");
        }

        // If still null/empty, provide a sensible default to avoid "null" showing in UI.
        if (quizTitle == null || quizTitle.isEmpty()) {
            quizTitle = "Untitled Quiz";
        }

        if (quizId == null || quizId.isEmpty()) {
            Toast.makeText(this, "Invalid Quiz", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Use a separate Firebase path for quizzes
        database = FirebaseDatabase.getInstance().getReference("QuizQuestions").child(quizId);

        tvQuizInfo = findViewById(R.id.tvExamInfo); // reuse same id from layout
        tvQuizInfo.setText("Quiz: " + quizTitle);

        spQuestionType = findViewById(R.id.spQuestionType);
        layoutMultipleChoice = findViewById(R.id.layoutMultipleChoice);
        layoutTrueFalse = findViewById(R.id.layoutTrueFalse);
        layoutMatching = findViewById(R.id.layoutMatching);
        layoutMCFields = findViewById(R.id.layoutMCFields);
        layoutTFFields = findViewById(R.id.layoutTFFields);
        layoutMatchingFields = findViewById(R.id.layoutMatchingFields);
        etNumMC = findViewById(R.id.etNumMC);
        etNumTF = findViewById(R.id.etNumTF);
        etNumMatching = findViewById(R.id.etNumMatching);
        cbMC = findViewById(R.id.cbMC);
        cbTF = findViewById(R.id.cbTF);
        cbMatching = findViewById(R.id.cbMatching);
        btnSaveQuestions = findViewById(R.id.btnSaveQuestion);
        fabImportQuestions = findViewById(R.id.fabImportQuestions);
        rvQuestions = findViewById(R.id.rvQuestions);
        rvQuestions.setLayoutManager(new LinearLayoutManager(this));

        String[] questionTypes = {"Multiple Choice", "True/False", "Matching Type"};
        spQuestionType.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, questionTypes));
        spQuestionType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                layoutMultipleChoice.setVisibility(position == 0 ? View.VISIBLE : View.GONE);
                layoutTrueFalse.setVisibility(position == 1 ? View.VISIBLE : View.GONE);
                layoutMatching.setVisibility(position == 2 ? View.VISIBLE : View.GONE);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        cbMC.setOnClickListener(v -> generateDynamicFields(cbMC, etNumMC, layoutMCFields, "MC"));
        cbTF.setOnClickListener(v -> generateDynamicFields(cbTF, etNumTF, layoutTFFields, "TF"));
        cbMatching.setOnClickListener(v -> generateDynamicFields(cbMatching, etNumMatching, layoutMatchingFields, "Matching"));

        btnSaveQuestions.setOnClickListener(v -> {
            if (editingQuestion != null) {
                saveEditedQuestion();
            } else {
                saveAllQuestions();
            }
        });

        btnImportQuestions.setOnClickListener(v -> openFilePicker());

        loadQuestions();
    }

    private void generateDynamicFields(CheckBox cb, EditText etNum, LinearLayout container, String type) {
        container.removeAllViews();
        if (!cb.isChecked()) return;

        int num = parseNumber(etNum);
        for (int i = 1; i <= num; i++) {
            LinearLayout itemContainer = new LinearLayout(this);
            itemContainer.setOrientation(LinearLayout.VERTICAL);
            itemContainer.setPadding(0, 0, 0, 16);

            EditText etQuestion = new EditText(this);
            etQuestion.setHint(type + " Question " + i);
            itemContainer.addView(etQuestion);

            if (type.equals("MC")) {
                EditText etA = new EditText(this); etA.setHint("Option A"); itemContainer.addView(etA);
                EditText etB = new EditText(this); etB.setHint("Option B"); itemContainer.addView(etB);
                EditText etC = new EditText(this); etC.setHint("Option C"); itemContainer.addView(etC);
                EditText etD = new EditText(this); etD.setHint("Option D"); itemContainer.addView(etD);

                Spinner spAnswer = new Spinner(this);
                spAnswer.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, Arrays.asList("A", "B", "C", "D")));
                itemContainer.addView(spAnswer);
            } else if (type.equals("TF")) {
                Spinner spAnswer = new Spinner(this);
                spAnswer.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, Arrays.asList("True", "False")));
                itemContainer.addView(spAnswer);
            } else if (type.equals("Matching")) {
                EditText etAnswer = new EditText(this);
                etAnswer.setHint("Correct Answer");
                itemContainer.addView(etAnswer);
            }

            container.addView(itemContainer);
        }
    }

    private int parseNumber(EditText et) {
        try { return Integer.parseInt(et.getText().toString()); }
        catch (NumberFormatException e) { return 0; }
    }

    private void saveAllQuestions() {
        List<Question> allQuestions = new ArrayList<>();
        collectQuestionsFromContainer(layoutMCFields, "Multiple Choice", allQuestions);
        collectQuestionsFromContainer(layoutTFFields, "True/False", allQuestions);
        collectQuestionsFromContainer(layoutMatchingFields, "Matching Type", allQuestions);
        saveQuestionsSafely(allQuestions);
    }

    private void saveQuestionsSafely(List<Question> questions) {
        if (questions == null || questions.isEmpty()) {
            Toast.makeText(this, "No questions to save.", Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(() -> {
            try {
                // --- 1. Save to Teacher Room DB (teacher-side QuestionDao) ---
                AppDatabase teacherDb = AppDatabase.getInstance(this); // make sure this is teacher DB
                teacherDb.questionDao().insertAll(questions);

                // --- 2. Save to Student offline DB (student-side QuestionEntity) ---
                List<QuestionEntity> entities = new ArrayList<>();
                for (Question q : questions) {
                    QuestionEntity entity = new QuestionEntity();
                    entity.examId = q.getExamId(); // reuse field - note: if you want quiz-specific field rename in DB
                    entity.questionText = q.getQuestionText();
                    entity.questionType = q.getQuestionType();
                    entity.optionA = q.getOptionA();
                    entity.optionB = q.getOptionB();
                    entity.optionC = q.getOptionC();
                    entity.optionD = q.getOptionD();
                    entity.correctAnswer = q.getCorrectAnswer();
                    entity.displayNumber = q.getDisplayNumber();
                    entity.matchingOptions = q.getMatchingOptions();
                    entity.firebaseKey = q.getFirebaseKey();
                    entities.add(entity);
                }

                com.example.nextgen.offline.AppDatabase studentDb = com.example.nextgen.offline.AppDatabase.getInstance(this);
                studentDb.questionDao().insertAll(entities);

                // --- 3. Sync to Firebase ---
                for (Question q : questions) {
                    if (q.getFirebaseKey() == null || q.getFirebaseKey().isEmpty()) {
                        String key = database.push().getKey();
                        q.setFirebaseKey(key);
                    }
                    database.child(q.getFirebaseKey()).setValue(q);
                }

                runOnUiThread(() -> {
                    Toast.makeText(this, questions.size() + " quiz questions saved and synced!", Toast.LENGTH_SHORT).show();
                    clearAllFields();
                    loadQuestions();
                });

            } catch (Exception ex) {
                ex.printStackTrace();
                runOnUiThread(() ->
                        Toast.makeText(this, "Failed to save: " + ex.getMessage(), Toast.LENGTH_LONG).show()
                );
            }
        }).start();
    }

    private void collectQuestionsFromContainer(LinearLayout container, String type, List<Question> allQuestions) {
        for (int i = 0; i < container.getChildCount(); i++) {
            LinearLayout item = (LinearLayout) container.getChildAt(i);
            String qText = ((EditText) item.getChildAt(0)).getText().toString().trim();
            if (qText.isEmpty()) continue;

            Question q;
            if (type.equals("Multiple Choice")) {
                String a = ((EditText) item.getChildAt(1)).getText().toString().trim();
                String b = ((EditText) item.getChildAt(2)).getText().toString().trim();
                String c = ((EditText) item.getChildAt(3)).getText().toString().trim();
                String d = ((EditText) item.getChildAt(4)).getText().toString().trim();
                String answer = ((Spinner) item.getChildAt(5)).getSelectedItem().toString();
                String correctOption = "";
                switch (answer) {
                    case "A": correctOption = a; break;
                    case "B": correctOption = b; break;
                    case "C": correctOption = c; break;
                    case "D": correctOption = d; break;
                }
                q = new Question(quizId, qText, type, a, b, c, d, correctOption);
            } else if (type.equals("True/False")) {
                String answer = ((Spinner) item.getChildAt(1)).getSelectedItem().toString();
                q = new Question(quizId, qText, type, "", "", "", "", answer);
            } else {
                String answer = ((EditText) item.getChildAt(1)).getText().toString().trim();
                q = new Question(quizId, qText, type, "", "", "", "", answer);
            }
            allQuestions.add(q);
        }
    }

    private void clearAllFields() {
        layoutMCFields.removeAllViews(); layoutTFFields.removeAllViews(); layoutMatchingFields.removeAllViews();
        cbMC.setChecked(false); cbTF.setChecked(false); cbMatching.setChecked(false);
        etNumMC.setText(""); etNumTF.setText(""); etNumMatching.setText("");
        editingQuestion = null; editingContainer = null;
    }

    private void loadQuestions() {
        new Thread(() -> {
            // Reuse existing DAO method that looks up by examId; pass quizId as argument.
            questionList = AppDatabase.getInstance(this).questionDao().getQuestionsByExamId(quizId);
            runOnUiThread(() -> {
                adapter = new QuestionAdapter(this, questionList, new QuestionAdapter.OnQuestionActionListener() {
                    @Override
                    public void onEdit(Question question) { showEditQuestionDialog(question); }

                    @Override
                    public void onDelete(Question question) {
                        new Thread(() -> {
                            AppDatabase.getInstance(GenerateQuizActivity.this).questionDao().deleteById(question.getId());
                            if (question.getFirebaseKey() != null) {
                                database.child(question.getFirebaseKey()).removeValue();
                            }
                            runOnUiThread(() -> loadQuestions());
                        }).start();
                    }
                });
                rvQuestions.setAdapter(adapter);
            });
        }).start();
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        String[] mimeTypes = {
                "application/pdf",
                "application/msword",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        };
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        startActivityForResult(Intent.createChooser(intent, "Select Quiz Question File"), PICK_FILE_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode == PICK_FILE_REQUEST && resultCode == RESULT_OK && data != null){
            Uri fileUri = data.getData();
            if(fileUri != null) importQuestionsFromFile(fileUri);
        }
    }

    private void importQuestionsFromFile(Uri uri) {
        new Thread(() -> {
            try {
                List<String> lines = new ArrayList<>();
                String fileName = getFileName(uri);

                InputStream inputStream = getContentResolver().openInputStream(uri);
                if (inputStream == null) throw new Exception("Cannot open file.");

                if (fileName.endsWith(".pdf")) {
                    PDDocument document = PDDocument.load(inputStream);
                    PDFTextStripper stripper = new PDFTextStripper();
                    String text = stripper.getText(document);
                    document.close();
                    lines = Arrays.asList(text.split("\n"));
                } else if (fileName.endsWith(".doc") || fileName.endsWith(".docx")) {
                    XWPFDocument document = new XWPFDocument(inputStream);
                    for (XWPFParagraph para : document.getParagraphs()) {
                        String text = para.getText().trim();
                        if (!text.isEmpty()) lines.add(text);
                    }
                    document.close();
                } else {
                    throw new Exception("Unsupported file format. Please choose a .pdf or .docx file.");
                }

                List<Question> importedQuestions = parseTextSmart(lines);
                // When importing for quizzes, ensure each question has quizId set
                for (Question q : importedQuestions) q.setExamId(quizId);

                saveQuestionsSafely(importedQuestions);

                runOnUiThread(() ->
                        Toast.makeText(this, "Questions imported successfully!", Toast.LENGTH_SHORT).show());

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() ->
                        Toast.makeText(this, "Failed to import questions: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            Cursor cursor = getContentResolver().query(uri, null, null, null, null);
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex >= 0) {
                        result = cursor.getString(nameIndex);
                    }
                }
            } finally {
                if (cursor != null) cursor.close();
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) result = result.substring(cut + 1);
        }
        return result;
    }

    private List<Question> parseTextSmart(List<String> lines) {
        List<Question> questions = new ArrayList<>();
        Question current = null;
        List<String> options = new ArrayList<>();

        for(String line : lines){
            line = line.trim(); if(line.isEmpty()) continue;

            if(line.startsWith("(MC)")){
                if(current != null) questions.add(current);
                current = new Question(); current.setExamId(quizId);
                current.setQuestionType("Multiple Choice"); current.setQuestionText(line.substring(4).trim());
                options.clear();
            } else if(line.startsWith("(TF)")){
                if(current != null) questions.add(current);
                current = new Question(); current.setExamId(quizId);
                current.setQuestionType("True/False"); current.setQuestionText(line.substring(4).trim());
            } else if(line.startsWith("(Match)")){
                if(current != null) questions.add(current);
                current = new Question(); current.setExamId(quizId);
                current.setQuestionType("Matching Type");
            } else if(line.matches("[A-D]\\.\\s.*")){
                options.add(line.substring(3).trim());
            } else if(line.startsWith("Answer:")){
                String ans = line.substring(7).trim();
                if (current == null) continue;
                if(current.getQuestionType().equals("Multiple Choice") && options.size() == 4){
                    current.setOptionA(options.get(0)); current.setOptionB(options.get(1));
                    current.setOptionC(options.get(2)); current.setOptionD(options.get(3));
                    String correctOption = ans;
                    if(ans.equals("A")) correctOption = options.get(0);
                    else if(ans.equals("B")) correctOption = options.get(1);
                    else if(ans.equals("C")) correctOption = options.get(2);
                    else if(ans.equals("D")) correctOption = options.get(3);
                    current.setCorrectAnswer(correctOption);

                } else if(current.getQuestionType().equals("True/False")){
                    current.setCorrectAnswer(ans);
                } else if(current.getQuestionType().equals("Matching Type")){
                    current.setCorrectAnswer(ans);
                }
                if(current.getQuestionType().equals("Multiple Choice") || current.getQuestionType().equals("True/False")){
                    questions.add(current); current = null;
                }
            } else if(current != null && current.getQuestionType().equals("Matching Type")){
                if(line.contains("→")){
                    String[] parts = line.split("→");
                    if(parts.length == 2){
                        current.setQuestionText(parts[0].trim());
                        current.setCorrectAnswer(parts[1].trim());
                        questions.add(current); current = null;
                    }
                }
            }
        }
        if(current != null) questions.add(current);
        return questions;
    }

    private void saveEditedQuestion() {
        if (editingQuestion == null || editingContainer == null) return;
        LinearLayout item = (LinearLayout) editingContainer.getChildAt(0);
        String qText = ((EditText) item.getChildAt(0)).getText().toString().trim();
        if (qText.isEmpty()) { Toast.makeText(this, "Question cannot be empty", Toast.LENGTH_SHORT).show(); return; }
        editingQuestion.setQuestionText(qText);

        if (editingQuestion.getQuestionType().equals("Multiple Choice")) {
            editingQuestion.setOptionA(((EditText) item.getChildAt(1)).getText().toString().trim());
            editingQuestion.setOptionB(((EditText) item.getChildAt(2)).getText().toString().trim());
            editingQuestion.setOptionC(((EditText) item.getChildAt(3)).getText().toString().trim());
            editingQuestion.setOptionD(((EditText) item.getChildAt(4)).getText().toString().trim());
            editingQuestion.setCorrectAnswer(((Spinner) item.getChildAt(5)).getSelectedItem().toString());
        } else if (editingQuestion.getQuestionType().equals("True/False")) {
            editingQuestion.setCorrectAnswer(((Spinner) item.getChildAt(1)).getSelectedItem().toString());
        } else if (editingQuestion.getQuestionType().equals("Matching Type")) {
            editingQuestion.setCorrectAnswer(((EditText) item.getChildAt(1)).getText().toString().trim());
        }

        new Thread(() -> {
            AppDatabase.getInstance(this).questionDao().updateQuestion(editingQuestion);
            if (editingQuestion.getFirebaseKey() != null) {
                database.child(editingQuestion.getFirebaseKey()).setValue(editingQuestion);
            }
            runOnUiThread(() -> {
                Toast.makeText(this, "Question updated successfully!", Toast.LENGTH_SHORT).show();
                clearAllFields();
                loadQuestions();
            });
        }).start();
    }

    private void showEditQuestionDialog(Question question) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_question, null);

        Spinner spType = dialogView.findViewById(R.id.spEditQType);
        EditText etQ = dialogView.findViewById(R.id.etEditQuestionText);

        LinearLayout layoutMC = dialogView.findViewById(R.id.layoutEditMC);
        EditText etA = dialogView.findViewById(R.id.etEditOptionA);
        EditText etB = dialogView.findViewById(R.id.etEditOptionB);
        EditText etC = dialogView.findViewById(R.id.etEditOptionC);
        EditText etD = dialogView.findViewById(R.id.etEditOptionD);
        Spinner spMCAnswer = dialogView.findViewById(R.id.spEditMCAnswer);

        LinearLayout layoutTF = dialogView.findViewById(R.id.layoutEditTF);
        Spinner spTFAnswer = dialogView.findViewById(R.id.spEditTFAnswer);

        LinearLayout layoutMatching = dialogView.findViewById(R.id.layoutEditMatching);
        EditText etMatchAnswer = dialogView.findViewById(R.id.etEditMatchAnswer);

        List<String> types = Arrays.asList("Multiple Choice", "True/False", "Matching Type");
        spType.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, types));
        spMCAnswer.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, Arrays.asList("A","B","C","D")));
        spTFAnswer.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, Arrays.asList("True","False")));

        spType.setSelection(types.indexOf(question.getQuestionType()));
        etQ.setText(question.getQuestionText());
        etA.setText(question.getOptionA()); etB.setText(question.getOptionB());
        etC.setText(question.getOptionC()); etD.setText(question.getOptionD());
        etMatchAnswer.setText(question.getCorrectAnswer());

        AdapterView.OnItemSelectedListener typeListener = new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                layoutMC.setVisibility(position==0 ? View.VISIBLE : View.GONE);
                layoutTF.setVisibility(position==1 ? View.VISIBLE : View.GONE);
                layoutMatching.setVisibility(position==2 ? View.VISIBLE : View.GONE);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        };
        spType.setOnItemSelectedListener(typeListener);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Edit Quiz Question")
                .setView(dialogView)
                .setPositiveButton("Save", (d, w) -> {
                    question.setQuestionType(spType.getSelectedItem().toString());
                    question.setQuestionText(etQ.getText().toString().trim());
                    if (question.getQuestionType().equals("Multiple Choice")) {
                        question.setOptionA(etA.getText().toString().trim());
                        question.setOptionB(etB.getText().toString().trim());
                        question.setOptionC(etC.getText().toString().trim());
                        question.setOptionD(etD.getText().toString().trim());
                        String selected = spMCAnswer.getSelectedItem().toString();
                        String correctOption = "";
                        switch (selected) {
                            case "A": correctOption = etA.getText().toString().trim(); break;
                            case "B": correctOption = etB.getText().toString().trim(); break;
                            case "C": correctOption = etC.getText().toString().trim(); break;
                            case "D": correctOption = etD.getText().toString().trim(); break;
                        }
                        question.setCorrectAnswer(correctOption);
                    } else if (question.getQuestionType().equals("True/False")) {
                        question.setCorrectAnswer(spTFAnswer.getSelectedItem().toString());
                        question.setOptionA(""); question.setOptionB(""); question.setOptionC(""); question.setOptionD("");
                    } else {
                        question.setCorrectAnswer(etMatchAnswer.getText().toString().trim());
                        question.setOptionA(""); question.setOptionB(""); question.setOptionC(""); question.setOptionD("");
                    }

                    new Thread(() -> {
                        AppDatabase.getInstance(this).questionDao().updateQuestion(question);
                        if (question.getFirebaseKey() != null) database.child(question.getFirebaseKey()).setValue(question);
                        runOnUiThread(() -> loadQuestions());
                    }).start();
                })
                .setNegativeButton("Cancel", null)
                .create();
        dialog.show();
    }

}