package com.example.nextgen;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.nextgen.admin.StudentModel;

import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * SessionManager - central place to persist small bits of user/teacher/student session data.
 *
 * Added helpers to persist and read teacher-related lists so StudentAttendanceActivity and
 * other screens can read assignedSections / assignedSubjects / courseDisplays / courseIds
 * from session storage instead of re-querying Firebase every time.
 *
 * Usage notes:
 * - saveTeacherId(...) stores the teacher node key (Teachers/{key}) in the same slot used previously by saveSession(...)
 *   (KEY_USER_ID). Existing code that calls getUserId() will continue to work.
 * - When you resolve the teacher node at login (e.g. by querying Teachers by email), call the save* helpers
 *   to cache assignedSections / assignedSubjects / courseDisplays / courseIds into session prefs. This will
 *   make StudentAttendanceActivity able to load the spinner immediately from session data.
 */
public class SessionManager {

    private static final String PREF_NAME = "user_session";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_ROLE = "role";
    private static final String KEY_FULL_NAME = "full_name";

    // Student metadata keys
    private static final String KEY_STUDENT_COURSE = "student_course";
    private static final String KEY_STUDENT_SPECIALIZATION = "student_specialization";
    private static final String KEY_STUDENT_YEAR = "student_year";
    private static final String KEY_STUDENT_SECTION = "student_section";
    private static final String KEY_STUDENT_UID = "student_uid"; // firebase auth uid or teacher's stored uid
    private static final String KEY_STUDENT_ID = "student_id"; // school student id if available
    private static final String KEY_STUDENT_PROFILE_IMAGE = "student_profile_image";

    // Teacher / assignment keys
    private static final String KEY_ASSIGNED_SECTIONS = "assignedSections";     // set of section node keys
    private static final String KEY_ASSIGNED_SUBJECTS = "assignedSubjects";     // set of subject ids
    private static final String KEY_COURSE_DISPLAYS = "courseDisplays";         // set of "Course - Spec - Year - Section"
    private static final String KEY_COURSE_IDS = "courseIds";                   // already used by some code

    private SharedPreferences pref;
    private SharedPreferences.Editor editor;

    public SessionManager(Context context) {
        pref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        editor = pref.edit();
    }

    /**
     * Instance accessor for the stored studentId.
     * Use this where you already have a SessionManager instance.
     */
    public String getStudentId() {
        return pref.getString(KEY_STUDENT_ID, null);
    }

    public void saveSession(String userId, String role) {
        saveSession(userId, role, null);
    }

    public void saveSession(String userId, String role, String fullName) {
        editor.putString(KEY_USER_ID, userId);
        editor.putString(KEY_ROLE, role);
        if (fullName != null) editor.putString(KEY_FULL_NAME, fullName);
        editor.apply();
    }

    /**
     * Persist StudentModel minimal metadata for offline convenience.
     * This does not store every field of StudentModel, only the common ones used by UI filters.
     */
    public void saveStudentModel(StudentModel student) {
        if (student == null) return;
        if (student.getStudentId() != null) editor.putString(KEY_STUDENT_ID, student.getStudentId());
        if (student.getUid() != null) editor.putString(KEY_STUDENT_UID, student.getUid());
        if (student.getCourseName() != null) editor.putString(KEY_STUDENT_COURSE, student.getCourseName());
        if (student.getSpecializationName() != null) editor.putString(KEY_STUDENT_SPECIALIZATION, student.getSpecializationName());
        if (student.getYearName() != null) editor.putString(KEY_STUDENT_YEAR, student.getYearName());
        if (student.getSectionName() != null) editor.putString(KEY_STUDENT_SECTION, student.getSectionName());
        if (student.getFullName() != null) editor.putString(KEY_FULL_NAME, student.getFullName());
        if (student.getProfileImage() != null) editor.putString(KEY_STUDENT_PROFILE_IMAGE, student.getProfileImage());
        editor.apply();
    }

    /**
     * Primary user/teacher id saved in session. This has historically been used by other activities
     * as the teacher node key. Keep this method to remain backward compatible.
     */
    public String getUserId() {
        return pref.getString(KEY_USER_ID, null);
    }

    public String getRole() {
        return pref.getString(KEY_ROLE, null);
    }

    /**
     * Return stored full name or null if not set.
     * IMPORTANT: we return null here (not "Unknown") so callers can avoid publishing "Unknown" into the DB.
     */
    public String getFullName() {
        return pref.getString(KEY_FULL_NAME, null);
    }

    public boolean isLoggedIn() {
        return getUserId() != null;
    }

    /**
     * Save courseIds (set) into session prefs.
     */
    public void saveCourseIds(List<String> courseIds) {
        if (courseIds == null) {
            editor.remove(KEY_COURSE_IDS);
            editor.apply();
            return;
        }
        editor.putStringSet(KEY_COURSE_IDS, new HashSet<>(courseIds));
        editor.apply();
    }

    /**
     * Get courseIds previously saved in session, or empty list if none.
     */
    public List<String> getCourseIds() {
        Set<String> set = pref.getStringSet(KEY_COURSE_IDS, new HashSet<>());
        return new ArrayList<>(set);
    }

    // ✅ Static helper method for quick access when Context is available but a SessionManager instance is not.
    public static String getStudentId(Context context) {
        if (context == null) return null;
        SharedPreferences pref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return pref.getString(KEY_STUDENT_ID, null);
    }

    public void clearSession() {
        editor.clear();
        editor.apply();
    }

    /**
     * Return a StudentModel built from stored session values (may be partial).
     * If nothing is stored, returns null.
     */
    public StudentModel getStudentModel() {
        String studentId = pref.getString(KEY_STUDENT_ID, null);
        String uid = pref.getString(KEY_STUDENT_UID, null);
        String course = pref.getString(KEY_STUDENT_COURSE, null);
        String specialization = pref.getString(KEY_STUDENT_SPECIALIZATION, null);
        String year = pref.getString(KEY_STUDENT_YEAR, null);
        String section = pref.getString(KEY_STUDENT_SECTION, null);
        String fullName = pref.getString(KEY_FULL_NAME, null);
        String profileImage = pref.getString(KEY_STUDENT_PROFILE_IMAGE, null);

        // If at least one meaningful field exists, return a StudentModel
        if (studentId == null && uid == null && course == null && specialization == null && year == null && section == null && fullName == null) {
            return null;
        }

        StudentModel s = new StudentModel();
        if (studentId != null) s.setStudentId(studentId);
        if (uid != null) s.setUid(uid);
        if (course != null) s.setCourseName(course);
        if (specialization != null) s.setSpecializationName(specialization);
        if (year != null) s.setYearName(year);
        if (section != null) s.setSectionName(section);
        if (fullName != null) s.setFullName(fullName);
        if (profileImage != null) s.setProfileImage(profileImage);
        return s;
    }

    // Convenience getters used by various activities
    public String getCourseName() {
        return pref.getString(KEY_STUDENT_COURSE, null);
    }

    public String getSpecializationName() {
        return pref.getString(KEY_STUDENT_SPECIALIZATION, null);
    }

    public String getYearName() {
        return pref.getString(KEY_STUDENT_YEAR, null);
    }

    public String getSectionName() {
        return pref.getString(KEY_STUDENT_SECTION, null);
    }

    public String getStudentUid() {
        return pref.getString(KEY_STUDENT_UID, null);
    }

    public String getStudentProfileImage() {
        return pref.getString(KEY_STUDENT_PROFILE_IMAGE, null);
    }

    /**
     * Optional helper to explicitly save/update the full name in session prefs.
     * Use this after fetching teacher/student profile so future publishes read a real name.
     */
    public void saveFullName(String fullName) {
        if (fullName == null) return;
        editor.putString(KEY_FULL_NAME, fullName);
        editor.apply();
    }

    /**
     * Save the studentId (school ID) into the session prefs.
     * Call this after resolving the studentId from the /Students node so subsequent scans will find it.
     */
    public void saveStudentId(String foundStudentId) {
        if (foundStudentId == null) return;
        editor.putString(KEY_STUDENT_ID, foundStudentId);
        editor.apply();
    }

    /**
     * Convenience: save Firebase auth UID associated with the student record (optional).
     */
    public void saveStudentUid(String uid) {
        if (uid == null) return;
        editor.putString(KEY_STUDENT_UID, uid);
        editor.apply();
    }

    // ---------------------------
    // TEACHER / ASSIGNMENT HELPERS
    // ---------------------------

    /**
     * Explicit alias to save teacher node key (keeps backward compatibility with saveSession).
     * Use this after you resolve the Teachers/{key} node during login.
     */
    public void saveTeacherId(String teacherKey) {
        if (teacherKey == null) return;
        editor.putString(KEY_USER_ID, teacherKey);
        editor.apply();
    }

    /**
     * Alias for getUserId() but clearer semantically when used by teacher-related code.
     */
    public String getTeacherId() {
        return getUserId();
    }

    /**
     * Save assignedSections (list of Section node keys) into session prefs.
     * Overwrites previous value.
     */
    public void saveAssignedSections(List<String> sectionIds) {
        if (sectionIds == null) {
            editor.remove(KEY_ASSIGNED_SECTIONS);
            editor.apply();
            return;
        }
        editor.putStringSet(KEY_ASSIGNED_SECTIONS, new HashSet<>(sectionIds));
        editor.apply();
    }

    /**
     * Return assignedSections previously saved in session or empty list.
     */
    public List<String> getAssignedSections() {
        Set<String> set = pref.getStringSet(KEY_ASSIGNED_SECTIONS, new HashSet<>());
        return new ArrayList<>(set);
    }

    /**
     * Save assignedSubjects (list of subject IDs) into session prefs.
     */
    public void saveAssignedSubjects(List<String> subjectIds) {
        if (subjectIds == null) {
            editor.remove(KEY_ASSIGNED_SUBJECTS);
            editor.apply();
            return;
        }
        editor.putStringSet(KEY_ASSIGNED_SUBJECTS, new HashSet<>(subjectIds));
        editor.apply();
    }

    /**
     * Return assignedSubjects previously saved in session or empty list.
     */
    public List<String> getAssignedSubjects() {
        Set<String> set = pref.getStringSet(KEY_ASSIGNED_SUBJECTS, new HashSet<>());
        return new ArrayList<>(set);
    }

    /**
     * Save courseDisplays (strings like "BSIT - Spec - 1 - A") into session prefs.
     */
    public void saveCourseDisplays(List<String> displays) {
        if (displays == null) {
            editor.remove(KEY_COURSE_DISPLAYS);
            editor.apply();
            return;
        }
        editor.putStringSet(KEY_COURSE_DISPLAYS, new HashSet<>(displays));
        editor.apply();
    }

    /**
     * Return courseDisplays previously saved in session or empty list.
     */
    public List<String> getCourseDisplays() {
        Set<String> set = pref.getStringSet(KEY_COURSE_DISPLAYS, new HashSet<>());
        return new ArrayList<>(set);
    }

    /**
     * Convenience: save teacher's profile image (base64 or url) into session for quick access.
     */
    public void saveProfileImage(String base64OrUrl) {
        if (base64OrUrl == null) return;
        editor.putString(KEY_STUDENT_PROFILE_IMAGE, base64OrUrl);
        editor.apply();
    }
}