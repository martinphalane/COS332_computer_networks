/**
 * Represents a single appointment entry.
 */
public class Appointment {
    private String date;    // dd/mm/yyyy
    private String time;    // HH:MM
    private String person;  // The customer name
    private String notes;   // Additional notes / description

    public Appointment(String date, String time, String person, String notes) {
        this.date = date;
        this.time = time;
        this.person = person;
        this.notes = notes;
    }

    public String getDate()   { return date; }
    public String getTime()   { return time; }
    public String getPerson() { return person; }
    public String getNotes()  { return notes; }

    /**
     * Serialises the appointment to a single CSV line for file storage.
     * Fields are separated by '|' to avoid conflicts with commas in notes.
     */
    public String toFileLine() {
        return date + "|" + time + "|" + person + "|" + notes;
    }

    /**
     * Parses a line from the data file back into an Appointment.
     */
    public static Appointment fromFileLine(String line) {
        String[] parts = line.split("\\|", 4);
        if (parts.length < 4) return null;
        return new Appointment(parts[0], parts[1], parts[2], parts[3]);
    }

    @Override
    public String toString() {
        return String.format("%-12s %-6s %-20s %s", date, time, person, notes);
    }
}
