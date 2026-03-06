import java.io.*;

/**
 * Helper class that wraps a PrintStream and provides
 * ANSI / VT100 escape-sequence methods for terminal control.
 *
 * All output goes through the underlying stream so the server
 * never writes to its own console during a session.
 */
public class Terminal {

    // -------------------------------------------------------
    // ANSI colours (foreground)
    // -------------------------------------------------------
    public static final String RESET   = "\u001B[0m";
    public static final String BOLD    = "\u001B[1m";
    public static final String BLACK   = "\u001B[30m";
    public static final String RED     = "\u001B[31m";
    public static final String GREEN   = "\u001B[32m";
    public static final String YELLOW  = "\u001B[33m";
    public static final String BLUE    = "\u001B[34m";
    public static final String MAGENTA = "\u001B[35m";
    public static final String CYAN    = "\u001B[36m";
    public static final String WHITE   = "\u001B[37m";

    // Background colours
    public static final String BG_BLUE  = "\u001B[44m";
    public static final String BG_BLACK = "\u001B[40m";

    private final PrintStream out;

    public Terminal(OutputStream os) {
        // Use ISO-8859-1 so every byte is transmitted as-is to the Telnet client
        try {
            this.out = new PrintStream(os, true, "ISO-8859-1");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new RuntimeException(e); // ISO-8859-1 is always supported
        }
    }

    // -------------------------------------------------------
    // Cursor / screen control
    // -------------------------------------------------------

    /** Clear the entire screen. */
    public void clearScreen() {
        out.print("\u001B[2J");
        out.flush();
    }

    /** Move cursor to row r, column c (both 1-based). */
    public void moveTo(int row, int col) {
        out.print("\u001B[" + row + ";" + col + "H");
        out.flush();
    }

    /** Clear from cursor to end of line. */
    public void clearEOL() {
        out.print("\u001B[K");
        out.flush();
    }

    /** Hide the cursor. */
    public void hideCursor() {
        out.print("\u001B[?25l");
        out.flush();
    }

    /** Show the cursor. */
    public void showCursor() {
        out.print("\u001B[?25h");
        out.flush();
    }

    // -------------------------------------------------------
    // Convenience print methods
    // -------------------------------------------------------

    public void print(String s) {
        out.print(s);
        out.flush();
    }

    public void println(String s) {
        // Telnet requires CR+LF for proper line endings
        out.print(s + "\r\n");
        out.flush();
    }

    public void println() {
        out.print("\r\n");
        out.flush();
    }

    /** Print a coloured string then reset. */
    public void printColour(String colour, String s) {
        out.print(colour + s + RESET);
        out.flush();
    }

    public void printlnColour(String colour, String s) {
        out.print(colour + s + RESET + "\r\n");
        out.flush();
    }

    /** Draw a horizontal rule across the given width. */
    public void horizontalRule(int width, char ch) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < width; i++) sb.append(ch);
        println(sb.toString());
    }

    /**
     * Draw the title bar at row 1 across the terminal.
     * Clears the screen first.
     */
    public void drawTitleBar(String title, int width) {
        clearScreen();
        moveTo(1, 1);
        printColour(BG_BLUE + BOLD + WHITE,
                centre(title, width));
        moveTo(2, 1);
        printlnColour(CYAN, repeat('-', width));
    }

    // -------------------------------------------------------
    // Utility
    // -------------------------------------------------------

    private String centre(String s, int width) {
        if (s.length() >= width) return s;
        int pad = (width - s.length()) / 2;
        return repeat(' ', pad) + s + repeat(' ', width - pad - s.length());
    }

    private String repeat(char c, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(c);
        return sb.toString();
    }
}
