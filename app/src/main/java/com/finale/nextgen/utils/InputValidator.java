package com.finale.nextgen.utils;

import android.text.TextUtils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Calendar;

public class InputValidator {

    // ---------- FULL NAME ----------
    public static String validateFullName(String fullName) {
        if (TextUtils.isEmpty(fullName))
            return "Full name cannot be empty.";

        // Trim extra spaces at start/end
        fullName = fullName.trim();

        // Prevent multiple consecutive spaces
        if (fullName.contains("  "))
            return "Full name cannot contain double spaces.";

        // Minimum & maximum length
        if (fullName.length() < 2)
            return "Full name must be at least 2 characters.";
        if (fullName.length() > 50)
            return "Full name cannot exceed 50 characters.";

        // Regex for letters, spaces, hyphen, apostrophe, dot (for Jr., Sr., etc.)
        // Allows: John O'Neil, Mary-Anne Smith Jr.
        String regex = "^[\\p{L}]+([ '\\-\\.]?[\\p{L}]+)*(\\s(Jr\\.|Sr\\.|II|III|IV|V))?$";
        if (!fullName.matches(regex))
            return "Full name contains invalid characters.";

        return null; // valid
    }

    // ---------- FORMAT FULL NAME ----------
    public static String formatFullName(String fullName) {
        if (TextUtils.isEmpty(fullName)) return fullName;

        String[] parts = fullName.trim().split("\\s+");
        StringBuilder formatted = new StringBuilder();

        for (String part : parts) {
            // Handle suffixes like Jr., Sr., II, III
            if (part.matches("(?i)(Jr\\.|Sr\\.|II|III|IV|V)")) {
                formatted.append(part.toUpperCase()).append(" ");
            } else if (part.contains("-")) {
                // Handle hyphenated names like Mary-Anne
                String[] subParts = part.split("-");
                for (int i = 0; i < subParts.length; i++) {
                    if (!subParts[i].isEmpty()) {
                        subParts[i] = subParts[i].substring(0, 1).toUpperCase() + subParts[i].substring(1).toLowerCase();
                    }
                }
                formatted.append(String.join("-", subParts)).append(" ");
            } else {
                formatted.append(part.substring(0, 1).toUpperCase())
                        .append(part.substring(1).toLowerCase()).append(" ");
            }
        }

        return formatted.toString().trim();
    }

    // ---------- BIRTHDAY ----------
    public static String validateBirthday(String birthday, int minAge) {
        if (TextUtils.isEmpty(birthday))
            return "Birthday cannot be empty.";

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        sdf.setLenient(false);

        try {
            Date birthDate = sdf.parse(birthday);
            Date today = new Date();

            if (birthDate.after(today))
                return "Birthday cannot be in the future.";

            // Check minimum age
            Calendar calBirth = Calendar.getInstance();
            calBirth.setTime(birthDate);
            Calendar calToday = Calendar.getInstance();
            int age = calToday.get(Calendar.YEAR) - calBirth.get(Calendar.YEAR);
            if (calToday.get(Calendar.DAY_OF_YEAR) < calBirth.get(Calendar.DAY_OF_YEAR)) {
                age--; // hasn't had birthday yet this year
            }

            if (age < minAge) {
                return "Age must be at least " + minAge + " years.";
            }

        } catch (ParseException e) {
            return "Invalid birthday format. Use YYYY-MM-DD.";
        }

        return null;
    }


    // ---------- EMAIL ----------
    public static String validateEmail(String email) {
        if (TextUtils.isEmpty(email))
            return "Email cannot be empty.";

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches())
            return "Invalid email format.";

        return null;
    }


    // ---------- COURSES ----------
    public static String validateCourses(List<?> selectedCourses) {
        if (selectedCourses == null || selectedCourses.isEmpty())
            return "Select at least one course.";

        return null;
    }

    // ---------- SUBJECTS ----------
    public static String validateSubjects(List<?> selectedSubjects) {
        if (selectedSubjects == null || selectedSubjects.isEmpty())
            return "Select at least one subject.";

        return null;
    }

    // ---------- CONTACT ----------
    public static String validateContact(String contact) {
        if (TextUtils.isEmpty(contact))
            return "Contact cannot be empty.";

        if (!contact.matches("\\d{10}|\\d{11}"))
            return "Contact must be 10 or 11 digits.";


        return null; // valid
    }

}
