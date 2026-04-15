// RequestHandler.java
// COS332 Practical Assignment 4
// Student: u26535272 Martin Phalane
//
// Handles every HTTP request in its own thread.
// All communication happens via raw sockets — no HTTP libraries.
//
// RFC 2616 features implemented:
//   HEAD request support        (Section 9.4)  — headers only, no body
//   GET and POST                (Sections 9.3 and 9.5)
//   Status codes 200 / 302 / 400 / 404 / 500  (Section 10)
//   Date header                 (Section 14.18) — RFC 1123 format in GMT
//   Server header               (Section 14.38)
//   Last-Modified header        (Section 14.29)
//   Content-Type header         (Section 14.17)
//   Content-Length header       (Section 14.13)
//   Location header for 302     (Section 14.30) — POST-Redirect-GET
//   Connection: close           (Section 14.10)
//   Serving binary images       (JPEG/PNG over HTTP)
//   Clean 404 for favicon.ico

import java.io.*;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.*;

public class RequestHandler implements Runnable {

    private final Socket          socket;
    private final AppointmentStore store;

    // RFC 1123 date format required by the HTTP Date header (Section 14.18)
    private static final SimpleDateFormat HTTP_DATE =
        new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'",
                             Locale.ENGLISH);
    static {
        HTTP_DATE.setTimeZone(TimeZone.getTimeZone("GMT"));
    }

    // Used as the Last-Modified timestamp for dynamically generated pages
    private static final long SERVER_START = System.currentTimeMillis();

    public RequestHandler(Socket socket, AppointmentStore store) {
        this.socket = socket;
        this.store  = store;
    }

    // ── Thread entry point ────────────────────────────────────
    @Override
    public void run() {
        try {
            InputStream  rawIn = socket.getInputStream();
            OutputStream out   = socket.getOutputStream();

            // ── 1. Read request line ───────────────────────────
            String requestLine = readLine(rawIn);
            if (requestLine == null || requestLine.isEmpty()) return;
            System.out.println("REQUEST: " + requestLine);

            // ── 2. Read all request headers ────────────────────
            Map<String, String> reqHeaders = new HashMap<>();
            String headerLine;
            while ((headerLine = readLine(rawIn)) != null
                   && !headerLine.isEmpty()) {
                int colon = headerLine.indexOf(':');
                if (colon > 0) {
                    String key = headerLine.substring(0, colon)
                                           .trim().toLowerCase();
                    String val = headerLine.substring(colon + 1).trim();
                    reqHeaders.put(key, val);
                }
            }

            // ── 3. Parse request line ──────────────────────────
            String[] parts = requestLine.split(" ");
            if (parts.length < 2) return;
            String method = parts[0];           // GET / POST / HEAD
            String full   = parts[1];

            // ── 4. Separate path and query string ──────────────
            String path, query;
            int q = full.indexOf('?');
            if (q >= 0) {
                path  = full.substring(0, q);
                query = full.substring(q + 1);
            } else {
                path  = full;
                query = "";
            }

            // ── 5. Read POST body ──────────────────────────────
            // Body length is declared in the Content-Length header
            byte[] body = new byte[0];
            if ("POST".equals(method)) {
                String lenStr = reqHeaders.get("content-length");
                if (lenStr != null) {
                    try {
                        int len = Integer.parseInt(lenStr.trim());
                        body = readBytes(rawIn, len);
                    } catch (NumberFormatException e) {
                        System.err.println("Bad Content-Length: " + lenStr);
                    }
                }
            }

            // ── 6. Route to the right handler ──────────────────
            Map<String, String> params = parseQuery(query);
            HttpResponse response = route(method, path,
                                          params, reqHeaders, body);

            // ── 7. Send response ───────────────────────────────
            // HEAD returns headers only — no body (RFC 2616 Section 9.4)
            if ("HEAD".equals(method)) {
                sendHeaders(out, response);
            } else {
                sendFull(out, response);
            }

        } catch (IOException e) {
            System.err.println("Handler error: " + e.getMessage());
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    // ── Routing ───────────────────────────────────────────────
    private HttpResponse route(String method, String path,
                                Map<String, String> params,
                                Map<String, String> headers,
                                byte[] body) {
        // POST endpoints
        if ("POST".equals(method)) {
            switch (path) {
                case "/do-add":  return doAddPost(headers, body);
                case "/do-edit": return doEditPost(headers, body);
                default:         return html(404, HtmlBuilder.notFound());
            }
        }

        // GET (and HEAD) endpoints
        switch (path) {
            case "/": {
                String sort  = params.getOrDefault("sort", "");
                return html(200, HtmlBuilder.home(
                    store.getAll(sort), "", sort, store));
            }

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
                return servePhoto(params);

            case "/favicon.ico":
                return empty(404);

            default:
                return html(404, HtmlBuilder.notFound());
        }
    }

    // ── POST: Add appointment ─────────────────────────────────
    // Uses multipart/form-data so the browser can send both text fields
    // and a binary image file in one request.
    // After a successful save we return a 302 redirect to "/" so that
    // hitting Refresh in the browser does NOT re-submit the POST.
    // This is the standard POST → Redirect → GET pattern.
    private HttpResponse doAddPost(Map<String, String> headers,
                                    byte[] body) {
        String boundary = extractBoundary(headers.get("content-type"));
        if (boundary == null)
            return html(400, HtmlBuilder.addForm(
                "Bad request — missing multipart boundary."));

        Map<String, MultipartParser.Field> fields =
            MultipartParser.parse(body, boundary);

        String date   = fieldStr(fields, "date").trim();
        String time   = fieldStr(fields, "time").trim();
        String person = fieldStr(fields, "person").trim();
        String notes  = fieldStr(fields, "notes").trim();

        // Server-side validation — no JavaScript involved
        String err = validate(date, time, person, notes);
        if (err != null)
            return html(400, HtmlBuilder.addForm(err));

        // Optional photo
        byte[] photoBytes = null;
        String photoExt   = "jpg";
        MultipartParser.Field photoField = fields.get("photo");
        if (photoField != null
                && photoField.filename != null
                && !photoField.filename.isEmpty()
                && photoField.value != null
                && photoField.value.length > 0) {

            String photoErr =
                validatePhoto(photoField.value, photoField.filename);
            if (photoErr != null)
                return html(400, HtmlBuilder.addForm(photoErr));

            photoBytes = photoField.value;
            photoExt   = fileExtension(photoField.filename);
        }

        store.add(date, time, person, notes, photoBytes, photoExt);
        return redirect("/", "Appointment added successfully!");
    }

    // ── POST: Edit appointment ────────────────────────────────
    private HttpResponse doEditPost(Map<String, String> headers,
                                     byte[] body) {
        String boundary = extractBoundary(headers.get("content-type"));
        if (boundary == null)
            return html(400, HtmlBuilder.notFound());

        Map<String, MultipartParser.Field> fields =
            MultipartParser.parse(body, boundary);

        String id     = fieldStr(fields, "id").trim();
        String date   = fieldStr(fields, "date").trim();
        String time   = fieldStr(fields, "time").trim();
        String person = fieldStr(fields, "person").trim();
        String notes  = fieldStr(fields, "notes").trim();

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

            String photoErr =
                validatePhoto(photoField.value, photoField.filename);
            if (photoErr != null) {
                Appointment existing = store.findById(id);
                if (existing == null)
                    return html(404, HtmlBuilder.notFound());
                return html(400, HtmlBuilder.editForm(existing, photoErr));
            }

            photoBytes = photoField.value;
            photoExt   = fileExtension(photoField.filename);
        }

        store.update(id, date, time, person, notes, photoBytes, photoExt);
        return redirect("/", "Appointment updated successfully!");
    }

    // ── GET: Show edit form pre-filled ────────────────────────
    private HttpResponse doEditForm(Map<String, String> p) {
        String id = p.getOrDefault("id", "").trim();
        Appointment a = store.findById(id);
        if (a == null) return html(404, HtmlBuilder.notFound());
        return html(200, HtmlBuilder.editForm(a, ""));
    }

    // ── GET: Confirm-delete page ──────────────────────────────
    private HttpResponse doConfirm(Map<String, String> p) {
        String id = p.getOrDefault("id", "").trim();
        Appointment a = store.findById(id);
        if (a == null) return html(404, HtmlBuilder.notFound());
        return html(200, HtmlBuilder.confirmDelete(a));
    }

    // ── GET: Delete and redirect ──────────────────────────────
    private HttpResponse doDelete(Map<String, String> p) {
        String id = p.getOrDefault("id", "").trim();
        store.delete(id);
        return redirect("/", "Appointment deleted.");
    }

    // ── GET: Search ───────────────────────────────────────────
    private HttpResponse doSearch(Map<String, String> p) {
        if (!p.containsKey("q"))
            return html(200, HtmlBuilder.search(null, "", false));
        String kw = p.getOrDefault("q", "").trim();
        return html(200, HtmlBuilder.search(store.search(kw), kw, true));
    }

    // ── GET: Serve stored photo ───────────────────────────────
    // URL: /photo?name=photo_1.jpg
    // This demonstrates binary HTTP transfer with the correct
    // Content-Type header — one of the explicit extra-mark features
    // mentioned in the assignment spec.
    private HttpResponse servePhoto(Map<String, String> p) {
        String name = p.getOrDefault("name", "").trim();
        // Reject empty names and path-traversal attempts
        if (name.isEmpty() || name.contains("..") || name.contains("/")
                || name.contains("\\"))
            return empty(404);

        byte[] bytes = store.getPhotoBytes(name);
        if (bytes == null) return empty(404);

        String contentType = store.getPhotoContentType(name);
        return new HttpResponse(200, contentType, bytes, SERVER_START);
    }

    // ── Validation ────────────────────────────────────────────
    // All validation is server-side. No JavaScript anywhere.
    // Returns an error string, or null if everything is valid.
    private String validate(String date, String time,
                             String person, String notes) {

        // 1. Required fields
        if (date   == null || date.isEmpty())
            return "Date is required.";
        if (time   == null || time.isEmpty())
            return "Time is required.";
        if (person == null || person.isEmpty())
            return "Person / Event is required.";

        // 2. Date format: exactly YYYY-MM-DD
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

        // 3. Sensible value ranges
        if (year < 2000 || year > 2100)
            return "Year must be between 2000 and 2100. "
                 + "You entered: " + year;
        if (month < 1 || month > 12)
            return "Month must be between 01 and 12. "
                 + "You entered: " + String.format("%02d", month);
        if (day < 1 || day > 31)
            return "Day must be between 01 and 31. "
                 + "You entered: " + String.format("%02d", day);

        // 4. Days per month — including leap year for February
        //    Leap year: divisible by 4, but not by 100, unless also by 400
        int[] maxDays = {0,31,28,31,30,31,30,31,31,30,31,30,31};
        boolean leapYear = (year % 4 == 0 && year % 100 != 0)
                        || (year % 400 == 0);
        if (leapYear) maxDays[2] = 29;

        if (day > maxDays[month])
            return "Day " + day + " does not exist in month "
                 + month + " of " + year
                 + ". That month has " + maxDays[month] + " days.";

        // 5. Past date check
        //    Convert both dates to YYYYMMDD integers for comparison.
        //    e.g. 2026-03-23 → 20260323
        //    Strictly less than means today is allowed; yesterday is not.
        Calendar today = Calendar.getInstance();
        int todayYear  = today.get(Calendar.YEAR);
        int todayMonth = today.get(Calendar.MONTH) + 1; // Calendar months are 0-based
        int todayDay   = today.get(Calendar.DAY_OF_MONTH);

        int apptInt  = year      * 10000 + month      * 100 + day;
        int todayInt = todayYear * 10000 + todayMonth * 100 + todayDay;

        if (apptInt < todayInt)
            return "The date " + date
                 + " is in the past. Please choose today or a future date.";

        // 6. Time format: exactly HH:MM
        if (!time.matches("\\d{2}:\\d{2}"))
            return "Time must be in HH:MM format "
                 + "(e.g. 14:30). You entered: " + time;

        int hour, minute;
        try {
            hour   = Integer.parseInt(time.substring(0, 2));
            minute = Integer.parseInt(time.substring(3, 5));
        } catch (NumberFormatException e) {
            return "Time contains non-numeric characters. "
                 + "Use HH:MM format.";
        }

        // 7. Time value ranges
        if (hour < 0 || hour > 23)
            return "Hour must be between 00 and 23. "
                 + "You entered: " + String.format("%02d", hour);
        if (minute < 0 || minute > 59)
            return "Minute must be between 00 and 59. "
                 + "You entered: " + String.format("%02d", minute);

        // 8. Length limits
        if (person.length() > 100)
            return "Person / Event must be 100 characters or fewer "
                 + "(you entered " + person.length() + ").";
        if (notes != null && notes.length() > 500)
            return "Notes must be 500 characters or fewer "
                 + "(you entered " + notes.length() + ").";

        return null; // all checks passed
    }

    // ── Photo validation ──────────────────────────────────────
    // Returns an error string, or null if the photo is acceptable.
    private String validatePhoto(byte[] photoBytes, String filename) {
        if (photoBytes == null || photoBytes.length == 0)
            return null; // optional — no photo is fine

        // File size limit: 5 MB
        int maxBytes = 5 * 1024 * 1024;
        if (photoBytes.length > maxBytes)
            return "Photo is too large ("
                 + (photoBytes.length / 1024) + " KB). "
                 + "Maximum allowed size is 5 MB.";

        // Extension check
        if (filename == null || filename.isEmpty())
            return "Could not determine the file type. "
                 + "Please upload a JPG, PNG, GIF or WEBP image.";

        String lower = filename.toLowerCase();
        if (!lower.endsWith(".jpg")  && !lower.endsWith(".jpeg")
         && !lower.endsWith(".png")  && !lower.endsWith(".gif")
         && !lower.endsWith(".webp"))
            return "Only JPG, PNG, GIF or WEBP files are allowed. "
                 + "You uploaded: " + filename;

        // Magic bytes check — verify the actual file content,
        // not just the filename. A renamed .exe still has wrong bytes.
        if (photoBytes.length < 4)
            return "The uploaded file is too small to be a valid image.";

        boolean validMagic = false;

        // JPEG: FF D8 FF
        if ((photoBytes[0] & 0xFF) == 0xFF
         && (photoBytes[1] & 0xFF) == 0xD8
         && (photoBytes[2] & 0xFF) == 0xFF)
            validMagic = true;

        // PNG: 89 50 4E 47
        if ((photoBytes[0] & 0xFF) == 0x89
         && (photoBytes[1] & 0xFF) == 0x50
         && (photoBytes[2] & 0xFF) == 0x4E
         && (photoBytes[3] & 0xFF) == 0x47)
            validMagic = true;

        // GIF: 47 49 46 ("GIF")
        if ((photoBytes[0] & 0xFF) == 0x47
         && (photoBytes[1] & 0xFF) == 0x49
         && (photoBytes[2] & 0xFF) == 0x46)
            validMagic = true;

        // WEBP: 52 49 46 46 ("RIFF")
        if ((photoBytes[0] & 0xFF) == 0x52
         && (photoBytes[1] & 0xFF) == 0x49
         && (photoBytes[2] & 0xFF) == 0x46
         && (photoBytes[3] & 0xFF) == 0x46)
            validMagic = true;

        if (!validMagic)
            return "The uploaded file does not appear to be a valid image. "
                 + "Only JPG, PNG, GIF or WEBP files are accepted.";

        return null; // photo is valid
    }

    // ── HttpResponse ──────────────────────────────────────────
    private static class HttpResponse {
        final int    status;
        final String contentType;
        final byte[] body;
        final long   lastModified;
        final String location; // for 302 redirect

        HttpResponse(int status, String contentType,
                     byte[] body, long lastModified) {
            this(status, contentType, body, lastModified, null);
        }

        HttpResponse(int status, String contentType,
                     byte[] body, long lastModified, String location) {
            this.status       = status;
            this.contentType  = contentType;
            this.body         = body;
            this.lastModified = lastModified;
            this.location     = location;
        }
    }

    // ── Response builders ─────────────────────────────────────
    private HttpResponse html(int status, String htmlBody) {
        try {
            return new HttpResponse(status,
                "text/html; charset=UTF-8",
                htmlBody.getBytes("UTF-8"), SERVER_START);
        } catch (UnsupportedEncodingException e) {
            return new HttpResponse(500,
                "text/html; charset=UTF-8", new byte[0], SERVER_START);
        }
    }

    private HttpResponse empty(int status) {
        return new HttpResponse(status, "text/plain",
                                new byte[0], SERVER_START);
    }

    // ── Redirect with countdown page ─────────────────────────
    // Returns a 200 HTML page that uses <meta http-equiv="refresh">
    // to navigate to `location` after 3 seconds.
    // A CSS ring animation counts down visually — no JavaScript used.
    // This replaces the bare 302 response so the user sees feedback
    // before the page changes, and prevents duplicate POST on Refresh
    // because the final page is a GET to location.
    private HttpResponse redirect(String location, String message) {
        String html = HtmlBuilder.redirectPage(location, 3, message);
        try {
            return new HttpResponse(200,
                "text/html; charset=UTF-8",
                html.getBytes("UTF-8"), SERVER_START);
        } catch (UnsupportedEncodingException e) {
            return new HttpResponse(200,
                "text/html; charset=UTF-8",
                new byte[0], SERVER_START);
        }
    }

    // ── Send full response (headers + body) ───────────────────
    private void sendFull(OutputStream out, HttpResponse r)
            throws IOException {
        sendHeaders(out, r);
        if (r.body != null && r.body.length > 0)
            out.write(r.body);
        out.flush();
    }

    // ── Send headers only (HEAD requests per RFC 2616 §9.4) ──
    private void sendHeaders(OutputStream out, HttpResponse r)
            throws IOException {
        // RFC 2616 §6.1.1 Reason phrases
        String reason;
        switch (r.status) {
            case 200: reason = "OK";                    break;
            case 302: reason = "Found";                 break;
            case 400: reason = "Bad Request";           break;
            case 404: reason = "Not Found";             break;
            case 500: reason = "Internal Server Error"; break;
            default:  reason = "OK";
        }

        String now, lastMod;
        synchronized (HTTP_DATE) {
            now     = HTTP_DATE.format(new Date());
            lastMod = HTTP_DATE.format(new Date(r.lastModified));
        }

        StringBuilder hdr = new StringBuilder();
        hdr.append("HTTP/1.1 ").append(r.status).append(" ")
           .append(reason).append("\r\n");
        hdr.append("Date: ").append(now).append("\r\n");          // §14.18
        hdr.append("Server: COS332-AppointmentServer/1.0\r\n");   // §14.38
        hdr.append("Last-Modified: ").append(lastMod).append("\r\n"); // §14.29
        hdr.append("Content-Type: ").append(r.contentType)
           .append("\r\n");                                        // §14.17
        hdr.append("Content-Length: ")
           .append(r.body == null ? 0 : r.body.length)
           .append("\r\n");                                        // §14.13

        // Location header is sent on 302 redirects (§14.30)
        if (r.location != null)
            hdr.append("Location: ").append(r.location).append("\r\n");

        hdr.append("Connection: close\r\n");                       // §14.10
        hdr.append("\r\n");

        out.write(hdr.toString().getBytes("UTF-8"));
        // body is written by sendFull() only; sendHeaders() stops here
    }

    // ── Read one text line from the raw InputStream ───────────
    // Handles both \r\n and bare \n line endings
    private String readLine(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\n') break;
            if (b != '\r') sb.append((char) b);
        }
        return (b == -1 && sb.length() == 0) ? null : sb.toString();
    }

    // ── Read exactly len bytes from the InputStream ───────────
    // Used to read the POST body whose size is known from Content-Length
    private byte[] readBytes(InputStream in, int len)
            throws IOException {
        byte[] buf = new byte[len];
        int total = 0;
        while (total < len) {
            int n = in.read(buf, total, len - total);
            if (n == -1) {
                System.err.println("Stream ended after " + total
                                 + " of " + len + " bytes");
                break;
            }
            total += n;
        }
        return buf;
    }

    // ── Parse a query string into a Map ──────────────────────
    // e.g. "sort=asc&q=smith" → {sort→asc, q→smith}
    private Map<String, String> parseQuery(String query) {
        Map<String, String> map = new LinkedHashMap<>();
        if (query == null || query.isEmpty()) return map;
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0)
                map.put(urlDecode(pair.substring(0, eq)),
                        urlDecode(pair.substring(eq + 1)));
        }
        return map;
    }

    // ── Manual URL decode ─────────────────────────────────────
    // Converts %XX hex sequences and '+' (space) back to characters.
    // We do NOT use java.net.URLDecoder — that would be a library call
    // hiding the network logic.
    private String urlDecode(String s) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '+') {
                sb.append(' ');
                i++;
            } else if (c == '%' && i + 2 < s.length()) {
                try {
                    int hex = Integer.parseInt(
                        s.substring(i + 1, i + 3), 16);
                    sb.append((char) hex);
                    i += 3;
                } catch (NumberFormatException e) {
                    sb.append(c);
                    i++;
                }
            } else {
                sb.append(c);
                i++;
            }
        }
        return sb.toString();
    }

    // ── Extract the boundary from a Content-Type header ──────
    // e.g. "multipart/form-data; boundary=----WebKitFormBoundaryXYZ"
    //       → "----WebKitFormBoundaryXYZ"
    private String extractBoundary(String contentType) {
        if (contentType == null) return null;
        for (String part : contentType.split(";")) {
            part = part.trim();
            if (part.startsWith("boundary="))
                return part.substring("boundary=".length()).trim();
        }
        return null;
    }

    // ── Get the lowercase extension from a filename ───────────
    // e.g. "Photo.JPG" → "jpg"
    private String fileExtension(String filename) {
        if (filename == null) return "jpg";
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) return "jpg";
        return filename.substring(dot + 1).toLowerCase();
    }

    // ── Get text value from a multipart field ─────────────────
    private String fieldStr(Map<String, MultipartParser.Field> fields,
                             String name) {
        MultipartParser.Field f = fields.get(name);
        return f == null ? "" : f.asString();
    }
}
