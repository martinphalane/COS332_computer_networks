import java.io.*;
import java.net.*;
import java.util.*;

/**
 * Handles one connected Telnet client in its own thread.
 * Provides an interactive, ANSI-coloured menu for managing appointments.
 *
 * The server echoes every character so the session works correctly
 * regardless of whether the client has localecho on or off.
 */
public class ClientHandler implements Runnable {

    // Shared database across all client threads
    private static final AppointmentDatabase DB = new AppointmentDatabase();

    private static final int WIDTH = 70;

    private final Socket socket;
    private Terminal term;
    private BufferedInputStream in;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    // -------------------------------------------------------
    // Entry point
    // -------------------------------------------------------

    @Override
    public void run() {
        try {
            in   = new BufferedInputStream(socket.getInputStream());
            term = new Terminal(socket.getOutputStream());

            // Negotiate Telnet: tell the client we will echo (WILL ECHO = 0xFF 0xFB 0x01)
            // and suppress go-ahead (WILL SGA = 0xFF 0xFB 0x03)
            socket.getOutputStream().write(new byte[]{
                (byte)0xFF, (byte)0xFB, 0x01,   // IAC WILL ECHO
                (byte)0xFF, (byte)0xFB, 0x03    // IAC WILL SGA
            });
            socket.getOutputStream().flush();

            showReminders();
            showMainMenu();
        } catch (IOException e) {
            // client disconnected - no console output during demo
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    // -------------------------------------------------------
    // Upcoming reminders — shown once on connect
    // -------------------------------------------------------

    private void showReminders() throws IOException {
        List<Appointment> upcoming = DB.getUpcoming(7);
        if (upcoming.isEmpty()) return;

        term.clearScreen();
        term.moveTo(1, 1);

        // Header banner
        term.printlnColour(Terminal.BG_BLUE + Terminal.BOLD + Terminal.WHITE,
            centre("  UPCOMING APPOINTMENTS — NEXT 7 DAYS  ", WIDTH));
        term.println();

        // Warning icon line
        term.printlnColour(Terminal.YELLOW + Terminal.BOLD,
            "  You have " + upcoming.size()
            + (upcoming.size() == 1 ? " appointment" : " appointments")
            + " coming up:");
        term.println();

        // Table header
        term.printlnColour(Terminal.BOLD + Terminal.WHITE,
            String.format("  %-12s %-6s %-20s %s", "Date", "Time", "Customer", "Notes"));
        term.printlnColour(Terminal.CYAN, "  " + repeat('-', WIDTH - 2));

        // Each upcoming appointment
        for (int i = 0; i < upcoming.size(); i++) {
            Appointment a = upcoming.get(i);
            String colour = (i % 2 == 0) ? Terminal.YELLOW : Terminal.WHITE;
            term.printlnColour(colour,
                String.format("  %-12s %-6s %-20s %s",
                    a.getDate(),
                    a.getTime(),
                    truncate(a.getPerson(), 20),
                    truncate(a.getNotes(), 25)));
        }

        term.println();
        term.printlnColour(Terminal.CYAN, "  " + repeat('-', WIDTH - 2));
        pressEnterToContinue();
    }

    // -------------------------------------------------------
    // Main menu
    // -------------------------------------------------------

    private void showMainMenu() throws IOException {
        while (true) {
            term.drawTitleBar("  APPOINTMENT MANAGER  ", WIDTH);
            term.moveTo(3, 1);
            term.printlnColour(Terminal.YELLOW + Terminal.BOLD, " Main Menu");
            term.println();
            term.printlnColour(Terminal.GREEN,  "  [1]  View all appointments");
            term.printlnColour(Terminal.GREEN,  "  [2]  Add appointment");
            term.printlnColour(Terminal.GREEN,  "  [3]  Search appointments");
            term.printlnColour(Terminal.GREEN,  "  [4]  Delete appointment");
            term.printlnColour(Terminal.GREEN,  "  [5]  Edit appointment");
            term.printlnColour(Terminal.RED,    "  [Q]  Quit");
            term.println();
            term.horizontalRule(WIDTH, '-');
            term.print(Terminal.CYAN + "  Choice: " + Terminal.RESET);

            char choice = readChar();
            term.println();   // move past the echoed character

            switch (Character.toUpperCase(choice)) {
                case '1' -> viewAll();
                case '2' -> addNew();
                case '3' -> search();
                case '4' -> delete();
                case '5' -> edit();
                case 'Q' -> {
                    term.clearScreen();
                    term.moveTo(5, 1);
                    term.printlnColour(Terminal.YELLOW, "  Goodbye! Session ended.");
                    term.println();
                    return;
                }
                default -> {
                    term.printlnColour(Terminal.RED, "  Unknown option. Try again.");
                    pause(1000);
                }
            }
        }
    }

    // -------------------------------------------------------
    // View all
    // -------------------------------------------------------

    private void viewAll() throws IOException {
        term.drawTitleBar("  ALL APPOINTMENTS  ", WIDTH);
        term.moveTo(3, 1);
        List<Appointment> list = DB.getAllSorted();
        if (list.isEmpty()) {
            term.printlnColour(Terminal.YELLOW, "  No appointments stored yet.");
        } else {
            term.printlnColour(Terminal.CYAN, "  Sorted by date and time");
            term.println();
            printTableHeader();
            int i = 1;
            for (Appointment a : list) {
                printRow(i++, a);
            }
        }
        pressEnterToContinue();
    }

    // -------------------------------------------------------
    // Add
    // -------------------------------------------------------

    private void addNew() throws IOException {
        term.drawTitleBar("  ADD APPOINTMENT  ", WIDTH);
        term.moveTo(3, 1);

        term.printlnColour(Terminal.CYAN, "  Enter appointment details (blank date to cancel)");
        term.println();

        String date   = prompt("  Date   (dd/mm/yyyy): ");
        if (date.isEmpty()) return;
        String time   = prompt("  Time   (HH:MM)     : ");
        String person = prompt("  Customer name      : ");
        String notes  = prompt("  Notes               : ");

        if (time.isEmpty() || person.isEmpty()) {
            term.printlnColour(Terminal.RED, "  Time and customer name are required. Cancelled.");
            pause(1200);
            return;
        }

        DB.add(new Appointment(date, time, person, notes));
        term.println();
        term.printlnColour(Terminal.GREEN, "  ✔  Appointment saved!");
        pause(1200);
    }

    // -------------------------------------------------------
    // Search
    // -------------------------------------------------------

    private void search() throws IOException {
        term.drawTitleBar("  SEARCH APPOINTMENTS  ", WIDTH);
        term.moveTo(3, 1);

        String query = prompt("  Search (date / name / notes): ");
        if (query.isEmpty()) return;

        term.println();
        List<Appointment> results = DB.search(query);
        if (results.isEmpty()) {
            term.printlnColour(Terminal.YELLOW, "  No matches found for: " + query);
        } else {
            term.printlnColour(Terminal.GREEN, "  Found " + results.size() + " result(s):");
            term.println();
            printTableHeader();
            int i = 1;
            for (Appointment a : results) {
                printRow(i++, a);
            }
        }
        pressEnterToContinue();
    }

    // -------------------------------------------------------
    // Delete
    // -------------------------------------------------------

    private void delete() throws IOException {
        term.drawTitleBar("  DELETE APPOINTMENT  ", WIDTH);
        term.moveTo(3, 1);

        List<Appointment> list = DB.getAll();
        if (list.isEmpty()) {
            term.printlnColour(Terminal.YELLOW, "  No appointments to delete.");
            pause(1200);
            return;
        }

        printTableHeader();
        int i = 1;
        for (Appointment a : list) {
            printRow(i++, a);
        }
        term.println();

        String numStr = prompt("  Enter number to delete (blank to cancel): ");
        if (numStr.isEmpty()) return;

        try {
            int num = Integer.parseInt(numStr.trim());
            Appointment toDelete = DB.get(num);
            if (toDelete == null) {
                term.printlnColour(Terminal.RED, "  Invalid number.");
                pause(1200);
                return;
            }

            // Show the appointment and ask for confirmation
            term.println();
            term.printlnColour(Terminal.YELLOW + Terminal.BOLD, "  About to delete:");
            term.printlnColour(Terminal.WHITE, String.format("  %s at %s with %s",
                toDelete.getDate(), toDelete.getTime(), toDelete.getPerson()) + "  —  " + truncate(toDelete.getNotes(), 20));
            term.println();
            term.print(Terminal.RED + Terminal.BOLD + "  Confirm delete? (Y/N): " + Terminal.RESET);

            char confirm = readChar();
            term.println();

            if (Character.toUpperCase(confirm) == 'Y') {
                DB.delete(num);
                term.printlnColour(Terminal.GREEN, "  ✔  Appointment #" + num + " deleted.");
            } else {
                term.printlnColour(Terminal.YELLOW, "  Cancelled — no changes made.");
            }
        } catch (NumberFormatException e) {
            term.printlnColour(Terminal.RED, "  Please enter a valid number.");
        }
        pause(1500);
    }


    // -------------------------------------------------------
    // Edit
    // -------------------------------------------------------

    private void edit() throws IOException {
        term.drawTitleBar("  EDIT APPOINTMENT  ", WIDTH);
        term.moveTo(3, 1);

        List<Appointment> list = DB.getAll();
        if (list.isEmpty()) {
            term.printlnColour(Terminal.YELLOW, "  No appointments to edit.");
            pause(1200);
            return;
        }

        printTableHeader();
        int i = 1;
        for (Appointment a : list) {
            printRow(i++, a);
        }
        term.println();

        String numStr = prompt("  Enter number to edit (blank to cancel): ");
        if (numStr.isEmpty()) return;

        int num;
        try {
            num = Integer.parseInt(numStr.trim());
        } catch (NumberFormatException e) {
            term.printlnColour(Terminal.RED, "  Please enter a valid number.");
            pause(1200);
            return;
        }

        Appointment existing = DB.get(num);
        if (existing == null) {
            term.printlnColour(Terminal.RED, "  Invalid number.");
            pause(1200);
            return;
        }

        // Show current values and let user edit field by field
        // Pressing Enter on a blank line keeps the existing value
        term.println();
        term.printlnColour(Terminal.CYAN, "  Press Enter on any field to keep the current value.");
        term.println();

        term.printlnColour(Terminal.WHITE, "  Current date   : " + existing.getDate());
        String date = prompt("  New date        (dd/mm/yyyy): ");
        if (date.isEmpty()) date = existing.getDate();

        term.printlnColour(Terminal.WHITE, "  Current time   : " + existing.getTime());
        String time = prompt("  New time        (HH:MM)     : ");
        if (time.isEmpty()) time = existing.getTime();

        term.printlnColour(Terminal.WHITE, "  Current customer : " + existing.getPerson());
        String person = prompt("  New customer name          : ");
        if (person.isEmpty()) person = existing.getPerson();

        term.printlnColour(Terminal.WHITE, "  Current notes  : " + existing.getNotes());
        String notes = prompt("  New notes                   : ");
        if (notes.isEmpty()) notes = existing.getNotes();

        Appointment updated = new Appointment(date, time, person, notes);
        if (DB.edit(num, updated)) {
            term.println();
            term.printlnColour(Terminal.GREEN, "  ✔  Appointment #" + num + " updated successfully.");
        } else {
            term.printlnColour(Terminal.RED, "  Failed to update appointment.");
        }
        pause(1500);
    }

    // -------------------------------------------------------
    // Table helpers
    // -------------------------------------------------------

    private void printTableHeader() {
        term.printlnColour(Terminal.BOLD + Terminal.WHITE,
            String.format("  %-4s %-12s %-6s %-18s %s",
                "#", "Date", "Time", "Customer", "Notes"));
        term.printlnColour(Terminal.CYAN,
            "  " + repeat('-', WIDTH - 2));
    }

    private void printRow(int idx, Appointment a) {
        String colour = (idx % 2 == 0) ? Terminal.WHITE : Terminal.YELLOW;
        term.printlnColour(colour,
            String.format("  %-4d %-12s %-6s %-18s %s",
                idx,
                a.getDate(),
                a.getTime(),
                truncate(a.getPerson(), 18),
                truncate(a.getNotes(), 25)));
    }

    // -------------------------------------------------------
    // Input helpers
    // -------------------------------------------------------

    /**
     * Read a single character from the stream, skipping Telnet IAC sequences
     * and filtering out non-printable control codes.
     * The character is echoed back so the user sees what they typed.
     */
    private char readChar() throws IOException {
        while (true) {
            int b = in.read();
            if (b == -1) throw new IOException("Client disconnected");

            // Handle Telnet IAC (0xFF) negotiation — skip 3 bytes
            if (b == 0xFF) {
                in.read(); // command byte
                in.read(); // option byte
                continue;
            }

            if (b < 32 || b > 126) continue; // skip control characters

            char c = (char) b;
            // Echo the character back
            term.print(String.valueOf(c));
            return c;
        }
    }

    /**
     * Read a full line. Handles backspace, echo, and CR/LF termination.
     * Telnet sends CR+NUL or CR+LF for Enter.
     */
    private String readLine() throws IOException {
        StringBuilder sb = new StringBuilder();
        while (true) {
            int b = in.read();
            if (b == -1) throw new IOException("Client disconnected");

            // Skip Telnet IAC negotiation sequences
            if (b == 0xFF) {
                in.read();
                in.read();
                continue;
            }

            if (b == '\r' || b == '\n') {
                // Absorb the paired LF or NUL that follows CR
                if (b == '\r') {
                    in.mark(1);
                    int next = in.read();
                    if (next != '\n' && next != 0) {
                        // Not a paired char — put it back
                        // BufferedInputStream supports mark/reset
                        in.reset();
                    }
                }
                term.println(); // move to next line on client
                return sb.toString();
            }

            // Backspace (DEL = 127, BS = 8)
            if (b == 127 || b == 8) {
                if (sb.length() > 0) {
                    sb.deleteCharAt(sb.length() - 1);
                    // Move back, overwrite with space, move back again
                    term.print("\b \b");
                }
                continue;
            }

            if (b >= 32 && b < 127) {
                char c = (char) b;
                sb.append(c);
                term.print(String.valueOf(c)); // echo
            }
        }
    }

    /** Print a prompt then collect a line of input. */
    private String prompt(String message) throws IOException {
        term.print(Terminal.CYAN + message + Terminal.RESET);
        return readLine().trim();
    }

    private void pressEnterToContinue() throws IOException {
        term.println();
        term.print(Terminal.CYAN + "  Press Enter to continue..." + Terminal.RESET);
        // Drain until CR/LF
        while (true) {
            int b = in.read();
            if (b == -1) throw new IOException("Client disconnected");
            if (b == 0xFF) { in.read(); in.read(); continue; }
            if (b == '\r' || b == '\n') { term.println(); return; }
        }
    }

    // -------------------------------------------------------
    // Utilities
    // -------------------------------------------------------

    private void pause(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "...";
    }

    private String repeat(char c, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(c);
        return sb.toString();
    }

    private String centre(String s, int width) {
        if (s.length() >= width) return s;
        int pad = (width - s.length()) / 2;
        return repeat(' ', pad) + s + repeat(' ', width - pad - s.length());
    }

}
