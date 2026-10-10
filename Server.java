import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Server.java
 * 
 * A lightweight Java HTTP Web Server built with standard JDK libraries.
 * - Serves the frontend web files (HTML, CSS, JS) from the "public" folder.
 * - Handles the "/api/calculate" REST endpoint to compute GPA.
 * - Marksheet PDF text is turned into subjects by the nested MarksheetParser class.
 */
public class Server {

    private static final int PORT = 8080;

    public static void main(String[] args) throws IOException {
        // Create HTTP server listening on port 8080
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

        // 1. API routes: handle GPA, CGPA, Semester History, and File Uploads
        server.createContext("/api/calculate", new CalculateHandler());
        server.createContext("/api/calculate-cgpa", new CalculateCgpaHandler());
        server.createContext("/api/history", new HistoryHandler());
        server.createContext("/api/upload", new UploadHandler());

        // Browsers request /favicon.ico automatically; answer it cleanly (404 if none exists).
        server.createContext("/favicon.ico", new FaviconHandler());

        // 2. Static file route: serves HTML, CSS, and JS to the browser
        server.createContext("/", new StaticFileHandler());

        // Use default executor
        server.setExecutor(null);

        System.out.println("=================================================");
        System.out.println("  GPA Calculator Web Server is running!");
        System.out.println("  Open your browser and visit: http://localhost:" + PORT);
        System.out.println("  Press Ctrl+C in terminal to stop.");
        System.out.println("=================================================");

        server.start();
    }

    /**
     * Handles POST requests to /api/calculate
     */
    static class CalculateHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Allow cross-origin requests & preflight
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "POST, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");

            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, "{\"status\":\"ERROR\",\"message\":\"Only POST method is allowed\"}");
                return;
            }

            // Read the JSON request body sent from the webpage
            InputStream is = exchange.getRequestBody();
            String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);

            // Parse incoming JSON subjects
            List<GpaCalculator.Subject> subjects = parseSubjectsFromJson(body);

            // Calculate GPA using our calculation engine
            GpaCalculator.GpaResponse response = GpaCalculator.calculate(subjects);

            // Convert response to JSON string
            String jsonResponse = toJson(response);

            // HTTP 400 for calculation errors (negative credit, no valid subjects, ...), so the
            // frontend, which checks response.ok, shows the message instead of a "0.00" GPA.
            sendJsonResponse(exchange, "ERROR".equals(response.status) ? 400 : 200, jsonResponse);
        }

        private void sendJsonResponse(HttpExchange exchange, int statusCode, String responseText) throws IOException {
            byte[] bytes = responseText.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(statusCode, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }

        /**
         * Parses subjects from simple JSON format:
         * [{"name":"Math", "credit":4, "grade":"A+"}, ...]
         */
        private List<GpaCalculator.Subject> parseSubjectsFromJson(String json) {
            List<GpaCalculator.Subject> list = new ArrayList<>();
            if (json == null || json.isEmpty()) return list;

            Pattern objPattern = Pattern.compile("\\{([^}]+)\\}");
            Matcher objMatcher = objPattern.matcher(json);

            while (objMatcher.find()) {
                String objContent = objMatcher.group(1);

                String name = extractStringValue(objContent, "name");
                // Missing / negative / fractional credits become a negative sentinel so that
                // GpaCalculator rejects them (they are never silently turned into 1).
                int credit = extractCreditValue(objContent);
                String grade = extractStringValue(objContent, "grade");

                if (name.isEmpty()) name = "Subject";
                if (grade.isEmpty()) grade = "U";

                list.add(new GpaCalculator.Subject(name, credit, grade));
            }
            return list;
        }

        private String extractStringValue(String text, String key) {
            // First try matching quoted string: "name": "Mathematics - II"
            Pattern quoted = Pattern.compile("[\"']?" + key + "[\"']?\\s*:\\s*\"([^\"]*)\"");
            Matcher m = quoted.matcher(text);
            if (m.find()) return m.group(1).trim();

            // Fallback for unquoted value: key: value
            Pattern unquoted = Pattern.compile("[\"']?" + key + "[\"']?\\s*:\\s*([^,}\\r\\n]+)");
            m = unquoted.matcher(text);
            return m.find() ? m.group(1).trim() : "";
        }

        private int extractIntValue(String text, String key, int defaultValue) {
            Pattern p = Pattern.compile("[\"']?" + key + "[\"']?\\s*:\\s*[\"']?(\\d+)[\"']?");
            Matcher m = p.matcher(text);
            if (m.find()) {
                try {
                    return Integer.parseInt(m.group(1));
                } catch (NumberFormatException ignored) {}
            }
            return defaultValue;
        }

        /** Reads the "credit" field. Returns Integer.MIN_VALUE when missing, negative or not a whole number. */
        private int extractCreditValue(String text) {
            Pattern p = Pattern.compile("[\"']?credit[\"']?\\s*:\\s*[\"']?([^,}\"'\\s]+)[\"']?");
            Matcher m = p.matcher(text);
            if (!m.find()) return Integer.MIN_VALUE;
            String raw = m.group(1);
            if (!raw.matches("\\d{1,6}")) return Integer.MIN_VALUE; // rejects "-2", "2.5", "abc"
            return Integer.parseInt(raw);
        }

        /**
         * Converts GpaResponse into a JSON string
         */
        private String toJson(GpaCalculator.GpaResponse res) {
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            sb.append("\"status\":\"").append(res.status).append("\",");
            sb.append("\"message\":\"").append(escapeJson(res.message)).append("\",");
            sb.append("\"totalCredits\":").append(res.totalCredits).append(",");
            sb.append("\"totalCreditPoints\":").append(res.totalCreditPoints).append(",");
            sb.append("\"gpa\":").append(String.format(java.util.Locale.US, "%.2f", res.gpa)).append(",");
            sb.append("\"analysis\":{");
            sb.append("\"topSubject\":\"").append(escapeJson(res.analysis.topSubject)).append("\",");
            sb.append("\"topSubjectPoints\":").append(res.analysis.topSubjectPoints).append(",");
            sb.append("\"lowestSubject\":\"").append(escapeJson(res.analysis.lowestSubject)).append("\",");
            sb.append("\"lowestSubjectPoints\":").append(res.analysis.lowestSubjectPoints).append(",");
            sb.append("\"passedCredits\":").append(res.analysis.passedCredits).append(",");
            sb.append("\"arrearCredits\":").append(res.analysis.arrearCredits).append(",");
            sb.append("\"arrearCount\":").append(res.analysis.arrearCount).append(",");
            sb.append("\"passRate\":").append(String.format(java.util.Locale.US, "%.1f", res.analysis.passRate)).append(",");
            sb.append("\"gradeCounts\":{");
            sb.append("\"S\":").append(res.analysis.countS).append(",");
            sb.append("\"A+\":").append(res.analysis.countAPlus).append(",");
            sb.append("\"A\":").append(res.analysis.countA).append(",");
            sb.append("\"B+\":").append(res.analysis.countBPlus).append(",");
            sb.append("\"B\":").append(res.analysis.countB).append(",");
            sb.append("\"C\":").append(res.analysis.countC).append(",");
            sb.append("\"U\":").append(res.analysis.countU);
            sb.append("}},");
            sb.append("\"subjects\":[");

            for (int i = 0; i < res.subjects.size(); i++) {
                GpaCalculator.Subject s = res.subjects.get(i);
                sb.append("{");
                sb.append("\"name\":\"").append(escapeJson(s.name)).append("\",");
                sb.append("\"credit\":").append(s.credit).append(",");
                sb.append("\"grade\":\"").append(s.grade).append("\",");
                sb.append("\"gradePoint\":").append(s.gradePoint).append(",");
                sb.append("\"creditPoint\":").append(s.creditPoint);
                sb.append("}");
                if (i < res.subjects.size() - 1) sb.append(",");
            }

            sb.append("],");
            // Zero-credit subjects: reported for transparency, never part of the calculation.
            sb.append("\"excludedSubjects\":[");
            for (int i = 0; i < res.excludedSubjects.size(); i++) {
                GpaCalculator.Subject s = res.excludedSubjects.get(i);
                sb.append("{\"name\":\"").append(escapeJson(s.name)).append("\",");
                sb.append("\"credit\":").append(s.credit).append(",");
                sb.append("\"grade\":\"").append(escapeJson(s.grade)).append("\"}");
                if (i < res.excludedSubjects.size() - 1) sb.append(",");
            }
            sb.append("]}");
            return sb.toString();
        }

        private String escapeJson(String text) {
            if (text == null) {
                return "";
            }

            StringBuilder escaped = new StringBuilder(text.length() + 16);
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                switch (c) {
                    case '"': escaped.append("\\\""); break;
                    case '\\': escaped.append("\\\\"); break;
                    case '\b': escaped.append("\\b"); break;
                    case '\f': escaped.append("\\f"); break;
                    case '\n': escaped.append("\\n"); break;
                    case '\r': escaped.append("\\r"); break;
                    case '\t': escaped.append("\\t"); break;
                    default:
                        if (c < 0x20) {
                            escaped.append(String.format(java.util.Locale.ROOT, "\\u%04x", (int) c));
                        } else {
                            escaped.append(c);
                        }
                        break;
                }
            }
            return escaped.toString();
        }
    }

    /**
     * Handles POST requests to /api/calculate-cgpa
     */
    static class CalculateCgpaHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "POST, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");

            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, "{\"status\":\"ERROR\",\"message\":\"Only POST method is allowed\"}");
                return;
            }

            InputStream is = exchange.getRequestBody();
            String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);

            List<GpaCalculator.Semester> semesters = parseSemestersFromJson(body);
            GpaCalculator.CgpaResponse response = GpaCalculator.calculateCgpa(semesters);
            String jsonResponse = toJson(response);

            sendJsonResponse(exchange, "ERROR".equals(response.status) ? 400 : 200, jsonResponse);
        }

        private void sendJsonResponse(HttpExchange exchange, int statusCode, String responseText) throws IOException {
            byte[] bytes = responseText.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(statusCode, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }

        private List<GpaCalculator.Semester> parseSemestersFromJson(String json) {
            List<GpaCalculator.Semester> list = new ArrayList<>();
            if (json == null || json.isEmpty()) return list;

            Pattern objPattern = Pattern.compile("\\{([^}]+)\\}");
            Matcher objMatcher = objPattern.matcher(json);
            int semIndex = 1;

            while (objMatcher.find()) {
                String objContent = objMatcher.group(1);
                int semNum = extractIntValue(objContent, "semester", semIndex);
                double gpa = extractDoubleValue(objContent, "gpa", 0.0);
                int credits = extractIntValue(objContent, "credits", 20);

                list.add(new GpaCalculator.Semester(semNum, gpa, credits));
                semIndex++;
            }
            return list;
        }

        private int extractIntValue(String text, String key, int defaultValue) {
            Pattern p = Pattern.compile("[\"']?" + key + "[\"']?\\s*:\\s*[\"']?(\\d+)[\"']?");
            Matcher m = p.matcher(text);
            if (m.find()) {
                try {
                    return Integer.parseInt(m.group(1));
                } catch (NumberFormatException ignored) {}
            }
            return defaultValue;
        }

        private double extractDoubleValue(String text, String key, double defaultValue) {
            Pattern p = Pattern.compile("[\"']?" + key + "[\"']?\\s*:\\s*[\"']?([0-9]*\\.?[0-9]+)[\"']?");
            Matcher m = p.matcher(text);
            if (m.find()) {
                try {
                    return Double.parseDouble(m.group(1));
                } catch (NumberFormatException ignored) {}
            }
            return defaultValue;
        }

        private String toJson(GpaCalculator.CgpaResponse res) {
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            sb.append("\"status\":\"").append(res.status).append("\",");
            sb.append("\"message\":\"").append(res.message).append("\",");
            sb.append("\"totalCredits\":").append(res.totalCredits).append(",");
            sb.append("\"totalPoints\":").append(String.format(java.util.Locale.US, "%.2f", res.totalPoints)).append(",");
            sb.append("\"cgpa\":").append(String.format(java.util.Locale.US, "%.2f", res.cgpa)).append(",");
            sb.append("\"classification\":\"").append(res.classification).append("\",");
            sb.append("\"semesters\":[");

            for (int i = 0; i < res.semesters.size(); i++) {
                GpaCalculator.Semester s = res.semesters.get(i);
                sb.append("{");
                sb.append("\"semester\":").append(s.semesterNumber).append(",");
                sb.append("\"gpa\":").append(String.format(java.util.Locale.US, "%.2f", s.gpa)).append(",");
                sb.append("\"credits\":").append(s.credits).append(",");
                sb.append("\"points\":").append(String.format(java.util.Locale.US, "%.2f", s.points));
                sb.append("}");
                if (i < res.semesters.size() - 1) sb.append(",");
            }

            sb.append("]}");
            return sb.toString();
        }
    }

    /**
     * Handles /api/history (GET, POST, DELETE) for local server-side persistence in data/history.json
     */
    static class HistoryHandler implements HttpHandler {
        private final File dataDir = new File("data");
        private final File historyFile = new File(dataDir, "history.json");

        public HistoryHandler() {
            if (!dataDir.exists()) {
                dataDir.mkdirs();
            }
            if (!historyFile.exists()) {
                try {
                    Files.writeString(historyFile.toPath(), "[]", StandardCharsets.UTF_8);
                } catch (IOException ignored) {}
            }
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");

            String method = exchange.getRequestMethod().toUpperCase();

            if ("OPTIONS".equals(method)) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if ("GET".equals(method)) {
                handleGet(exchange);
            } else if ("POST".equals(method)) {
                handlePost(exchange);
            } else if ("DELETE".equals(method)) {
                handleDelete(exchange);
            } else {
                sendJsonResponse(exchange, 405, "{\"status\":\"ERROR\",\"message\":\"Method Not Allowed\"}");
            }
        }

        private synchronized void handleGet(HttpExchange exchange) throws IOException {
            String content = "[]";
            if (historyFile.exists()) {
                content = Files.readString(historyFile.toPath(), StandardCharsets.UTF_8).trim();
                if (content.isEmpty()) content = "[]";
            }
            sendJsonResponse(exchange, 200, content);
        }

        private synchronized void handlePost(HttpExchange exchange) throws IOException {
            InputStream is = exchange.getRequestBody();
            String newRecord = new String(is.readAllBytes(), StandardCharsets.UTF_8).trim();

            if (newRecord.isEmpty() || !newRecord.startsWith("{")) {
                sendJsonResponse(exchange, 400, "{\"status\":\"ERROR\",\"message\":\"Invalid record data\"}");
                return;
            }

            String current = "[]";
            if (historyFile.exists()) {
                current = Files.readString(historyFile.toPath(), StandardCharsets.UTF_8).trim();
            }
            if (!current.startsWith("[")) current = "[]";

            String updated;
            if (current.equals("[]") || current.equals("[ ]")) {
                updated = "[\n  " + newRecord + "\n]";
            } else {
                String inner = current.substring(1).trim();
                updated = "[\n  " + newRecord + ",\n  " + inner;
            }

            Files.writeString(historyFile.toPath(), updated, StandardCharsets.UTF_8);
            sendJsonResponse(exchange, 200, "{\"status\":\"SUCCESS\",\"message\":\"Semester saved to history\"}");
        }

        private synchronized void handleDelete(HttpExchange exchange) throws IOException {
            String query = exchange.getRequestURI().getQuery();
            String targetId = "";
            if (query != null && query.contains("id=")) {
                targetId = query.split("id=")[1].split("&")[0].trim();
            }

            if (targetId.isEmpty()) {
                Files.writeString(historyFile.toPath(), "[]", StandardCharsets.UTF_8);
                sendJsonResponse(exchange, 200, "{\"status\":\"SUCCESS\",\"message\":\"All history cleared\"}");
                return;
            }

            String current = Files.readString(historyFile.toPath(), StandardCharsets.UTF_8).trim();
            Pattern objPattern = Pattern.compile("\\{([^{}]+(?:\\{[^{}]*\\}[^{}]*)*)\\}");
            Matcher m = objPattern.matcher(current);
            StringBuilder sb = new StringBuilder("[\n");
            boolean first = true;

            while (m.find()) {
                String obj = m.group(0);
                if (!obj.contains("\"id\":\"" + targetId + "\"") && !obj.contains("\"id\": \"" + targetId + "\"")) {
                    if (!first) sb.append(",\n");
                    sb.append("  ").append(obj);
                    first = false;
                }
            }
            sb.append("\n]");

            Files.writeString(historyFile.toPath(), sb.toString(), StandardCharsets.UTF_8);
            sendJsonResponse(exchange, 200, "{\"status\":\"SUCCESS\",\"message\":\"Record deleted\"}");
        }

        private void sendJsonResponse(HttpExchange exchange, int statusCode, String responseText) throws IOException {
            byte[] bytes = responseText.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(statusCode, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    /**
     * Handles /api/upload (POST to save uploaded PDF / image files to uploads/)
     */
    static class UploadHandler implements HttpHandler {
        private final File uploadsDir = new File("uploads");

        public UploadHandler() {
            if (!uploadsDir.exists()) {
                uploadsDir.mkdirs();
            }
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");

            String method = exchange.getRequestMethod().toUpperCase();

            if ("OPTIONS".equals(method)) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if ("GET".equals(method)) {
                File[] files = uploadsDir.listFiles();
                StringBuilder sb = new StringBuilder("[");
                if (files != null) {
                    for (int i = 0; i < files.length; i++) {
                        File f = files[i];
                        if (f.isFile()) {
                            sb.append("{");
                            sb.append("\"name\":\"").append(escapeJson(f.getName())).append("\",");
                            sb.append("\"size\":").append(f.length()).append(",");
                            sb.append("\"modified\":").append(f.lastModified());
                            sb.append("}");
                            if (i < files.length - 1) sb.append(",");
                        }
                    }
                }
                sb.append("]");
                sendJsonResponse(exchange, 200, sb.toString());
            } else if ("POST".equals(method)) {
                // Server-side limits (the page also checks 10 MB, but the server must not trust it).
                final int MAX_FILE_BYTES = 10 * 1024 * 1024;
                final int MAX_BODY_BYTES = MAX_FILE_BYTES / 3 * 4 + 8192; // base64 overhead + JSON
                InputStream is = exchange.getRequestBody();
                byte[] rawBody = is.readNBytes(MAX_BODY_BYTES + 1);
                if (rawBody.length > MAX_BODY_BYTES) {
                    sendJsonResponse(exchange, 413, "{\"status\":\"ERROR\",\"message\":\"File is larger than the 10 MB limit\"}");
                    return;
                }
                String body = new String(rawBody, StandardCharsets.UTF_8);

                String fileName = extractStringValue(body, "fileName");
                String fileData = extractStringValue(body, "fileData");

                if (fileName.isEmpty()) {
                    fileName = "marksheet_" + System.currentTimeMillis() + ".pdf";
                }

                String safeName = fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
                String uniqueFileName = System.currentTimeMillis() + "_" + safeName;
                File targetFile = new File(uploadsDir, uniqueFileName);

                byte[] fileBytes;
                if (!fileData.isEmpty()) {
                    if (fileData.contains(",")) {
                        fileData = fileData.substring(fileData.indexOf(",") + 1);
                    }
                    try {
                        fileBytes = Base64.getDecoder().decode(fileData);
                    } catch (IllegalArgumentException ex) {
                        sendJsonResponse(exchange, 400, "{\"status\":\"ERROR\",\"message\":\"Invalid file data\"}");
                        return;
                    }
                } else {
                    sendJsonResponse(exchange, 400, "{\"status\":\"ERROR\",\"message\":\"No file data received\"}");
                    return;
                }

                // Only marksheet file types are accepted, and nothing is saved before this check.
                String ext = safeName.contains(".") ? safeName.substring(safeName.lastIndexOf(".") + 1).toLowerCase() : "";
                if (!(ext.equals("pdf") || ext.equals("png") || ext.equals("jpg") || ext.equals("jpeg"))) {
                    sendJsonResponse(exchange, 400, "{\"status\":\"ERROR\",\"message\":\"Only PDF, PNG, JPG and JPEG files are accepted\"}");
                    return;
                }
                if (fileBytes.length > MAX_FILE_BYTES) {
                    sendJsonResponse(exchange, 413, "{\"status\":\"ERROR\",\"message\":\"File is larger than the 10 MB limit\"}");
                    return;
                }

                Files.write(targetFile.toPath(), fileBytes);

                String fileType = safeName.contains(".") ? safeName.substring(safeName.lastIndexOf(".") + 1).toLowerCase() : "unknown";
                long sizeKb = Math.max(1, fileBytes.length / 1024);

                String extractedText = "";
                List<ExtractedSubject> extractedSubjects = new ArrayList<>();
                List<MarksheetParser.Subject> excludedZeroCredit = new ArrayList<>();
                List<String> parseWarnings = new ArrayList<>();
                String message = "Marksheet uploaded and processed";

                if ("pdf".equals(fileType)) {
                    try {
                        extractedText = extractPdfText(fileBytes);
                    } catch (Exception ex) {
                        // The file is saved, but the PDF could not be read: tell the frontend why.
                        System.err.println("PDF extraction failed: " + ex.getMessage());
                        sendJsonResponse(exchange, 422, "{\"status\":\"ERROR\",\"message\":\""
                            + escapeJson("Could not read the PDF text: " + ex.getMessage()) + "\"}");
                        return;
                    }

                    if (extractedText == null || extractedText.trim().isEmpty()) {
                        sendJsonResponse(exchange, 422, "{\"status\":\"ERROR\",\"message\":\""
                            + escapeJson("No text could be extracted. The PDF may be a scanned image.") + "\"}");
                        return;
                    }

                    try {
                        MarksheetParser.Result parsed = MarksheetParser.parse(extractedText);
                        for (MarksheetParser.Subject s : parsed.subjects) {
                            extractedSubjects.add(new ExtractedSubject(s.name, s.credit, s.grade));
                        }
                        excludedZeroCredit = parsed.zeroCredit;
                        parseWarnings = parsed.rejected;
                    } catch (Exception ex) {
                        System.err.println("Marksheet parsing failed: " + ex.getMessage());
                        sendJsonResponse(exchange, 500, "{\"status\":\"ERROR\",\"message\":\""
                            + escapeJson("Could not parse the marksheet: " + ex.getMessage()) + "\"}");
                        return;
                    }

                    if (extractedSubjects.isEmpty()) {
                        message = "Uploaded, but no valid subjects with positive credits were found in this marksheet.";
                    } else if (!excludedZeroCredit.isEmpty()) {
                        message = "Marksheet processed. " + excludedZeroCredit.size()
                            + " zero-credit subject(s) were excluded from GPA calculation.";
                    }
                }

                if (!"pdf".equals(fileType)) {
                    message = "File saved. Subjects can only be read automatically from PDF marksheets; enter image-based marks manually.";
                }

                StringBuilder subjectJson = new StringBuilder("[");
                for (int i = 0; i < extractedSubjects.size(); i++) {
                    ExtractedSubject subject = extractedSubjects.get(i);
                    subjectJson.append("{\"name\":\"")
                        .append(escapeJson(subject.name))
                        .append("\",\"credit\":")
                        .append(subject.credit)
                        .append(",\"grade\":\"")
                        .append(escapeJson(subject.grade))
                        .append("\"}");
                    if (i < extractedSubjects.size() - 1) subjectJson.append(",");
                }
                subjectJson.append("]");

                // Extra (additive) fields: zero-credit subjects that were left out, and records
                // that were found but rejected as invalid. The frontend may ignore them.
                StringBuilder excludedJson = new StringBuilder("[");
                for (int i = 0; i < excludedZeroCredit.size(); i++) {
                    MarksheetParser.Subject z = excludedZeroCredit.get(i);
                    excludedJson.append("{\"name\":\"").append(escapeJson(z.name))
                        .append("\",\"credit\":0,\"grade\":\"").append(escapeJson(z.grade)).append("\"}");
                    if (i < excludedZeroCredit.size() - 1) excludedJson.append(",");
                }
                excludedJson.append("]");

                StringBuilder warningJson = new StringBuilder("[");
                for (int i = 0; i < parseWarnings.size(); i++) {
                    warningJson.append("\"").append(escapeJson(parseWarnings.get(i))).append("\"");
                    if (i < parseWarnings.size() - 1) warningJson.append(",");
                }
                warningJson.append("]");

                String jsonResponse = "{"
                    + "\"status\":\"SUCCESS\","
                    + "\"message\":\"" + escapeJson(message) + "\","
                    + "\"fileId\":\"" + escapeJson(targetFile.getName()) + "\","
                    + "\"fileName\":\"" + escapeJson(safeName) + "\","
                    + "\"fileType\":\"" + escapeJson(fileType) + "\","
                    + "\"fileSize\":\"" + sizeKb + " KB\","
                    + "\"filePath\":\"uploads/" + escapeJson(targetFile.getName()) + "\","
                    + "\"extractedText\":\"" + escapeJson(extractedText) + "\","
                    + "\"extractedSubjects\":" + subjectJson + ","
                    + "\"excludedSubjects\":" + excludedJson + ","
                    + "\"parseWarnings\":" + warningJson
                    + "}";

                sendJsonResponse(exchange, 200, jsonResponse);
            } else {
                sendJsonResponse(exchange, 405, "{\"status\":\"ERROR\",\"message\":\"Method Not Allowed\"}");
            }
        }

        static class ExtractedSubject {
            String name;
            int credit;
            String grade;

            ExtractedSubject(String name, int credit, String grade) {
                this.name = name;
                this.credit = credit;
                this.grade = grade;
            }
        }

        private String extractPdfText(byte[] fileBytes) throws IOException {
            try (PDDocument document = Loader.loadPDF(fileBytes)) {
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setSortByPosition(true);
                return stripper.getText(document);
            }
        }

        private String extractStringValue(String text, String key) {
            Pattern quoted = Pattern.compile("[\"']?" + key + "[\"']?\\s*:\\s*\"([^\"]*)\"");
            Matcher m = quoted.matcher(text);
            return m.find() ? m.group(1).trim() : "";
        }

        private String escapeJson(String text) {
            if (text == null) {
                return "";
            }

            StringBuilder escaped = new StringBuilder(text.length() + 16);

            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);

                switch (c) {
                    case '"':
                        escaped.append("\\\"");
                        break;
                    case '\\':
                        escaped.append("\\\\");
                        break;
                    case '\b':
                        escaped.append("\\b");
                        break;
                    case '\f':
                        escaped.append("\\f");
                        break;
                    case '\n':
                        escaped.append("\\n");
                        break;
                    case '\r':
                        escaped.append("\\r");
                        break;
                    case '\t':
                        escaped.append("\\t");
                        break;
                    default:
                        if (c < 0x20) {
                            escaped.append(String.format(java.util.Locale.ROOT, "\\u%04x", (int) c));
                        } else {
                            escaped.append(c);
                        }
                        break;
                }
            }

            return escaped.toString();
        }

        private void sendJsonResponse(HttpExchange exchange, int statusCode, String responseText) throws IOException {
            byte[] bytes = responseText.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(statusCode, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    /**
     * Answers /favicon.ico. Serves public/favicon.ico if you add one, otherwise a clean 404
     * with an empty body. This never touches GPA calculation or any API endpoint.
     */
    static class FaviconHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            File icon = new File("public", "favicon.ico");
            if (icon.isFile()) {
                byte[] bytes = Files.readAllBytes(icon.toPath());
                exchange.getResponseHeaders().set("Content-Type", "image/x-icon");
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            } else {
                exchange.sendResponseHeaders(404, -1); // no body
            }
            exchange.close();
        }
    }

    /**
     * MarksheetParser
     *
     * Turns the text that PDFBox extracts from a KRCE-style marksheet into validated subjects.
     * It does NOT assume one subject per line or a fixed order of fields:
     *   1. The text is cut into segments, each ending at a "RESULT STATUS" line.
     *   2. In each segment the course code is located ("GEA1109 -", "CGB1101 -", ...).
     *   3. The segment is flattened into one string. The subject name is everything after
     *      "CODE -" up to the first value label (GRADE POINT / GRADES / CREDIT / RESULT STATUS),
     *      so names split over several lines, or interrupted by the "COURSE NAME" label,
     *      stay whole (e.g. "Engineering Physics Laboratory").
     *   4. GRADE POINT, GRADES and CREDIT are each read from the token that follows their OWN
     *      label, so a grade point can never be mistaken for a credit.
     * Nothing is invented: a record with a missing name, grade or credit is rejected and reported.
     */
    static class MarksheetParser {

        /** A validated subject. */
        static class Subject {
            final String code;
            final String name;
            final String grade;
            final int credit;

            Subject(String code, String name, String grade, int credit) {
                this.code = code;
                this.name = name;
                this.grade = grade;
                this.credit = credit;
            }
        }

        /** Outcome of a parse. Only 'subjects' may be used for GPA calculation. */
        static class Result {
            final List<Subject> subjects = new ArrayList<>();   // valid, credit > 0
            final List<Subject> zeroCredit = new ArrayList<>(); // identified correctly, then excluded
            final List<String> rejected = new ArrayList<>();    // invalid / incomplete records (reasons)
        }

        // "GEA1109 -": 2-4 capital letters, 3-4 digits, optional letter, then a dash.
        private static final Pattern CODE =
            Pattern.compile("(?<![A-Za-z0-9])([A-Z]{2,4}[0-9]{3,4}[A-Z]?)\\s*[-\u2013\u2014]\\s*");
        private static final Pattern RESULT_LINE =
            Pattern.compile("^RESULT\\s+STATUS\\b", Pattern.CASE_INSENSITIVE);
        // Labels that END a subject name. "COURSE NAME" is deliberately NOT here: the extractor
        // may put it in the middle of a name, so it is removed from the name text instead.
        private static final Pattern NAME_STOP =
            Pattern.compile("\\b(GRADE\\s+POINTS?|GRADES|CREDITS?|RESULT\\s+STATUS|SEMESTER)\\b");
        private static final Pattern COURSE_NAME_LABEL = Pattern.compile("\\bCOURSE\\s+NAME\\b");
        private static final Pattern GRADE_FIELD = Pattern.compile("\\bGRADES\\s*:?\\s*(\\S+)");
        private static final Pattern CREDIT_FIELD = Pattern.compile("\\bCREDITS?\\s*:?\\s*(\\S+)");
        private static final Pattern NON_NEGATIVE_NUMBER = Pattern.compile("^\\+?[0-9]+(\\.[0-9]+)?$");
        private static final Pattern NEGATIVE_NUMBER = Pattern.compile("^-[0-9]+(\\.[0-9]+)?$");

        static Result parse(String text) {
            Result result = new Result();
            if (text == null || text.trim().isEmpty()) return result;

            // Normalise: drop blank lines, collapse whitespace, treat NBSP as a space.
            List<String> lines = new ArrayList<>();
            for (String raw : text.split("\\R")) {
                String t = raw.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
                if (!t.isEmpty()) lines.add(t);
            }

            // Step 1: segments terminated by a RESULT STATUS line.
            List<List<String>> segments = new ArrayList<>();
            List<String> current = new ArrayList<>();
            for (String line : lines) {
                current.add(line);
                if (RESULT_LINE.matcher(line).find()) {
                    segments.add(current);
                    current = new ArrayList<>();
                }
            }
            if (!current.isEmpty()) segments.add(current);

            Map<String, String> seen = new HashMap<>(); // course code -> signature (de-duplication)
            for (List<String> segment : segments) {
                List<Integer> codeLines = new ArrayList<>();
                for (int i = 0; i < segment.size(); i++) {
                    if (CODE.matcher(segment.get(i)).find()) codeLines.add(i);
                }
                // No course code => headings, student info, footer totals. Never a subject.
                if (codeLines.isEmpty()) continue;

                if (codeLines.size() == 1) {
                    handleRecord(join(segment, 0, segment.size()), result, seen);
                } else {
                    // Several codes in one segment (a RESULT STATUS line was missing): slice per code.
                    for (int k = 0; k < codeLines.size(); k++) {
                        int end = (k + 1 < codeLines.size()) ? codeLines.get(k + 1) : segment.size();
                        handleRecord(join(segment, codeLines.get(k), end), result, seen);
                    }
                }
            }
            return result;
        }

        private static String join(List<String> lines, int from, int to) {
            StringBuilder sb = new StringBuilder();
            for (int i = from; i < to; i++) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(lines.get(i));
            }
            return sb.toString();
        }

        private static void handleRecord(String block, Result result, Map<String, String> seen) {
            Matcher codeMatcher = CODE.matcher(block);
            if (!codeMatcher.find()) return;
            String code = codeMatcher.group(1);

            // Subject name: text after "CODE -" up to the first value label; the stray
            // "COURSE NAME" label (which may sit inside the name) is removed.
            String afterCode = block.substring(codeMatcher.end());
            Matcher stop = NAME_STOP.matcher(afterCode);
            String name = stop.find() ? afterCode.substring(0, stop.start()) : afterCode;
            name = COURSE_NAME_LABEL.matcher(name).replaceAll(" ").replaceAll("\\s+", " ").trim();

            String gradeRaw = firstGroup(GRADE_FIELD, block);
            String creditRaw = firstGroup(CREDIT_FIELD, block);
            String label = code + (name.isEmpty() ? "" : " " + name);

            // ---- Validation: a missing name, grade or credit is reported, never invented ----
            if (name.isEmpty()) {
                result.rejected.add(code + ": course name not found");
                return;
            }
            String grade = (gradeRaw == null) ? null : gradeRaw.toUpperCase();
            if (grade == null || !GpaCalculator.isValidGrade(grade)) {
                result.rejected.add(label + ": missing or invalid grade" + (gradeRaw == null ? "" : " '" + gradeRaw + "'"));
                return;
            }
            if (creditRaw == null) {
                result.rejected.add(label + ": CREDIT value not found");
                return;
            }
            if (NEGATIVE_NUMBER.matcher(creditRaw).matches()) {
                result.rejected.add(label + ": negative credit '" + creditRaw + "'");
                return;
            }
            if (!NON_NEGATIVE_NUMBER.matcher(creditRaw).matches()) {
                result.rejected.add(label + ": invalid credit '" + creditRaw + "'");
                return;
            }
            int credit;
            try {
                // The calculator works with whole-number credits; a fractional credit is rejected
                // instead of being rounded.
                credit = new java.math.BigDecimal(creditRaw.startsWith("+") ? creditRaw.substring(1) : creditRaw)
                    .intValueExact();
            } catch (ArithmeticException ex) {
                result.rejected.add(label + ": credit '" + creditRaw + "' is not a whole number");
                return;
            }

            // De-duplicate by course code (identical repeats are ignored silently).
            String signature = name + "|" + grade + "|" + credit;
            if (seen.containsKey(code)) {
                if (!seen.get(code).equals(signature)) {
                    result.rejected.add(label + ": conflicting duplicate record ignored");
                }
                return;
            }
            seen.put(code, signature);

            // ---- ZERO-CREDIT EXCLUSION ----
            // Reached only after the record was identified correctly. A subject with 0 credits is
            // kept apart from 'subjects', so it can never reach totals, GPA, grade statistics or
            // analysis. The credit is never replaced by 1.
            Subject subject = new Subject(code, name, grade, credit);
            if (credit == 0) {
                result.zeroCredit.add(subject);
            } else {
                result.subjects.add(subject);
            }
        }

        private static String firstGroup(Pattern pattern, String text) {
            Matcher m = pattern.matcher(text);
            return m.find() ? m.group(1) : null;
        }
    }

    /**
     * Serves static frontend files (HTML, CSS, JS) from the 'public' directory
     */
    static class StaticFileHandler implements HttpHandler {
        private final File publicDir = new File("public");

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();

            // Default to index.html for root path
            if (path.equals("/") || path.isEmpty()) {
                path = "/index.html";
            }

            File targetFile = new File(publicDir, path.substring(1));

            // Prevent directory traversal
            String root = publicDir.getCanonicalPath();
            String target = targetFile.getCanonicalPath();
            if (!target.equals(root) && !target.startsWith(root + File.separator)) {
                sendError(exchange, 403, "Access Forbidden");
                return;
            }

            if (!targetFile.exists() || targetFile.isDirectory()) {
                sendError(exchange, 404, "File Not Found");
                return;
            }

            String mimeType = getMimeType(targetFile.getName());
            byte[] fileBytes = Files.readAllBytes(targetFile.toPath());

            exchange.getResponseHeaders().set("Content-Type", mimeType);
            exchange.sendResponseHeaders(200, fileBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(fileBytes);
            }
        }

        private String getMimeType(String filename) {
            if (filename.endsWith(".html")) return "text/html; charset=UTF-8";
            if (filename.endsWith(".css"))  return "text/css; charset=UTF-8";
            if (filename.endsWith(".js"))   return "application/javascript; charset=UTF-8";
            if (filename.endsWith(".json")) return "application/json; charset=UTF-8";
            if (filename.endsWith(".png"))  return "image/png";
            if (filename.endsWith(".svg"))  return "image/svg+xml";
            return "text/plain; charset=UTF-8";
        }

        private void sendError(HttpExchange exchange, int statusCode, String message) throws IOException {
            byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
            exchange.sendResponseHeaders(statusCode, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }
}