import java.io.*;
import java.util.*;
import java.text.*;

/**
 * Thread-safe, file-backed appointment store.
 * All reads and writes are synchronised so multiple clients
 * can use the server at the same time without corrupting data.
 */
public class AppointmentDatabase {

    private static final String DATA_FILE = "appointments.dat";
    private final List<Appointment> appointments = new ArrayList<>();

    public AppointmentDatabase() {
        load();
    }

    /** Load appointments from the data file into memory. */
    private synchronized void load() {
        appointments.clear();
        File f = new File(DATA_FILE);
        if (!f.exists()) return;
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    Appointment a = Appointment.fromFileLine(line);
                    if (a != null) appointments.add(a);
                }
            }
        } catch (IOException e) {
            // silently continue with empty list
        }
    }

    /** Persist the current in-memory list to disk. */
    private synchronized void save() {
        try (PrintWriter pw = new PrintWriter(new FileWriter(DATA_FILE))) {
            for (Appointment a : appointments) {
                pw.println(a.toFileLine());
            }
        } catch (IOException e) {
            // silently ignore
        }
    }

    /** Add a new appointment and persist. */
    public synchronized void add(Appointment a) {
        appointments.add(a);
        save();
    }

    /** Return all appointments (a snapshot copy). */
    public synchronized List<Appointment> getAll() {
        return new ArrayList<>(appointments);
    }

    /**
     * Search appointments whose date, person or notes contain the query
     * (case-insensitive).
     */
    public synchronized List<Appointment> search(String query) {
        String q = query.toLowerCase();
        List<Appointment> results = new ArrayList<>();
        for (Appointment a : appointments) {
            if (a.getDate().contains(q)
                    || a.getTime().contains(q)
                    || a.getPerson().toLowerCase().contains(q)
                    || a.getNotes().toLowerCase().contains(q)) {
                results.add(a);
            }
        }
        return results;
    }

    /**
     * Delete the appointment at the given 1-based index.
     * Returns true on success, false if index is out of range.
     */
    public synchronized boolean delete(int oneBasedIndex) {
        if (oneBasedIndex < 1 || oneBasedIndex > appointments.size()) return false;
        appointments.remove(oneBasedIndex - 1);
        save();
        return true;
    }

    public synchronized int size() {
        return appointments.size();
    }

    /**
     * Returns appointments occurring within the next 'days' days from today.
     * Parses the stored date string in dd/mm/yyyy format.
     * Appointments with unparseable dates are silently skipped.
     */
    public synchronized List<Appointment> getUpcoming(int days) {
        List<Appointment> upcoming = new ArrayList<>();

        // Get today at midnight and the cutoff date
        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);

        Calendar cutoff = Calendar.getInstance();
        cutoff.setTime(today.getTime());
        cutoff.add(Calendar.DAY_OF_MONTH, days);

        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
        sdf.setLenient(false);

        for (Appointment a : appointments) {
            try {
                Date apptDate = sdf.parse(a.getDate());
                Calendar apptCal = Calendar.getInstance();
                apptCal.setTime(apptDate);
                apptCal.set(Calendar.HOUR_OF_DAY, 0);
                apptCal.set(Calendar.MINUTE, 0);
                apptCal.set(Calendar.SECOND, 0);
                apptCal.set(Calendar.MILLISECOND, 0);

                // Include if on or after today AND on or before cutoff
                if (!apptCal.before(today) && !apptCal.after(cutoff)) {
                    upcoming.add(a);
                }
            } catch (ParseException e) {
                // Skip appointments with unrecognised date formats
            }
        }

        // Sort by date then time
        upcoming.sort((x, y) -> {
            int dateCmp = x.getDate().compareTo(y.getDate());
            if (dateCmp != 0) return dateCmp;
            return x.getTime().compareTo(y.getTime());
        });

        return upcoming;
    }

    /**
     * Replace the appointment at the given 1-based index with a new one.
     * Returns true on success, false if index is out of range.
     */
    public synchronized boolean edit(int oneBasedIndex, Appointment updated) {
        if (oneBasedIndex < 1 || oneBasedIndex > appointments.size()) return false;
        appointments.set(oneBasedIndex - 1, updated);
        save();
        return true;
    }

    /**
     * Return a single appointment by 1-based index, or null if out of range.
     */
    public synchronized Appointment get(int oneBasedIndex) {
        if (oneBasedIndex < 1 || oneBasedIndex > appointments.size()) return null;
        return appointments.get(oneBasedIndex - 1);
    }


    /**
     * Returns all appointments sorted chronologically by date then time.
     * Appointments with unparseable dates are sorted to the end.
     */
    public synchronized List<Appointment> getAllSorted() {
        List<Appointment> sorted = new ArrayList<>(appointments);
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
        sdf.setLenient(false);

        sorted.sort((x, y) -> {
            try {
                Date dx = sdf.parse(x.getDate());
                Date dy = sdf.parse(y.getDate());
                int dateCmp = dx.compareTo(dy);
                if (dateCmp != 0) return dateCmp;
                // Same date — sort by time string (HH:MM compares lexicographically)
                return x.getTime().compareTo(y.getTime());
            } catch (ParseException e) {
                // Unparseable dates sort to the end
                return 1;
            }
        });

        return sorted;
    }

}
