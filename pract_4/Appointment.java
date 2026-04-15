
// COS332 Practical Assignment 4
// Student: u26535272 Martin Phalane
// Appointment.java
// COS332 Practical Assignment 4
// One appointment record — stores date, time, person, notes, optional photo.
//
// File format uses pipe | as separator.
// Pipes inside field values are escaped as \P so the format is never broken.



public class Appointment {
    public String id;
    public String date;
    public String time;
    public String person;
    public String notes;
    // Filename of the stored photo e.g. "photo_1.jpg"
    // Empty string means no photo was uploaded.
    public String photo;

    public Appointment(String id, String date, String time,
                       String person, String notes, String photo) {
        this.id     = id;
        this.date   = date;
        this.time   = time;
        this.person = person;
        this.notes  = notes;
        this.photo  = photo == null ? "" : photo;
    }

    // ── Serialise to one file line ────────────────────────────
    // Format: id|date|time|person|notes|photo
    // Pipes inside values are escaped as \P so the format stays safe.
    public String toFileLine() {
        return fileEsc(id)     + "|" + fileEsc(date)   + "|"
             + fileEsc(time)   + "|" + fileEsc(person) + "|"
             + fileEsc(notes)  + "|" + fileEsc(photo);
    }

    // ── Deserialise from one file line ────────────────────────
    public static Appointment fromFileLine(String line) {
        if (line == null || line.trim().isEmpty()) return null;
        String[] p = line.split("\\|", 6);
        if (p.length < 5) return null;
        String photo = p.length >= 6 ? fileUnesc(p[5]) : "";
        return new Appointment(
            fileUnesc(p[0]), fileUnesc(p[1]), fileUnesc(p[2]),
            fileUnesc(p[3]), fileUnesc(p[4]), photo);
    }

    // Escape backslashes first, then pipes
    private static String fileEsc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("|", "\\P");
    }

    // Reverse: unescape pipes first, then backslashes
    private static String fileUnesc(String s) {
        if (s == null) return "";
        return s.replace("\\P", "|").replace("\\\\", "\\");
    }

    // True if a photo has been stored for this appointment
    public boolean hasPhoto() {
        return photo != null && !photo.isEmpty();
    }
}
