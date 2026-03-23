// AppointmentStore.java
// COS332 Practical Assignment 4
// Thread-safe in-memory store with file persistence and photo storage.

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class AppointmentStore {

    private final List<Appointment> list = new ArrayList<>();
    private int nextId = 1;

    private static final String DATA_FILE = "appointments.dat";
    private static final String PHOTO_DIR = "photos";

    // Date parser used for the reminder countdown
    private static final SimpleDateFormat DATE_FMT =
        new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH);

    public AppointmentStore() {
        new File(PHOTO_DIR).mkdirs(); // create photos/ if absent
        load();
    }

    // ── Add ───────────────────────────────────────────────────
    public synchronized void add(String date, String time,
                                  String person, String notes,
                                  byte[] photoBytes, String photoExt) {
        String id        = String.valueOf(nextId++);
        String photoName = "";

        if (photoBytes != null && photoBytes.length > 0) {
            photoName = "photo_" + id + "." + photoExt;
            savePhotoFile(photoName, photoBytes);
        }

        list.add(new Appointment(id, date, time, person, notes, photoName));
        save();
    }

    // Convenience — no photo
    public synchronized void add(String date, String time,
                                  String person, String notes) {
        add(date, time, person, notes, null, "");
    }

    // ── Delete ────────────────────────────────────────────────
    public synchronized boolean delete(String id) {
        Appointment found = findById(id);
        if (found != null && found.hasPhoto()) {
            new File(PHOTO_DIR + File.separator + found.photo).delete();
        }
        boolean ok = list.removeIf(a -> a.id.equals(id));
        if (ok) save();
        return ok;
    }

    // ── Update ────────────────────────────────────────────────
    public synchronized boolean update(String id, String date, String time,
                                        String person, String notes,
                                        byte[] photoBytes, String photoExt) {
        for (Appointment a : list) {
            if (a.id.equals(id)) {
                a.date   = date;
                a.time   = time;
                a.person = person;
                a.notes  = notes;

                if (photoBytes != null && photoBytes.length > 0) {
                    // Remove old photo file
                    if (a.hasPhoto())
                        new File(PHOTO_DIR + File.separator + a.photo).delete();

                    String photoName = "photo_" + id + "." + photoExt;
                    savePhotoFile(photoName, photoBytes);
                    a.photo = photoName;
                }
                save();
                return true;
            }
        }
        return false;
    }

    // ── Photo retrieval ───────────────────────────────────────
    public byte[] getPhotoBytes(String photoName) {
        if (photoName == null || photoName.isEmpty()) return null;
        File f = new File(PHOTO_DIR + File.separator + photoName);
        if (!f.exists()) return null;
        try (FileInputStream fis = new FileInputStream(f)) {
            byte[] bytes = new byte[(int) f.length()];
            int total = 0;
            while (total < bytes.length) {
                int n = fis.read(bytes, total, bytes.length - total);
                if (n == -1) break;
                total += n;
            }
            return bytes;
        } catch (IOException e) {
            System.err.println("Photo read error: " + e.getMessage());
            return null;
        }
    }

    public String getPhotoContentType(String photoName) {
        if (photoName == null) return "image/jpeg";
        String lower = photoName.toLowerCase();
        if (lower.endsWith(".png"))  return "image/png";
        if (lower.endsWith(".gif"))  return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        return "image/jpeg";
    }

    // ── Reminder logic ────────────────────────────────────────
    // Returns the number of calendar days between today and a.date.
    // Negative = in the past, 0 = today, positive = future.
    public long daysUntil(Appointment a) {
        try {
            DATE_FMT.setLenient(false);
            Date apptDate = DATE_FMT.parse(a.date);
            // Strip time from today so we compare dates only
            Calendar today = Calendar.getInstance();
            today.set(Calendar.HOUR_OF_DAY, 0);
            today.set(Calendar.MINUTE,      0);
            today.set(Calendar.SECOND,      0);
            today.set(Calendar.MILLISECOND, 0);
            long diffMs = apptDate.getTime() - today.getTimeInMillis();
            return diffMs / (1000L * 60 * 60 * 24);
        } catch (Exception e) {
            return 999; // unparseable date — treat as far future
        }
    }

    // Appointments within the next 30 days, sorted soonest first
    public List<Appointment> getUpcoming() {
        List<Appointment> upcoming = new ArrayList<>();
        for (Appointment a : list) {
            long days = daysUntil(a);
            if (days >= 0 && days <= 30) upcoming.add(a);
        }
        upcoming.sort((x, y) -> x.date.compareTo(y.date));
        return upcoming;
    }

    // ── Queries ───────────────────────────────────────────────
    public synchronized List<Appointment> getAll(String sort) {
        List<Appointment> copy = new ArrayList<>(list);
        if ("asc".equals(sort))
            copy.sort((a, b) -> a.date.compareTo(b.date));
        else if ("desc".equals(sort))
            copy.sort((a, b) -> b.date.compareTo(a.date));
        return copy;
    }

    public List<Appointment> getAll() { return getAll(""); }

    public synchronized List<Appointment> search(String kw) {
        List<Appointment> res = new ArrayList<>();
        if (kw == null || kw.isEmpty()) return res;
        String k = kw.toLowerCase();
        for (Appointment a : list) {
            if (a.date.contains(k)
                || a.time.contains(k)
                || a.person.toLowerCase().contains(k)
                || a.notes.toLowerCase().contains(k))
                res.add(a);
        }
        return res;
    }

    public synchronized Appointment findById(String id) {
        if (id == null) return null;
        for (Appointment a : list)
            if (a.id.equals(id)) return a;
        return null;
    }

    // ── Private helpers ───────────────────────────────────────
    private void savePhotoFile(String name, byte[] bytes) {
        File f = new File(PHOTO_DIR + File.separator + name);
        try (FileOutputStream fos = new FileOutputStream(f)) {
            fos.write(bytes);
        } catch (IOException e) {
            System.err.println("Photo save failed: " + e.getMessage());
        }
    }

    private void save() {
        try (PrintWriter pw =
                new PrintWriter(new FileWriter(DATA_FILE))) {
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
        try (BufferedReader br =
                new BufferedReader(new FileReader(f))) {
            String first = br.readLine();
            if (first != null)
                nextId = Integer.parseInt(first.trim());
            String line;
            while ((line = br.readLine()) != null) {
                Appointment a = Appointment.fromFileLine(line);
                if (a != null) list.add(a);
            }
        } catch (IOException | NumberFormatException e) {
            System.err.println("Load failed: " + e.getMessage());
        }
    }
}
