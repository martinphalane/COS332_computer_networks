// AppointmentStore.java
// COS332 Practical Assignment 4
// Manages appointments + file persistence + photo storage
// Photos are saved as files in a "photos/" subfolder

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class AppointmentStore {
    private List<Appointment> list = new ArrayList<>();
    private int nextId = 1;
    private final String DATA_FILE  = "appointments.dat";
    private final String PHOTO_DIR  = "photos";

    // Date parser for reminder logic
    private static final SimpleDateFormat DATE_FMT =
        new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH);

    public AppointmentStore() {
        // Make sure the photos folder exists
        new File(PHOTO_DIR).mkdirs();
        load();
    }

    // ── Add with optional photo ───────────────────────────────
    // photoBytes = raw image bytes, or null/empty if no photo
    // photoExt   = "jpg", "png", etc  (from the uploaded filename)
    public void add(String date, String time, String person,
                    String notes, byte[] photoBytes, String photoExt) {
        String id       = String.valueOf(nextId++);
        String photoName = "";

        // Save photo to disk if one was provided
        if (photoBytes != null && photoBytes.length > 0) {
            photoName = "photo_" + id + "." + photoExt;
            savePhoto(photoName, photoBytes);
        }

        list.add(new Appointment(id, date, time,
                                  person, notes, photoName));
        save();
    }

    // Convenience — add without photo
    public void add(String date, String time,
                    String person, String notes) {
        add(date, time, person, notes, null, "");
    }

    // ── Delete appointment + its photo ────────────────────────
    public boolean delete(String id) {
        Appointment found = findById(id);
        if (found != null && found.hasPhoto()) {
            // Also delete the photo file from disk
            new File(PHOTO_DIR + File.separator + found.photo).delete();
        }
        boolean ok = list.removeIf(a -> a.id.equals(id));
        if (ok) save();
        return ok;
    }

    // ── Update appointment (keep existing photo if none uploaded) ─
    public boolean update(String id, String date, String time,
                          String person, String notes,
                          byte[] photoBytes, String photoExt) {
        for (Appointment a : list) {
            if (a.id.equals(id)) {
                a.date   = date;
                a.time   = time;
                a.person = person;
                a.notes  = notes;
                // Only replace photo if a new one was uploaded
                if (photoBytes != null && photoBytes.length > 0) {
                    // Delete old photo if there was one
                    if (a.hasPhoto()) {
                        new File(PHOTO_DIR + File.separator + a.photo)
                            .delete();
                    }
                    String photoName = "photo_" + id + "." + photoExt;
                    savePhoto(photoName, photoBytes);
                    a.photo = photoName;
                }
                save();
                return true;
            }
        }
        return false;
    }

    // Convenience — update without changing photo
    public boolean update(String id, String date, String time,
                          String person, String notes) {
        return update(id, date, time, person, notes, null, "");
    }

    // ── Read a photo file as bytes ────────────────────────────
    // Returns null if the file does not exist
    public byte[] getPhotoBytes(String photoName) {
        File f = new File(PHOTO_DIR + File.separator + photoName);
        if (!f.exists()) return null;
        try (FileInputStream fis = new FileInputStream(f)) {
            byte[] bytes = new byte[(int) f.length()];
            fis.read(bytes);
            return bytes;
        } catch (IOException e) {
            return null;
        }
    }

    // ── Get content type from filename extension ──────────────
    public String getPhotoContentType(String photoName) {
        if (photoName == null) return "image/jpeg";
        String lower = photoName.toLowerCase();
        if (lower.endsWith(".png"))  return "image/png";
        if (lower.endsWith(".gif"))  return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        return "image/jpeg"; // default
    }

    // ── Reminder logic ────────────────────────────────────────
    public long daysUntil(Appointment a) {
        try {
            Date apptDate = DATE_FMT.parse(a.date);
            Calendar today = Calendar.getInstance();
            today.set(Calendar.HOUR_OF_DAY, 0);
            today.set(Calendar.MINUTE, 0);
            today.set(Calendar.SECOND, 0);
            today.set(Calendar.MILLISECOND, 0);
            long diffMs = apptDate.getTime() - today.getTimeInMillis();
            return diffMs / (1000L * 60 * 60 * 24);
        } catch (Exception e) {
            return 999;
        }
    }

    public List<Appointment> getUpcoming() {
        List<Appointment> upcoming = new ArrayList<>();
        for (Appointment a : list) {
            long days = daysUntil(a);
            if (days >= 0 && days <= 30) upcoming.add(a);
        }
        upcoming.sort((a, b) -> a.date.compareTo(b.date));
        return upcoming;
    }

    // ── Standard getters ─────────────────────────────────────
    public List<Appointment> getAll(String sort) {
        List<Appointment> copy = new ArrayList<>(list);
        if ("asc".equals(sort))
            copy.sort((a, b) -> a.date.compareTo(b.date));
        else if ("desc".equals(sort))
            copy.sort((a, b) -> b.date.compareTo(a.date));
        return copy;
    }

    public List<Appointment> getAll() { return getAll(""); }

    public List<Appointment> search(String kw) {
        List<Appointment> res = new ArrayList<>();
        String k = kw.toLowerCase();
        for (Appointment a : list) {
            if (a.date.contains(k) || a.time.contains(k)
                || a.person.toLowerCase().contains(k)
                || a.notes.toLowerCase().contains(k))
                res.add(a);
        }
        return res;
    }

    public Appointment findById(String id) {
        for (Appointment a : list)
            if (a.id.equals(id)) return a;
        return null;
    }

    // ── Private helpers ───────────────────────────────────────
    private void savePhoto(String name, byte[] bytes) {
        File f = new File(PHOTO_DIR + File.separator + name);
        try (FileOutputStream fos = new FileOutputStream(f)) {
            fos.write(bytes);
        } catch (IOException e) {
            System.err.println("Photo save failed: " + e.getMessage());
        }
    }

    private void save() {
        try (PrintWriter pw = new PrintWriter(new FileWriter(DATA_FILE))) {
            pw.println(nextId);
            for (Appointment a : list)
                pw.println(a.toFileLine());
        } catch (IOException e) {
            System.err.println("Save failed: " + e.getMessage());
        }
    }

    private void load() {
        File f = new File(DATA_FILE);
        if (!f.exists()) return;
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            nextId = Integer.parseInt(br.readLine().trim());
            String line;
            while ((line = br.readLine()) != null) {
                Appointment a = Appointment.fromFileLine(line);
                if (a != null) list.add(a);
            }
        } catch (IOException e) {
            System.err.println("Load failed: " + e.getMessage());
        }
    }
}
