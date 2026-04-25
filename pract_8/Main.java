// ============================================================
// Main.java
// Starts the program and runs the backup loop.
// Every 60 seconds: check for changed files, upload them.
// ============================================================

import java.io.*;
import java.util.*;

public class Main {

    // Settings for the program
    static String SERVER_IP     = settings.SERVER_IP;   // Read FTP server IP from settings.java
    static int    SERVER_PORT   = settings.SERVER_PORT;   // The port number for FTP (usually 21)
    static String USERNAME      = settings.USERNAME;  // Read FTP username from settings.java
    static String PASSWORD      = settings.PASSWORD;  // Read FTP password from settings.java
    static String WATCH_FOLDER  = settings.WATCH_FOLDER;  // The folder on your computer to watch for changes
    static String BACKUP_FOLDER = settings.BACKUP_FOLDER; // The folder on the server where files will be saved
    static int    CHECK_EVERY   = settings.CHECK_EVERY;   // How often to check for changes (in seconds)

    // This is where the program starts
    public static void main(String[] args) {

        System.out.println("== File Backup Program ==\n");

        // Start watching the folder
        FolderMonitor monitor = new FolderMonitor(WATCH_FOLDER);

        System.out.println("Checking every " + CHECK_EVERY + " seconds...\n");

        // Run forever until the user presses Ctrl+C
        while (true) {

            try {
                // Wait before checking again
                Thread.sleep(CHECK_EVERY * 1000L);
            } catch (InterruptedException e) {
                // If interrupted, stop the program
                System.out.println("Program interrupted. Stopping...");
                break;
            }

            try {
                // See which files are new or changed
                List<File> changedFiles = monitor.getChangedFiles();

                if (changedFiles.isEmpty()) {
                    System.out.println("No changes.");

                } else {
                    System.out.println(changedFiles.size() + " file(s) changed. Uploading...");

                    // Connect to the server and upload all changed files
                    FTPConnection conn = new FTPConnection();
                    conn.connect();
                    conn.login();

                    FTPUploader uploader = new FTPUploader(conn);
                    for (File f : changedFiles) {
                        uploader.upload(f);
                    }

                    conn.disconnect();
                }
            } catch (Exception e) {
                // If something goes wrong, print error and continue
                System.out.println("Error during check/upload: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}
