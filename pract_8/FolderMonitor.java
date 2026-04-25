// ============================================================
// FolderMonitor.java
// Watches a folder and tells us which files are new or changed.
// ============================================================

import java.io.*;
import java.util.*;

public class FolderMonitor {

    // The folder we are watching
    private File folder;

    // Remembers each file and when it was last saved
    // Key: filename, Value: last modified time
    private HashMap<String, Long> savedTimes = new HashMap<>();

    // Constructor: Set up the monitor and remember all files already in the folder
    public FolderMonitor(String folderPath) {

        folder = new File(folderPath);

        // Loop through all files in the folder
        for (File f : folder.listFiles()) {
            // Only remember files, not folders
            if (f.isFile()) {
                // Save the filename and its last modified time
                savedTimes.put(f.getName(), f.lastModified());
            }
        }

        System.out.println("Watching: " + folderPath);
        System.out.println("Found " + savedTimes.size() + " existing file(s).");
    }

    // Method: Check the folder and return a list of files that are new or changed
    public List<File> getChangedFiles() {

        // Create a list to hold changed files
        List<File> changed = new ArrayList<>();

        // Loop through all files in the folder again
        for (File f : folder.listFiles()) {

            // Skip folders, only look at files
            if (!f.isFile()) continue;

            // Get the file name and last modified time
            String name = f.getName();
            long modTime = f.lastModified();

            // Check if this is a new file (not in our saved list)
            if (!savedTimes.containsKey(name)) {
                // Brand new file we have never seen before
                System.out.println("  New file: " + name);
                changed.add(f);  // Add to changed list
                savedTimes.put(name, modTime);  // Remember it

            // Check if the file was modified (time changed)
            } else if (savedTimes.get(name) != modTime) {
                // File we know, but it was edited since last check
                System.out.println("  Changed file: " + name);
                changed.add(f);  // Add to changed list
                savedTimes.put(name, modTime);  // Update the time
            }
            // If not new or changed, do nothing
        }

        // Return the list of changed files
        return changed;
    }
}
