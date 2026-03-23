// HtmlBuilder.java
// COS332 Practical Assignment 4
// Builds every HTML page — NO JavaScript anywhere

import java.util.List;

public class HtmlBuilder {

    private static final String CSS =
        "<style>"
        + "*{margin:0;padding:0;box-sizing:border-box}"
        + "body{font-family:'Segoe UI',Arial,sans-serif;"
        +      "background:#f0f4f8;color:#2d3748;min-height:100vh}"
        + ".header{background:#1a202c;color:white;padding:0 40px;"
        +         "display:flex;align-items:center;"
        +         "justify-content:space-between;height:64px;"
        +         "box-shadow:0 2px 10px rgba(0,0,0,0.3)}"
        + ".header h1{font-size:20px;font-weight:600;color:white}"
        + ".header-icon{font-size:22px;margin-right:10px}"
        + ".nav{display:flex;gap:6px}"
        + ".nav a{color:rgba(255,255,255,0.7);text-decoration:none;"
        +        "padding:8px 16px;border-radius:6px;"
        +        "font-size:14px;font-weight:500}"
        + ".nav a:hover{background:rgba(255,255,255,0.12);color:white}"
        + ".nav a.active{background:#e67e22;color:white}"
        + ".container{max-width:980px;margin:36px auto;padding:0 24px}"
        + ".page-title{font-size:22px;font-weight:700;color:#1a202c;"
        +             "margin-bottom:20px;padding-bottom:10px;"
        +             "border-bottom:3px solid #e67e22;"
        +             "display:inline-block}"
        + ".card{background:white;border-radius:12px;"
        +       "box-shadow:0 1px 6px rgba(0,0,0,0.08);"
        +       "padding:28px 32px;margin-bottom:24px}"
        + ".alert{padding:12px 18px;border-radius:8px;"
        +        "margin-bottom:20px;font-size:14px;font-weight:500}"
        + ".alert-ok{background:#d4edda;color:#155724;"
        +            "border-left:4px solid #28a745}"
        + ".alert-er{background:#f8d7da;color:#721c24;"
        +            "border-left:4px solid #dc3545}"
        + ".alert-wn{background:#fff3cd;color:#856404;"
        +            "border-left:4px solid #ffc107}"
        // Reminder banner
        + ".rembanner{background:white;border-radius:12px;"
        +            "box-shadow:0 1px 6px rgba(0,0,0,0.08);"
        +            "padding:18px 24px;margin-bottom:24px;"
        +            "border-left:5px solid #e67e22}"
        + ".remtitle{font-size:14px;font-weight:700;color:#1a202c;"
        +           "margin-bottom:10px}"
        + ".remrow{display:flex;align-items:center;gap:12px;"
        +         "padding:7px 0;border-bottom:1px solid #edf2f7}"
        + ".remrow:last-child{border-bottom:none}"
        + ".remperson{font-weight:600;font-size:13px;"
        +            "color:#2d3748;flex:1}"
        + ".remdate{font-size:12px;color:#718096;min-width:100px}"
        // Countdown badges
        + ".badge-today{display:inline-block;padding:4px 12px;"
        +              "border-radius:20px;font-size:12px;"
        +              "font-weight:700;background:#fff0f0;"
        +              "color:#c53030;border:1px solid #fed7d7}"
        + ".badge-urgent{display:inline-block;padding:4px 12px;"
        +               "border-radius:20px;font-size:12px;"
        +               "font-weight:700;background:#fffaf0;"
        +               "color:#c05621;border:1px solid #fbd38d}"
        + ".badge-soon{display:inline-block;padding:4px 12px;"
        +             "border-radius:20px;font-size:12px;"
        +             "font-weight:700;background:#ebf8ff;"
        +             "color:#2b6cb0;border:1px solid #bee3f8}"
        + ".badge-past{display:inline-block;padding:4px 10px;"
        +             "border-radius:20px;font-size:12px;"
        +             "background:#f7fafc;color:#a0aec0;"
        +             "border:1px solid #e2e8f0}"
        // Table
        + "table{width:100%;border-collapse:collapse;font-size:13px}"
        + "thead tr{background:#1a202c;color:white}"
        + "thead th{padding:11px 14px;text-align:left;"
        +          "font-size:12px;font-weight:600;letter-spacing:.3px}"
        + "tbody tr{border-bottom:1px solid #edf2f7}"
        + "tbody tr:hover{background:#f7fafc}"
        + "tbody td{padding:10px 14px;color:#4a5568;vertical-align:middle}"
        + "tbody tr:nth-child(even){background:#f9fafb}"
        // Photo thumbnail in table
        + ".thumb{width:44px;height:44px;border-radius:50%;"
        +        "object-fit:cover;border:2px solid #e2e8f0}"
        + ".no-photo{width:44px;height:44px;border-radius:50%;"
        +           "background:#edf2f7;display:inline-flex;"
        +           "align-items:center;justify-content:center;"
        +           "font-size:18px;color:#a0aec0}"
        // Action buttons
        + ".btn-delete{display:inline-block;background:#fff0f0;"
        +             "color:#e53e3e;padding:4px 10px;"
        +             "border-radius:20px;font-size:11px;"
        +             "font-weight:600;text-decoration:none;"
        +             "border:1px solid #fed7d7;margin-left:4px}"
        + ".btn-delete:hover{background:#e53e3e;color:white}"
        + ".btn-edit{display:inline-block;background:#ebf8ff;"
        +           "color:#2b6cb0;padding:4px 10px;"
        +           "border-radius:20px;font-size:11px;"
        +           "font-weight:600;text-decoration:none;"
        +           "border:1px solid #bee3f8}"
        + ".btn-edit:hover{background:#2b6cb0;color:white}"
        // Form
        + ".form-group{margin-bottom:16px}"
        + ".form-group label{display:block;font-size:12px;"
        +                   "font-weight:600;color:#4a5568;"
        +                   "margin-bottom:5px;text-transform:uppercase;"
        +                   "letter-spacing:.5px}"
        + ".form-group input[type=text],"
        + ".form-group input[type=file]{"
        +   "width:100%;padding:10px 14px;"
        +   "border:1.5px solid #e2e8f0;border-radius:8px;"
        +   "font-size:14px;color:#2d3748;outline:none}"
        + ".form-group input[type=text]:focus{border-color:#e67e22}"
        + ".form-group input[type=file]{padding:7px 10px;"
        +                              "background:#f7fafc;"
        +                              "cursor:pointer}"
        + ".form-hint{font-size:11px;color:#a0aec0;margin-top:4px}"
        // Buttons
        + ".btn{display:inline-block;padding:11px 28px;"
        +      "border-radius:8px;font-size:14px;font-weight:600;"
        +      "text-decoration:none;cursor:pointer;border:none}"
        + ".btn-primary{background:#e67e22;color:white}"
        + ".btn-primary:hover{background:#d35400}"
        + ".btn-danger{background:#e53e3e;color:white}"
        + ".btn-secondary{background:#e2e8f0;color:#4a5568}"
        // Search bar
        + ".search-bar{display:flex;gap:10px;margin-bottom:20px}"
        + ".search-bar input{flex:1;padding:11px 16px;"
        +                   "border:1.5px solid #e2e8f0;"
        +                   "border-radius:8px;font-size:14px;outline:none}"
        + ".search-bar input:focus{border-color:#e67e22}"
        // Empty state
        + ".empty{text-align:center;padding:48px 20px;color:#a0aec0}"
        + ".empty-icon{font-size:48px;margin-bottom:12px}"
        // Stats
        + ".stats{display:flex;gap:16px;margin-bottom:24px}"
        + ".stat-card{background:white;border-radius:10px;"
        +            "padding:16px 20px;flex:1;"
        +            "box-shadow:0 1px 4px rgba(0,0,0,0.07);"
        +            "border-top:3px solid #e67e22}"
        + ".stat-num{font-size:28px;font-weight:700;color:#1a202c}"
        + ".stat-lbl{font-size:12px;color:#718096;margin-top:2px;"
        +           "text-transform:uppercase;letter-spacing:.5px}"
        // Sort bar
        + ".sort-bar{display:flex;gap:8px;margin-bottom:16px;"
        +            "align-items:center}"
        + ".sort-bar span{font-size:13px;color:#718096}"
        + ".sort-link{display:inline-block;padding:5px 14px;"
        +            "border-radius:20px;font-size:12px;"
        +            "font-weight:600;text-decoration:none;"
        +            "border:1px solid #e2e8f0;background:white;"
        +            "color:#4a5568}"
        + ".sort-link.on{background:#1a202c;color:white;"
        +               "border-color:#1a202c}"
        // Detail box (confirm delete)
        + ".detail-box{background:#f7fafc;border:1px solid #e2e8f0;"
        +             "border-radius:10px;padding:16px 20px;"
        +             "margin:16px 0 24px}"
        + ".detail-row{display:flex;align-items:center;padding:8px 0;"
        +             "border-bottom:1px solid #edf2f7}"
        + ".detail-row:last-child{border-bottom:none}"
        + ".detail-label{font-weight:600;font-size:13px;"
        +               "color:#718096;width:90px}"
        + ".detail-value{font-size:14px;color:#2d3748}"
        // Current photo preview in edit form
        + ".photo-preview{border-radius:8px;max-width:120px;"
        +                "max-height:120px;object-fit:cover;"
        +                "border:2px solid #e2e8f0;margin-top:6px}"
        + ".footer{text-align:center;padding:24px;color:#a0aec0;"
        +         "font-size:12px;margin-top:20px}"
        + "</style>";

    // ── Page wrapper ──────────────────────────────────────────
    private static String page(String title, String active,
                                String body) {
        return "<!DOCTYPE html><html lang='en'><head>"
             + "<meta charset='UTF-8'>"
             + "<title>" + title + " - Appointments</title>"
             + CSS
             + "</head><body>"
             + "<div class='header'>"
             +   "<div style='display:flex;align-items:center'>"
             +     "<span class='header-icon'>&#128197;</span>"
             +     "<h1>Appointment Manager</h1>"
             +   "</div>"
             +   "<nav class='nav'>"
             +     navLink("/",       "&#127968; Home",   active)
             +     navLink("/add",    "&#10133; Add",     active)
             +     navLink("/search", "&#128269; Search", active)
             +   "</nav>"
             + "</div>"
             + "<div class='container'>"
             + body
             + "</div>"
             + "<div class='footer'>COS332 Practical Assignment 4 by u26535272</div>"
             + "</body></html>";
    }

    private static String navLink(String href, String label,
                                   String active) {
        String cls = href.equals(active) ? " class='active'" : "";
        return "<a href='" + href + "'" + cls + ">" + label + "</a>";
    }

    // ── Home page ─────────────────────────────────────────────
    public static String home(List<Appointment> all, String msg,
                               String sort, AppointmentStore store) {
        StringBuilder sb = new StringBuilder();
        sb.append("<h2 class='page-title'>All Appointments</h2>");

        if (!msg.isEmpty()) {
            String cls = msg.contains("deleted") ? "alert-wn"
                       : msg.contains("not found") ? "alert-er"
                       : "alert-ok";
            sb.append("<div class='alert ").append(cls).append("'>")
              .append(esc(msg)).append("</div>");
        }

        // Reminder banner
        List<Appointment> upcoming = store.getUpcoming();
        if (!upcoming.isEmpty()) {
            sb.append("<div class='rembanner'>")
              .append("<div class='remtitle'>&#9888;&#65039; ")
              .append(upcoming.size())
              .append(upcoming.size() == 1
                  ? " appointment" : " appointments")
              .append(" within the next 30 days</div>");
            for (Appointment a : upcoming) {
                sb.append("<div class='remrow'>")
                  .append("<span class='remperson'>")
                  .append(esc(a.person)).append("</span>")
                  .append("<span class='remdate'>")
                  .append(esc(a.date)).append(" at ")
                  .append(esc(a.time)).append("</span>")
                  .append(countdownBadge(store.daysUntil(a)))
                  .append("</div>");
            }
            sb.append("</div>");
        }

        // Stats
        sb.append("<div class='stats'>")
          .append("<div class='stat-card'>")
          .append("<div class='stat-num'>").append(all.size())
          .append("</div><div class='stat-lbl'>Total</div></div>")
          .append("<div class='stat-card'>")
          .append("<div class='stat-num'")
          .append(upcoming.isEmpty() ? "" : " style='color:#e67e22'")
          .append(">").append(upcoming.size()).append("</div>")
          .append("<div class='stat-lbl'>Within 30 days</div>")
          .append("</div></div>");

        // Sort bar
        sb.append("<div class='sort-bar'>")
          .append("<span>Sort:</span>")
          .append(sortLink("",     "Default",      sort))
          .append(sortLink("asc",  "Oldest first", sort))
          .append(sortLink("desc", "Newest first", sort))
          .append("</div>");

        // Table
        if (all.isEmpty()) {
            sb.append("<div class='card'><div class='empty'>")
              .append("<div class='empty-icon'>&#128197;</div>")
              .append("<p>No appointments yet. ")
              .append("<a href='/add' style='color:#e67e22'>")
              .append("Add one now</a></p></div></div>");
        } else {
            sb.append("<div class='card'><table><thead><tr>")
              .append("<th>Photo</th><th>Date</th><th>Time</th>")
              .append("<th>Person</th><th>Notes</th>")
              .append("<th>Countdown</th><th>Actions</th>")
              .append("</tr></thead><tbody>");

            for (Appointment a : all) {
                long days = store.daysUntil(a);
                sb.append("<tr>")
                  // Photo column — thumbnail if photo exists
                  .append("<td>")
                  .append(photoThumb(a))
                  .append("</td>")
                  .append("<td><strong>").append(esc(a.date))
                  .append("</strong></td>")
                  .append("<td>").append(esc(a.time)).append("</td>")
                  .append("<td>").append(esc(a.person)).append("</td>")
                  .append("<td style='color:#718096'>")
                  .append(esc(a.notes)).append("</td>")
                  .append("<td>").append(countdownBadge(days))
                  .append("</td>")
                  .append("<td>")
                  .append("<a class='btn-edit' href='/edit?id=")
                  .append(a.id).append("'>Edit</a>")
                  .append("<a class='btn-delete' href='/confirm?id=")
                  .append(a.id).append("'>Delete</a>")
                  .append("</td></tr>");
            }
            sb.append("</tbody></table></div>");
        }
        return page("Home", "/", sb.toString());
    }

    // Convenience overloads
    public static String home(List<Appointment> all, String msg,
                               AppointmentStore store) {
        return home(all, msg, "", store);
    }
    public static String home(List<Appointment> all, String msg) {
        return home(all, msg, "", null);
    }

    // ── Photo thumbnail HTML ──────────────────────────────────
    // If the appointment has a photo, show it as a circular thumbnail
    // The browser fetches it from /photo?name=photo_1.jpg
    private static String photoThumb(Appointment a) {
        if (a.hasPhoto()) {
            return "<img class='thumb' "
                 + "src='/photo?name=" + esc(a.photo) + "' "
                 + "alt='" + esc(a.person) + "'>";
        }
        // No photo — show a placeholder person icon
        return "<div class='no-photo'>&#128100;</div>";
    }

    // ── Countdown badge ───────────────────────────────────────
    private static String countdownBadge(long days) {
        if (days < 0)
            return "<span class='badge-past'>"
                 + Math.abs(days) + "d ago</span>";
        if (days == 0)
            return "<span class='badge-today'>&#128680; TODAY</span>";
        if (days <= 7)
            return "<span class='badge-urgent'>&#9200; "
                 + days + "d left</span>";
        if (days <= 30)
            return "<span class='badge-soon'>&#128336; "
                 + days + "d left</span>";
        return "<span style='font-size:12px;color:#a0aec0'>"
             + days + " days</span>";
    }

    private static String sortLink(String val, String label,
                                    String current) {
        String cls  = val.equals(current) ? " on" : "";
        String href = val.isEmpty() ? "/" : "/?sort=" + val;
        return "<a class='sort-link" + cls + "' href='"
             + href + "'>" + label + "</a> ";
    }

    // ── Add form ──────────────────────────────────────────────
    // IMPORTANT: method="post" and enctype="multipart/form-data"
    // are required to upload a file via an HTML form.
    // Without enctype the browser sends the filename but NOT the bytes.
    public static String addForm(String err) {
        StringBuilder sb = new StringBuilder();
        sb.append("<h2 class='page-title'>Add Appointment</h2>");
        if (!err.isEmpty())
            sb.append("<div class='alert alert-er'>")
              .append(esc(err)).append("</div>");

        sb.append("<div class='card' style='max-width:520px'>")
          // POST + multipart/form-data is required for file uploads
          .append("<form method='post' action='/do-add' "
                +      "enctype='multipart/form-data'>")
          .append(tf("Date (YYYY-MM-DD)", "date",   "2026-03-25"))
          .append(tf("Time (HH:MM)",      "time",   "14:00"))
          .append(tf("Person / Event",    "person", "e.g. Dr Smith"))
          .append(tf("Notes",             "notes",  "e.g. Annual checkup"))
          // File upload field for the photo
          .append("<div class='form-group'>")
          .append("<label>Photo (optional)</label>")
          .append("<input type='file' name='photo' "
                +        "accept='image/jpeg,image/png,image/gif'>")
          .append("<div class='form-hint'>JPG, PNG or GIF. "
                + "Photo will be stored on the server and "
                + "served back to the browser over HTTP.</div>")
          .append("</div>")
          .append("<input class='btn btn-primary' "
                +        "type='submit' value='Save Appointment'>")
          .append("</form></div>");
        return page("Add", "/add", sb.toString());
    }

    // ── Edit form ─────────────────────────────────────────────
    public static String editForm(Appointment a, String err) {
        StringBuilder sb = new StringBuilder();
        sb.append("<h2 class='page-title'>Edit Appointment</h2>");
        if (!err.isEmpty())
            sb.append("<div class='alert alert-er'>")
              .append(esc(err)).append("</div>");

        sb.append("<div class='card' style='max-width:520px'>")
          .append("<form method='post' action='/do-edit' "
                +      "enctype='multipart/form-data'>")
          .append("<input type='hidden' name='id' value='")
          .append(esc(a.id)).append("'>")
          .append(tff("Date (YYYY-MM-DD)", "date",   a.date))
          .append(tff("Time (HH:MM)",      "time",   a.time))
          .append(tff("Person / Event",    "person", a.person))
          .append(tff("Notes",             "notes",  a.notes))
          // Show current photo if one exists
          .append("<div class='form-group'>")
          .append("<label>Photo</label>");

        if (a.hasPhoto()) {
            sb.append("<br><img class='photo-preview' "
                    +         "src='/photo?name=")
              .append(esc(a.photo))
              .append("' alt='Current photo'><br>")
              .append("<div class='form-hint' "
                    +      "style='margin-bottom:8px'>")
              .append("Current photo shown above. "
                    + "Upload a new one to replace it.</div>");
        }

        sb.append("<input type='file' name='photo' "
                +        "accept='image/jpeg,image/png,image/gif'>")
          .append("</div>")
          .append("<div style='display:flex;gap:10px;margin-top:8px'>")
          .append("<input class='btn btn-primary' "
                +        "type='submit' value='Update Appointment'>")
          .append("<a class='btn btn-secondary' href='/'>Cancel</a>")
          .append("</div></form></div>");
        return page("Edit", "/", sb.toString());
    }

    // Helpers — blank and pre-filled text fields
    private static String tf(String label, String name, String ph) {
        return "<div class='form-group'><label>" + label + "</label>"
             + "<input type='text' name='" + name
             + "' placeholder='" + ph + "'></div>";
    }
    private static String tff(String label, String name, String val) {
        return "<div class='form-group'><label>" + label + "</label>"
             + "<input type='text' name='" + name
             + "' value='" + esc(val) + "'></div>";
    }

    // ── Confirm delete ────────────────────────────────────────
    public static String confirmDelete(Appointment a) {
        StringBuilder sb = new StringBuilder();
        sb.append("<h2 class='page-title'>Confirm Delete</h2>")
          .append("<div class='card' style='max-width:460px'>")
          .append("<div class='alert alert-wn'>")
          .append("Delete this appointment?</div>")
          .append("<div class='detail-box'>");

        // Show photo in confirm page if there is one
        if (a.hasPhoto()) {
            sb.append("<div class='detail-row'>")
              .append("<span class='detail-label'>Photo</span>")
              .append("<img class='photo-preview' src='/photo?name=")
              .append(esc(a.photo)).append("' alt=''>")
              .append("</div>");
        }
        sb.append(drow("Person", a.person))
          .append(drow("Date",   a.date))
          .append(drow("Time",   a.time))
          .append(drow("Notes",  a.notes))
          .append("</div>")
          .append("<div style='display:flex;gap:12px'>")
          .append("<a class='btn btn-danger' href='/delete?id=")
          .append(a.id).append("'>Yes, delete</a>")
          .append("<a class='btn btn-secondary' href='/'>Cancel</a>")
          .append("</div></div>");
        return page("Confirm Delete", "/", sb.toString());
    }

    private static String drow(String label, String value) {
        return "<div class='detail-row'>"
             + "<span class='detail-label'>" + label + "</span>"
             + "<span class='detail-value'>" + esc(value) + "</span>"
             + "</div>";
    }

    // ── Search page ───────────────────────────────────────────
    public static String search(List<Appointment> res,
                                 String kw, boolean searched) {
        StringBuilder sb = new StringBuilder();
        sb.append("<h2 class='page-title'>Search</h2>")
          .append("<div class='card'>")
          .append("<form method='get' action='/search'>")
          .append("<div class='search-bar'>")
          .append("<input type='text' name='q' value='")
          .append(esc(kw))
          .append("' placeholder='Search by name, date, notes...'>")
          .append("<input class='btn btn-primary' "
                +        "type='submit' value='Search'>")
          .append("</div></form>");

        if (searched) {
            if (res == null || res.isEmpty()) {
                sb.append("<div class='empty'>")
                  .append("<div class='empty-icon'>&#128269;</div>")
                  .append("<p>No results for <strong>")
                  .append(esc(kw)).append("</strong></p></div>");
            } else {
                sb.append("<p style='margin-bottom:12px;"
                        +          "color:#718096;font-size:13px'>")
                  .append(res.size()).append(" result(s) for <strong>")
                  .append(esc(kw)).append("</strong></p>")
                  .append("<table><thead><tr>")
                  .append("<th>Photo</th><th>Date</th><th>Time</th>")
                  .append("<th>Person</th><th>Notes</th>")
                  .append("</tr></thead><tbody>");
                for (Appointment a : res) {
                    sb.append("<tr>")
                      .append("<td>").append(photoThumb(a)).append("</td>")
                      .append("<td><strong>").append(esc(a.date))
                      .append("</strong></td>")
                      .append("<td>").append(esc(a.time)).append("</td>")
                      .append("<td>").append(esc(a.person)).append("</td>")
                      .append("<td style='color:#718096'>")
                      .append(esc(a.notes)).append("</td>")
                      .append("</tr>");
                }
                sb.append("</tbody></table>");
            }
        }
        return page("Search", "/search", sb.append("</div>").toString());
    }

    // ── 404 ───────────────────────────────────────────────────
    public static String notFound() {
        return page("Not Found", "/",
            "<div class='card' style='text-align:center;padding:48px'>"
          + "<div style='font-size:56px'>&#128533;</div>"
          + "<h2 style='margin:12px 0 8px'>404 - Not Found</h2>"
          + "<p style='color:#718096;margin-bottom:20px'>"
          +   "This page does not exist.</p>"
          + "<a class='btn btn-primary' href='/'>Back to Home</a>"
          + "</div>");
    }

    // ── HTML escape ───────────────────────────────────────────
    public static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
