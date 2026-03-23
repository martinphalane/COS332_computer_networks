// Appointment.java
// COS332 Practical Assignment 4
// One appointment record — now includes an optional photo filename

public class Appointment {
    public String id;
    public String date;
    public String time;
    public String person;
    public String notes;
    // Filename of the stored photo, e.g. "photo_1.jpg"
    // Empty string means no photo was uploaded
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

    // Save to file — pipe separated, photo field last
    // "1|2026-03-25|14:00|Dr Smith|Checkup|photo_1.jpg"
    public String toFileLine() {
        return id + "|" + date + "|" + time + "|"
             + person + "|" + notes + "|" + photo;
    }

    // Load from that same line
    public static Appointment fromFileLine(String line) {
        // Split into at most 6 parts so notes can contain |
        String[] p = line.split("\\|", 6);
        if (p.length < 5) return null;
        String photo = p.length >= 6 ? p[5] : "";
        return new Appointment(p[0], p[1], p[2], p[3], p[4], photo);
    }

    // True if a photo has been stored for this appointment
    public boolean hasPhoto() {
        return photo != null && !photo.isEmpty();
    }
}
