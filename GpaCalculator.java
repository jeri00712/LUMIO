import java.util.ArrayList;
import java.util.List;

/**
 * GpaCalculator
 * 
 * Handles the mathematical calculation of Grade Points, Credit Points, and GPA.
 * This directly adapts the calculation logic you created in gpa.java into reusable methods.
 */
public class GpaCalculator {

    // Data structure representing a single subject
    public static class Subject {
        public String name;
        public int credit;
        public String grade;
        public int gradePoint;
        public int creditPoint;

        public Subject() {}

        public Subject(String name, int credit, String grade) {
            this.name = (name == null || name.trim().isEmpty()) ? "Subject" : name.trim();
            this.credit = credit;
            this.grade = (grade == null) ? "U" : grade.trim().toUpperCase();
        }
    }

    // Data structure representing detailed subject analytics
    public static class Analysis {
        public String topSubject = "N/A";
        public int topSubjectPoints = 0;
        public String lowestSubject = "N/A";
        public int lowestSubjectPoints = 10;
        public int passedCredits = 0;
        public int arrearCredits = 0;
        public int arrearCount = 0;
        public double passRate = 100.0;
        public int countS = 0;
        public int countAPlus = 0;
        public int countA = 0;
        public int countBPlus = 0;
        public int countB = 0;
        public int countC = 0;
        public int countU = 0;
    }

    // Data structure representing the calculation result
    public static class GpaResponse {
        public List<Subject> subjects = new ArrayList<>();
        // Zero-credit subjects: reported to the caller but excluded from every calculation.
        public List<Subject> excludedSubjects = new ArrayList<>();
        public int totalCredits = 0;
        public int totalCreditPoints = 0;
        public double gpa = 0.0;
        public Analysis analysis = new Analysis();
        public String status = "SUCCESS";
        public String message = "";
    }

    /**
     * Converts letter grade into grade points.
     * Matches the exact grading scale from your gpa.java:
     * S -> 10, A+ -> 9, A -> 8, B+ -> 7, B -> 6, C -> 5, U -> 0
     */
    public static int getGradePoint(String grade) {
        if (grade == null) return 0;
        switch (grade.toUpperCase().trim()) {
            case "S":  return 10;
            case "A+": return 9;
            case "A":  return 8;
            case "B+": return 7;
            case "B":  return 6;
            case "C":  return 5;
            case "U":  return 0;
            default:   return 0;
        }
    }

    private static GpaResponse errorResponse(String message) {
        GpaResponse error = new GpaResponse();
        error.status = "ERROR";
        error.message = message;
        return error;
    }

    /** True only for grades that exist in the grading scale above. */
    public static boolean isValidGrade(String grade) {
        if (grade == null) return false;
        switch (grade.toUpperCase().trim()) {
            case "S": case "A+": case "A": case "B+": case "B": case "C": case "U":
                return true;
            default:
                return false;
        }
    }

    /**
     * Calculates total credits, credit points, final GPA, and subject analytics.
     *
     * Credit rules:
     *   - credit > 0  : included in every total, the GPA, grade statistics and analysis.
     *   - credit == 0 : EXCLUDED from all of the above (listed in excludedSubjects only).
     *   - credit < 0 or not a valid whole number : the request is rejected with an error.
     * A zero or invalid credit is never replaced by 1.
     */
    public static GpaResponse calculate(List<Subject> inputList) {
        GpaResponse response = new GpaResponse();

        if (inputList == null || inputList.isEmpty()) {
            response.status = "ERROR";
            response.message = "No subjects provided.";
            return response;
        }

        int totalCredit = 0;
        int totalCreditPoint = 0;
        int maxPoints = -1;
        int minPoints = 11;

        for (Subject input : inputList) {
            Subject item = new Subject(input.name, input.credit, input.grade);

            // Reject negative / invalid credits (the server uses a negative sentinel for
            // missing or non-numeric values) and unknown grades. Nothing is silently fixed.
            if (item.credit < 0) {
                return errorResponse("Invalid credit for \"" + item.name
                    + "\": credits must be a whole number of 0 or more.");
            }
            if (!isValidGrade(item.grade)) {
                return errorResponse("Invalid grade \"" + item.grade + "\" for \"" + item.name
                    + "\". Allowed grades: S, A+, A, B+, B, C, U.");
            }

            // ZERO-CREDIT EXCLUSION: a subject with 0 credits is skipped completely.
            // It is not added to subjects, totals, GPA, grade statistics or analysis.
            item.gradePoint = getGradePoint(item.grade);
            if (item.credit == 0) {
                response.excludedSubjects.add(item);
                continue;
            }

            // Calculate points
            item.creditPoint = item.credit * item.gradePoint;

            totalCredit += item.credit;
            totalCreditPoint += item.creditPoint;

            // Compute Subject Analytics
            if (item.gradePoint > maxPoints) {
                maxPoints = item.gradePoint;
                response.analysis.topSubject = item.name + " (" + item.grade + ")";
                response.analysis.topSubjectPoints = item.gradePoint;
            }
            if (item.gradePoint < minPoints) {
                minPoints = item.gradePoint;
                response.analysis.lowestSubject = item.name + " (" + item.grade + ")";
                response.analysis.lowestSubjectPoints = item.gradePoint;
            }

            if ("U".equalsIgnoreCase(item.grade)) {
                response.analysis.arrearCount++;
                response.analysis.arrearCredits += item.credit;
                response.analysis.countU++;
            } else {
                response.analysis.passedCredits += item.credit;
                if ("S".equalsIgnoreCase(item.grade)) response.analysis.countS++;
                else if ("A+".equalsIgnoreCase(item.grade)) response.analysis.countAPlus++;
                else if ("A".equalsIgnoreCase(item.grade)) response.analysis.countA++;
                else if ("B+".equalsIgnoreCase(item.grade)) response.analysis.countBPlus++;
                else if ("B".equalsIgnoreCase(item.grade)) response.analysis.countB++;
                else if ("C".equalsIgnoreCase(item.grade)) response.analysis.countC++;
            }

            response.subjects.add(item);
        }

        // If every subject had zero credits there is nothing to average: return a clear
        // error instead of a misleading GPA of 0.00.
        if (response.subjects.isEmpty()) {
            GpaResponse error = new GpaResponse();
            error.status = "ERROR";
            error.message = "No valid subjects with positive credits were found. "
                + "Zero-credit subjects are excluded from GPA calculation.";
            error.excludedSubjects = response.excludedSubjects;
            return error;
        }

        if (!response.excludedSubjects.isEmpty()) {
            StringBuilder note = new StringBuilder("Excluded zero-credit subject(s): ");
            for (int i = 0; i < response.excludedSubjects.size(); i++) {
                if (i > 0) note.append(", ");
                note.append(response.excludedSubjects.get(i).name);
            }
            response.message = note.toString();
        }

        response.totalCredits = totalCredit;
        response.totalCreditPoints = totalCreditPoint;
        response.gpa = (totalCredit > 0) ? Math.round(((double) totalCreditPoint / totalCredit) * 100.0) / 100.0 : 0.0;
        response.analysis.passRate = (totalCredit > 0) 
            ? Math.round(((double) response.analysis.passedCredits / totalCredit) * 1000.0) / 10.0 
            : 0.0;

        return response;
    }

    // ==========================================
    // STEP 4: CGPA DATA MODELS & CALCULATION
    // ==========================================

    // Data structure representing a single semester
    public static class Semester {
        public int semesterNumber;
        public double gpa;
        public int credits;
        public double points; // gpa * credits

        public Semester() {}

        public Semester(int semesterNumber, double gpa, int credits) {
            this.semesterNumber = (semesterNumber > 0) ? semesterNumber : 1;
            // Clamp GPA between 0.0 and 10.0
            this.gpa = Math.max(0.0, Math.min(10.0, gpa));
            this.credits = Math.max(1, credits);
            this.points = Math.round(this.gpa * this.credits * 100.0) / 100.0;
        }
    }

    // Data structure representing the CGPA calculation result
    public static class CgpaResponse {
        public List<Semester> semesters = new ArrayList<>();
        public int totalCredits = 0;
        public double totalPoints = 0.0;
        public double cgpa = 0.0;
        public String classification = "";
        public String status = "SUCCESS";
        public String message = "";
    }

    /**
     * Calculates Cumulative GPA (CGPA) across multiple semesters:
     * CGPA = Sum(Semester GPA * Semester Credits) / Sum(Semester Credits)
     */
    public static CgpaResponse calculateCgpa(List<Semester> inputList) {
        CgpaResponse response = new CgpaResponse();

        if (inputList == null || inputList.isEmpty()) {
            response.status = "ERROR";
            response.message = "No semesters provided.";
            return response;
        }

        int totalCredit = 0;
        double totalPoints = 0.0;

        for (Semester input : inputList) {
            Semester sem = new Semester(input.semesterNumber, input.gpa, input.credits);
            totalCredit += sem.credits;
            totalPoints += sem.points;
            response.semesters.add(sem);
        }

        response.totalCredits = totalCredit;
        response.totalPoints = Math.round(totalPoints * 100.0) / 100.0;
        response.cgpa = (totalCredit > 0) 
            ? Math.round((totalPoints / totalCredit) * 100.0) / 100.0 
            : 0.0;

        // Degree Academic Classification
        if (response.cgpa >= 8.5) {
            response.classification = "First Class with Distinction";
        } else if (response.cgpa >= 7.0) {
            response.classification = "First Class";
        } else if (response.cgpa >= 5.5) {
            response.classification = "Second Class";
        } else {
            response.classification = "Pass / Needs Improvement";
        }

        return response;
    }
}