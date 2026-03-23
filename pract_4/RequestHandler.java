// RequestHandler.java
// COS332 Practical Assignment 4
//
// Handles both GET and POST requests.
// POST is needed for file (photo) uploads — multipart/form-data.
// GET handles all other form submissions and navigation.
//
// RFC 2616 features:
//   - HEAD request support        (Section 9.4)
//   - Correct status codes        (200, 400, 404)
//   - Date header                 (Section 14.18)
//   - Server header               (Section 14.38)
//   - Last-Modified header        (Section 14.29)
//   - Content-Length header       (Section 14.13)
//   - Content-Type header         (Section 14.17)
//   - Serve JPEG/PNG images       (binary HTTP transfer)
//   - favicon.ico clean 404

import java.io.*;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.*;

public class RequestHandler implements Runnable {
    private Socket socket;
    private AppointmentStore store;

    // RFC 1123 date format for HTTP headers
    private static final SimpleDateFormat HTTP_DATE =
        new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'",
                             Locale.ENGLISH);
    static {
        HTTP_DATE.setTimeZone(TimeZone.getTimeZone("GMT"));
    }

    private static final long SERVER_START = System.currentTimeMillis();

    public RequestHandler(Socket socket, AppointmentStore store) {
        this.socket = socket;
        this.store  = store;
    }

    @Override
    public void run() {
        try {
            InputStream  rawIn = socket.getInputStream();
            OutputStream out   = socket.getOutputStream();

            // ── Step 1: Read the request line ─────────────────
            String requestLine = readLine(rawIn);
            if (requestLine == null || requestLine.isEmpty()) return;
            System.out.println("REQUEST: " + requestLine);

            // ── Step 2: Read all headers into a map ───────────
            Map<String, String> headers = new HashMap<>();
            String headerLine;
            while ((headerLine = readLine(rawIn)) != null
                   && !headerLine.isEmpty()) {
                int colon = headerLine.indexOf(':');
                if (colon > 0) {
                    String key = headerLine.substring(0, colon)
                                           .trim().toLowerCase();
                    String val = headerLine.substring(colon + 1).trim();
                    headers.put(key, val);
                }
            }

            // ── Step 3: Parse the request line ────────────────
            String[] parts = requestLine.split(" ");
            if (parts.length < 2) return;
            String method = parts[0];  // "GET" or "POST" or "HEAD"
            String full   = parts[1];

            // ── Step 4: Separate path and query string ─────────
            String path, query;
            int q = full.indexOf('?');
            if (q >= 0) {
                path  = full.substring(0, q);
                query = full.substring(q + 1);
            } else {
                path  = full;
                query = "";
            }

            // ── Step 5: Read request body for POST ───────────
            // POST body length is in the Content-Length header
            byte[] body = new byte[0];
            if ("POST".equals(method)) {
                String lenStr = headers.get("content-length");
                if (lenStr != null) {
                    int len = Integer.parseInt(lenStr.trim());
                    body = readBytes(rawIn, len);
                }
            }

            // ── Step 6: Route to the right handler ────────────
            Map<String, String> params = parseQuery(query);
            HttpResponse response = route(method, path,
                                          params, headers, body);

            // ── Step 7: Send the response ─────────────────────
            // HEAD = headers only, no body (RFC 2616 Section 9.4)
            if ("HEAD".equals(method)) {
                sendHeaders(out, response);
            } else {
                sendFull(out, response);
            }

        } catch (IOException e) {
            System.err.println("Handler error: " + e.getMessage());
        } finally {
            try { socket.close(); } catch (IOException e) {}
        }
    }

    // ── Routing ───────────────────────────────────────────────
    private HttpResponse route(String method, String path,
                                Map<String, String> params,
                                Map<String, String> headers,
                                byte[] body) {
        // POST requests
        if ("POST".equals(method)) {
            switch (path) {
                case "/do-add":  return doAddPost(headers, body);
                case "/do-edit": return doEditPost(headers, body);
                default:         return html(404, HtmlBuilder.notFound());
            }
        }

        // GET (and HEAD) requests
        switch (path) {
            case "/":
                String sort = params.getOrDefault("sort", "");
                return html(200, HtmlBuilder.home(
                    store.getAll(sort), "", sort, store));

            case "/add":
                return html(200, HtmlBuilder.addForm(""));

            case "/edit":
                return doEditForm(params);

            case "/confirm":
                return doConfirm(params);

            case "/delete":
                return doDelete(params);

            case "/search":
                return doSearch(params);

            case "/photo":
                // Serve a stored appointment photo
                // e.g. /photo?name=photo_1.jpg
                return servePhoto(params);

            case "/image":
                // Serve the server logo (RFC 2616 binary demo)
                return serveFile("logo.jpg");

            case "/favicon.ico":
                return empty(404);

            default:
                return html(404, HtmlBuilder.notFound());
        }
    }

    // ── POST: Add appointment with optional photo ─────────────
    // The form uses multipart/form-data so the browser can send
    // both text fields and a binary image file in one request
    private HttpResponse doAddPost(Map<String, String> headers,
                                    byte[] body) {
        // Extract the boundary from Content-Type header
        // e.g. "multipart/form-data; boundary=----WebKitFormBoundary..."
        String boundary = extractBoundary(
            headers.get("content-type"));

        if (boundary == null) {
            return html(400, HtmlBuilder.addForm(
                "Bad request — missing boundary."));
        }

        // Parse the multipart body into individual fields
        Map<String, MultipartParser.Field> fields =
            MultipartParser.parse(body, boundary);

        // Pull out the text fields
        String date   = fieldStr(fields, "date").trim();
        String time   = fieldStr(fields, "time").trim();
        String person = fieldStr(fields, "person").trim();
        String notes  = fieldStr(fields, "notes").trim();

        // Validate all fields before saving
        String err = validate(date, time, person, notes);
        if (err != null) {
            return html(400, HtmlBuilder.addForm(err));
        }

        // Pull out the photo (if one was uploaded)
        byte[] photoBytes = null;
        String photoExt   = "jpg";
        String photoName  = "";
        MultipartParser.Field photoField = fields.get("photo");
        if (photoField != null
            && photoField.filename != null
            && !photoField.filename.isEmpty()
            && photoField.value != null
            && photoField.value.length > 0) {

            // Validate the photo before accepting it
            String photoErr = validatePhoto(
                photoField.value, photoField.filename);
            if (photoErr != null)
                return html(400, HtmlBuilder.addForm(photoErr));

            photoBytes = photoField.value;
            photoExt   = fileExtension(photoField.filename);
            photoName  = photoField.filename;
        }

        store.add(date, time, person, notes, photoBytes, photoExt);
        return html(200, HtmlBuilder.home(
            store.getAll(), "Appointment added!", store));
    }

    // ── POST: Edit appointment with optional new photo ────────
    private HttpResponse doEditPost(Map<String, String> headers,
                                     byte[] body) {
        String boundary = extractBoundary(
            headers.get("content-type"));
        if (boundary == null)
            return html(400, HtmlBuilder.notFound());

        Map<String, MultipartParser.Field> fields =
            MultipartParser.parse(body, boundary);

        String id     = fieldStr(fields, "id").trim();
        String date   = fieldStr(fields, "date").trim();
        String time   = fieldStr(fields, "time").trim();
        String person = fieldStr(fields, "person").trim();
        String notes  = fieldStr(fields, "notes").trim();

        // Validate all fields before saving
        String err = validate(date, time, person, notes);
        if (err != null) {
            Appointment a = store.findById(id);
            if (a == null) return html(404, HtmlBuilder.notFound());
            return html(400, HtmlBuilder.editForm(a, err));
        }

        byte[] photoBytes = null;
        String photoExt   = "jpg";
        MultipartParser.Field photoField = fields.get("photo");
        if (photoField != null
            && photoField.filename != null
            && !photoField.filename.isEmpty()
            && photoField.value != null
            && photoField.value.length > 0) {

            // Validate the photo before accepting it
            String photoErr = validatePhoto(
                photoField.value, photoField.filename);
            if (photoErr != null) {
                Appointment existing = store.findById(id);
                if (existing == null)
                    return html(404, HtmlBuilder.notFound());
                return html(400,
                    HtmlBuilder.editForm(existing, photoErr));
            }

            photoBytes = photoField.value;
            photoExt   = fileExtension(photoField.filename);
        }

        boolean ok = store.update(id, date, time, person, notes,
                                   photoBytes, photoExt);
        return html(200, HtmlBuilder.home(store.getAll(),
            ok ? "Appointment updated!" : "Appointment not found.",
            store));
    }

    // ── Validation ────────────────────────────────────────────
    // Checks date, time, person and notes fields.
    // Returns an error message if anything fails, or null if OK.
    // All validation is server-side — no JavaScript used.
    private String validate(String date, String time,
                             String person, String notes) {

        // ── 1. Required fields ────────────────────────────────
        if (date.isEmpty())   return "Date is required.";
        if (time.isEmpty())   return "Time is required.";
        if (person.isEmpty()) return "Person / Event is required.";

        // ── 2. Date format: must be exactly YYYY-MM-DD ────────
        if (!date.matches("\\d{4}-\\d{2}-\\d{2}"))
            return "Date must be in YYYY-MM-DD format "
                 + "(e.g. 2026-03-25). You entered: " + date;

        int year, month, day;
        try {
            year  = Integer.parseInt(date.substring(0, 4));
            month = Integer.parseInt(date.substring(5, 7));
            day   = Integer.parseInt(date.substring(8, 10));
        } catch (NumberFormatException e) {
            return "Date contains non-numeric characters. "
                 + "Use YYYY-MM-DD format.";
        }

        // ── 3. Date value ranges ──────────────────────────────
        if (year < 2000 || year > 2100)
            return "Year must be between 2000 and 2100. "
                 + "You entered: " + year;
        if (month < 1 || month > 12)
            return "Month must be between 01 and 12. "
                 + "You entered: " + date.substring(5, 7);
        if (day < 1 || day > 31)
            return "Day must be between 01 and 31. "
                 + "You entered: " + date.substring(8, 10);

        // ── 4. Days per month + leap year ─────────────────────
        int[] maxDays = {0,31,28,31,30,31,30,31,31,30,31,30,31};
        boolean leapYear = (year % 4 == 0 && year % 100 != 0)
                        || (year % 400 == 0);
        if (leapYear) maxDays[2] = 29;
        if (day > maxDays[month])
            return "Day " + day + " does not exist in month "
                 + month + " of " + year
                 + ". That month has " + maxDays[month] + " days.";

        // ── 5. Past date check ────────────────────────────────
        // Get today's date from the system calendar
        Calendar today = Calendar.getInstance();
        int todayYear  = today.get(Calendar.YEAR);
        int todayMonth = today.get(Calendar.MONTH) + 1; // 0-based
        int todayDay   = today.get(Calendar.DAY_OF_MONTH);

        // Build comparable integers: YYYYMMDD
        int apptInt  = year  * 10000 + month  * 100 + day;
        int todayInt = todayYear * 10000 + todayMonth * 100 + todayDay;

        if (apptInt < todayInt)
            return "Appointment date " + date
                 + " is in the past. Please choose today or a future date.";

        // ── 6. Time format: must be exactly HH:MM ─────────────
        if (!time.matches("\\d{2}:\\d{2}"))
            return "Time must be in HH:MM format "
                 + "(e.g. 14:30). You entered: " + time;

        int hour, minute;
        try {
            hour   = Integer.parseInt(time.substring(0, 2));
            minute = Integer.parseInt(time.substring(3, 5));
        } catch (NumberFormatException e) {
            return "Time contains non-numeric characters. "
                 + "Use HH:MM format (e.g. 14:30).";
        }

        // ── 7. Time value ranges ──────────────────────────────
        if (hour < 0 || hour > 23)
            return "Hour must be between 00 and 23. "
                 + "You entered: " + time.substring(0, 2);
        if (minute < 0 || minute > 59)
            return "Minute must be between 00 and 59. "
                 + "You entered: " + time.substring(3, 5);

        // ── 8. Length limits ──────────────────────────────────
        if (person.length() > 100)
            return "Person / Event must be 100 characters or fewer "
                 + "(you entered " + person.length() + ").";
        if (notes.length() > 300)
            return "Notes must be 300 characters or fewer "
                 + "(you entered " + notes.length() + ").";

        // All checks passed
        return null;
    }

    // ── Photo validation ──────────────────────────────────────
    // Checks file type and file size.
    // Returns an error message if invalid, or null if OK.
    // photoBytes = the raw uploaded bytes
    // filename   = original filename from the browser e.g. "face.jpg"
    private String validatePhoto(byte[] photoBytes, String filename) {

        // No photo uploaded — that is fine, it is optional
        if (photoBytes == null || photoBytes.length == 0)
            return null;

        // ── File size limit: 5 MB ─────────────────────────────
        int maxBytes = 5 * 1024 * 1024; // 5 MB in bytes
        if (photoBytes.length > maxBytes)
            return "Photo is too large ("
                 + (photoBytes.length / 1024) + " KB). "
                 + "Maximum allowed size is 5 MB.";

        // ── File type: only jpg, jpeg, png, gif ───────────────
        if (filename == null || filename.isEmpty())
            return "Could not determine file type. "
                 + "Please upload a JPG, PNG or GIF image.";

        String lower = filename.toLowerCase();
        if (!lower.endsWith(".jpg")
            && !lower.endsWith(".jpeg")
            && !lower.endsWith(".png")
            && !lower.endsWith(".gif")
            && !lower.endsWith(".webp")) {
            return "Only JPG, PNG, GIF or WEBP images are allowed. "
                 + "You uploaded: " + filename;
        }

        // ── Magic bytes check ─────────────────────────────────
        // Check the actual file content, not just the filename.
        // A renamed .exe file still has the wrong magic bytes.
        // JPEG starts with FF D8 FF
        // PNG  starts with 89 50 4E 47
        // GIF  starts with 47 49 46 38
        // WEBP starts with 52 49 46 46 (RIFF)
        if (photoBytes.length < 4)
            return "Uploaded file is too small to be a valid image.";

        boolean validMagic = false;

        // JPEG: first 3 bytes are FF D8 FF
        if ((photoBytes[0] & 0xFF) == 0xFF
            && (photoBytes[1] & 0xFF) == 0xD8
            && (photoBytes[2] & 0xFF) == 0xFF) {
            validMagic = true;
        }
        // PNG: first 4 bytes are 89 50 4E 47
        if ((photoBytes[0] & 0xFF) == 0x89
            && (photoBytes[1] & 0xFF) == 0x50
            && (photoBytes[2] & 0xFF) == 0x4E
            && (photoBytes[3] & 0xFF) == 0x47) {
            validMagic = true;
        }
        // GIF: first 3 bytes are 47 49 46 ("GIF")
        if ((photoBytes[0] & 0xFF) == 0x47
            && (photoBytes[1] & 0xFF) == 0x49
            && (photoBytes[2] & 0xFF) == 0x46) {
            validMagic = true;
        }
        // WEBP: bytes 0-3 are "RIFF" (52 49 46 46)
        if ((photoBytes[0] & 0xFF) == 0x52
            && (photoBytes[1] & 0xFF) == 0x49
            && (photoBytes[2] & 0xFF) == 0x46
            && (photoBytes[3] & 0xFF) == 0x46) {
            validMagic = true;
        }

        if (!validMagic)
            return "The uploaded file does not appear to be a valid "
                 + "image. Only JPG, PNG, GIF or WEBP files are accepted. "
                 + "File: " + filename;

        return null; // photo is valid
    }

    // ── GET handlers ──────────────────────────────────────────
    private HttpResponse doEditForm(Map<String, String> p) {
        String id = p.getOrDefault("id", "").trim();
        Appointment a = store.findById(id);
        if (a == null) return html(404, HtmlBuilder.notFound());
        return html(200, HtmlBuilder.editForm(a, ""));
    }

    private HttpResponse doConfirm(Map<String, String> p) {
        String id = p.getOrDefault("id", "").trim();
        Appointment a = store.findById(id);
        if (a == null) return html(404, HtmlBuilder.notFound());
        return html(200, HtmlBuilder.confirmDelete(a));
    }

    private HttpResponse doDelete(Map<String, String> p) {
        String id = p.getOrDefault("id", "").trim();
        boolean ok = store.delete(id);
        return html(200, HtmlBuilder.home(store.getAll(),
            ok ? "Appointment deleted." : "Appointment not found.",
            store));
    }

    private HttpResponse doSearch(Map<String, String> p) {
        if (!p.containsKey("q"))
            return html(200, HtmlBuilder.search(null, "", false));
        String kw = p.getOrDefault("q", "").trim();
        return html(200, HtmlBuilder.search(
            store.search(kw), kw, true));
    }

    // ── Serve a stored appointment photo ──────────────────────
    // URL: /photo?name=photo_1.jpg
    // This is the key extra-mark feature — serving binary image
    // data over HTTP with correct Content-Type header
    private HttpResponse servePhoto(Map<String, String> p) {
        String name = p.getOrDefault("name", "").trim();
        if (name.isEmpty() || name.contains("..")) {
            // Reject empty or path-traversal attempts
            return empty(404);
        }
        byte[] bytes = store.getPhotoBytes(name);
        if (bytes == null) return empty(404);

        String contentType = store.getPhotoContentType(name);
        return new HttpResponse(200, contentType, bytes, SERVER_START);
    }

    // ── Serve the logo.jpg file (RFC 2616 binary demo) ────────
    private HttpResponse serveFile(String filename) {
        File f = new File(filename);
        if (!f.exists()) return empty(404);
        try (FileInputStream fis = new FileInputStream(f)) {
            byte[] bytes = new byte[(int) f.length()];
            fis.read(bytes);
            return new HttpResponse(200, "image/jpeg",
                                    bytes, f.lastModified());
        } catch (IOException e) {
            return empty(500);
        }
    }

    // ── HttpResponse inner class ──────────────────────────────
    private static class HttpResponse {
        int    status;
        String contentType;
        byte[] body;
        long   lastModified;

        HttpResponse(int status, String contentType,
                     byte[] body, long lastModified) {
            this.status       = status;
            this.contentType  = contentType;
            this.body         = body;
            this.lastModified = lastModified;
        }
    }

    private HttpResponse html(int status, String htmlBody) {
        try {
            return new HttpResponse(status,
                "text/html; charset=UTF-8",
                htmlBody.getBytes("UTF-8"), SERVER_START);
        } catch (UnsupportedEncodingException e) {
            return new HttpResponse(500,
                "text/html; charset=UTF-8",
                new byte[0], SERVER_START);
        }
    }

    private HttpResponse empty(int status) {
        return new HttpResponse(status, "text/plain",
                                new byte[0], SERVER_START);
    }

    // ── Send full response (headers + body) ───────────────────
    private void sendFull(OutputStream out,
                          HttpResponse r) throws IOException {
        sendHeaders(out, r);
        out.write(r.body);
        out.flush();
    }

    // ── Send headers only (for HEAD requests) ─────────────────
    private void sendHeaders(OutputStream out,
                              HttpResponse r) throws IOException {
        String reason;
        switch (r.status) {
            case 200: reason = "OK";           break;
            case 400: reason = "Bad Request";  break;
            case 404: reason = "Not Found";    break;
            case 500: reason = "Server Error"; break;
            default:  reason = "OK";
        }

        String now, lastMod;
        synchronized (HTTP_DATE) {
            now     = HTTP_DATE.format(new Date());
            lastMod = HTTP_DATE.format(new Date(r.lastModified));
        }

        String headers =
            "HTTP/1.1 " + r.status + " " + reason + "\r\n"
          + "Date: "          + now     + "\r\n"   // RFC 14.18
          + "Server: COS332-AppointmentServer/1.0\r\n" // RFC 14.38
          + "Last-Modified: " + lastMod + "\r\n"   // RFC 14.29
          + "Content-Type: "  + r.contentType + "\r\n" // RFC 14.17
          + "Content-Length: "+ r.body.length + "\r\n" // RFC 14.13
          + "Connection: close\r\n"
          + "\r\n";

        out.write(headers.getBytes("UTF-8"));
        out.flush();
    }

    // ── Read one text line from the raw InputStream ───────────
    // Stops at \n (handles both \r\n and \n)
    private String readLine(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\n') break;
            if (b != '\r') sb.append((char) b);
        }
        return b == -1 && sb.length() == 0 ? null : sb.toString();
    }

    // ── Read exactly len bytes from the InputStream ───────────
    // Used for reading the POST body
    private byte[] readBytes(InputStream in, int len)
            throws IOException {
        byte[] buf = new byte[len];
        int read = 0;
        while (read < len) {
            int n = in.read(buf, read, len - read);
            if (n == -1) break;
            read += n;
        }
        return buf;
    }

    // ── Parse query string into a Map ─────────────────────────
    private Map<String, String> parseQuery(String query) {
        Map<String, String> map = new HashMap<>();
        if (query == null || query.isEmpty()) return map;
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0)
                map.put(decode(pair.substring(0, eq)),
                        decode(pair.substring(eq + 1)));
        }
        return map;
    }

    // ── Manual URL decode — no URLDecoder library ─────────────
    private String decode(String s) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '+') {
                sb.append(' '); i++;
            } else if (c == '%' && i + 2 < s.length()) {
                try {
                    sb.append((char) Integer.parseInt(
                        s.substring(i + 1, i + 3), 16));
                    i += 3;
                } catch (NumberFormatException e) {
                    sb.append(c); i++;
                }
            } else {
                sb.append(c); i++;
            }
        }
        return sb.toString();
    }

    // ── Extract boundary from Content-Type header ─────────────
    // e.g. "multipart/form-data; boundary=----WebKit123"
    //       returns "----WebKit123"
    private String extractBoundary(String contentType) {
        if (contentType == null) return null;
        for (String part : contentType.split(";")) {
            part = part.trim();
            if (part.startsWith("boundary=")) {
                return part.substring("boundary=".length()).trim();
            }
        }
        return null;
    }

    // ── Get extension from a filename ─────────────────────────
    // e.g. "face.jpg" → "jpg"
    private String fileExtension(String filename) {
        if (filename == null) return "jpg";
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) return "jpg";
        return filename.substring(dot + 1).toLowerCase();
    }

    // ── Helper: get a text field value from multipart fields ──
    private String fieldStr(Map<String, MultipartParser.Field> fields,
                             String name) {
        MultipartParser.Field f = fields.get(name);
        return f == null ? "" : f.asString();
    }
}
