import java.io.*;
import java.net.*;
import java.util.*;

public class MailboxManager {

    // ----------------------------------------------------------------
    // Server settings - change these to match your server
    // ----------------------------------------------------------------
    static String host     = "127.0.0.1";
    static int    port     = 110;
    static String username = "testuser";
    static String password = "test123";

    // ----------------------------------------------------------------
    // Global variables used throughout the program
    // ----------------------------------------------------------------
    static Socket         socket;
    static BufferedReader reader;
    static PrintWriter    writer;
    static Scanner        keyboard = new Scanner(System.in);

    // These lists store the information about each email
    static ArrayList<Integer> messageNumbers  = new ArrayList<Integer>();
    static ArrayList<Integer> messageSizes    = new ArrayList<Integer>();
    static ArrayList<String>  fromList        = new ArrayList<String>();
    static ArrayList<String>  subjectList     = new ArrayList<String>();
    static ArrayList<Boolean> deletedList     = new ArrayList<Boolean>();  // tracks deleted messages


    // ================================================================
    // MAIN - This is where the program starts
    // ================================================================
    public static void main(String[] args) throws Exception {

        printHeader();
        connectToServer();
        loginToServer();
        loadMessages();

        // Keep showing the menu until the user chooses to quit
        boolean running = true;
        while (running) {
            printMenu();
            String choice = getUserInput("Your choice");

            if (choice.equals("1")) {
                showAllMessages();
            } else if (choice.equals("2")) {
                readAMessage();
            } else if (choice.equals("3")) {
                searchMessages();
            } else if (choice.equals("4")) {
                deleteMessages();
            } else if (choice.equals("5")) {
                running = false;
            } else {
                System.out.println("  Please enter a number between 1 and 5.");
            }
        }

        quitAndDisconnect();
    }


    // ================================================================
    // STEP 1: Connect to the mail server
    // ================================================================
    static void connectToServer() throws Exception {
        System.out.println("  Connecting to " + host + " on port " + port + "...");

        socket = new Socket(host, port);
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        writer = new PrintWriter(socket.getOutputStream(), true);

        String serverGreeting = reader.readLine();
        System.out.println("  Server: " + serverGreeting);
        System.out.println("  Connected!\n");
    }


    // ================================================================
    // STEP 2: Log in with username and password
    // ================================================================
    static void loginToServer() throws Exception {
        sendCommand("USER " + username);
        System.out.println("  Server: " + reader.readLine());

        sendCommand("PASS " + password);
        System.out.println("  Server: " + reader.readLine());

        // STAT command tells us how many messages and total size
        sendCommand("STAT");
        String statReply = reader.readLine();

        // STAT reply looks like: "+OK 3 12345"
        // That means 3 messages, 12345 bytes total
        String[] statParts    = statReply.split(" ");
        String totalMessages  = statParts[1];
        String totalBytes     = statParts[2];

        System.out.println("\n  You have " + totalMessages + " message(s) totalling " + totalBytes + " bytes.");
        printDivider();
    }


    // ================================================================
    // STEP 3: Load all message headers from the server
    // ================================================================
    static void loadMessages() throws Exception {
        System.out.println("  Loading your messages...");

        // LIST command gives us message numbers and sizes
        sendCommand("LIST");
        reader.readLine(); // skip the "+OK" line

        String line = reader.readLine();
        while (!line.equals(".")) {
            String[] parts = line.split(" ");
            messageNumbers.add(Integer.parseInt(parts[0]));
            messageSizes.add(Integer.parseInt(parts[1]));
            deletedList.add(false);  // nothing is deleted yet
            line = reader.readLine();
        }

        if (messageNumbers.size() == 0) {
            System.out.println("  No messages found. Goodbye!");
            quitAndDisconnect();
            System.exit(0);
        }

        // For each message, get only the headers using TOP
        // TOP n 0 means: get message n, but only 0 lines of the body
        for (int i = 0; i < messageNumbers.size(); i++) {
            int messageNumber = messageNumbers.get(i);
            loadHeadersForMessage(messageNumber);
        }

        System.out.println("  Done loading " + messageNumbers.size() + " message(s).\n");
    }


    // ================================================================
    // Load the From and Subject headers for one message
    // ================================================================
    static void loadHeadersForMessage(int messageNumber) throws Exception {
        sendCommand("TOP " + messageNumber + " 0");
        reader.readLine(); // skip "+OK"

        String from    = "(unknown sender)";
        String subject = "(no subject)";

        String headerLine = reader.readLine();
        while (!headerLine.equals(".") && !headerLine.equals("")) {
            if (headerLine.toLowerCase().startsWith("from:")) {
                from = headerLine.substring(5).trim();
            }
            if (headerLine.toLowerCase().startsWith("subject:")) {
                subject = headerLine.substring(8).trim();
            }
            headerLine = reader.readLine();
        }

        // Drain any remaining lines until the final "."
        while (!headerLine.equals(".")) {
            headerLine = reader.readLine();
        }

        fromList.add(from);
        subjectList.add(subject);
    }


    // ================================================================
    // MENU OPTION 1: Show all messages in a table
    // ================================================================
    static void showAllMessages() {
        System.out.println();
        printDivider();
        System.out.println("  No.  From                          Subject                       Size        Status");
        printDivider();

        for (int i = 0; i < messageNumbers.size(); i++) {

            // If this message was deleted, show [DELETED], otherwise show nothing
            String status = "";
            if (deletedList.get(i) == true) {
                status = "[DELETED]";
            }

            // Build each column as a fixed-width string so the table lines up neatly
            String num     = padRight(String.valueOf(messageNumbers.get(i)), 4);
            String from    = padRight(shorten(fromList.get(i), 29), 29);
            String subject = padRight(shorten(subjectList.get(i), 29), 29);
            String size    = padRight(messageSizes.get(i) + " bytes", 11);

            System.out.println("  " + num + " " + from + " " + subject + " " + size + " " + status);
        }

        printDivider();
        System.out.println("  Total: " + messageNumbers.size() + " message(s)");
        printDivider();
    }


    // ================================================================
    // MENU OPTION 2: Read a full message
    // ================================================================
    static void readAMessage() throws Exception {
        showAllMessages();
        String input = getUserInput("Enter message number to read");

        // Convert the text the user typed into a number
        int messageNumber = Integer.parseInt(input);

        // Check the message exists
        if (!messageNumbers.contains(messageNumber)) {
            System.out.println("  That message does not exist.");
            return;
        }

        // RETR downloads the full message including the body
        // We read lines until we see a single dot "." which means end of message
        sendCommand("RETR " + messageNumber);
        reader.readLine(); // skip the "+OK" line

        System.out.println();
        printDivider();
        System.out.println("  MESSAGE #" + messageNumber);
        printDivider();

        String line = reader.readLine();
        while (!line.equals(".")) {
            System.out.println("  " + line);
            line = reader.readLine();
        }

        printDivider();
    }


    // ================================================================
    // MENU OPTION 3: Search messages by keyword
    // ================================================================
    static void searchMessages() {
        String keyword = getUserInput("Enter a keyword to search for").toLowerCase();

        System.out.println();
        printDivider();
        System.out.println("  Search results for: " + keyword);
        printDivider();

        int matchCount = 0;

        for (int i = 0; i < messageNumbers.size(); i++) {
            boolean senderMatches  = fromList.get(i).toLowerCase().contains(keyword);
            boolean subjectMatches = subjectList.get(i).toLowerCase().contains(keyword);

            if (senderMatches || subjectMatches) {

                String status = "";
                if (deletedList.get(i) == true) {
                    status = "[DELETED]";
                }

                String num     = padRight(String.valueOf(messageNumbers.get(i)), 4);
                String from    = padRight(shorten(fromList.get(i), 29), 29);
                String subject = padRight(shorten(subjectList.get(i), 29), 29);

                System.out.println("  " + num + " " + from + " " + subject + " " + status);
                matchCount++;
            }
        }

        if (matchCount == 0) {
            System.out.println("  No messages found matching: " + keyword);
        } else {
            System.out.println("\n  Found " + matchCount + " matching message(s).");
        }

        printDivider();
    }


    // ================================================================
    // MENU OPTION 4: Delete one or more messages
    // ================================================================
    static void deleteMessages() throws Exception {
        showAllMessages();
        System.out.println("  Enter message numbers to delete.");
        String input = getUserInput("Type one number e.g. 3  OR  multiple e.g. 1,3,5");

        String[] parts = input.split(",");

        for (int i = 0; i < parts.length; i++) {
            String part = parts[i].trim();
            deleteOneMessage(part);
        }
    }


    // ================================================================
    // Delete a single message by its number
    // ================================================================
    static void deleteOneMessage(String numberAsText) throws Exception {
        try {
            // Convert the text into a number e.g. "3" becomes 3
            int messageNumber = Integer.parseInt(numberAsText);

            // Check the message exists in our list
            if (!messageNumbers.contains(messageNumber)) {
                System.out.println("  Message " + messageNumber + " does not exist. Skipping.");
                return;
            }

            // Find where this message sits in our lists (position 0, 1, 2 etc)
            int index = messageNumbers.indexOf(messageNumber);

            // Check if it was already deleted
            if (deletedList.get(index) == true) {
                System.out.println("  Message " + messageNumber + " is already marked for deletion.");
                return;
            }

            // Tell the server to mark this message for deletion
            // NOTE: it is not actually deleted yet - that only happens on QUIT
            sendCommand("DELE " + messageNumber);
            String response = reader.readLine();
            System.out.println("  Server: " + response);

            // Update our local list so the table shows [DELETED]
            deletedList.set(index, true);
            System.out.println("  Message " + messageNumber + " marked for deletion.");

        } catch (NumberFormatException e) {
            System.out.println("  '" + numberAsText + "' is not a valid number. Skipping.");
        }
    }


    // ================================================================
    // STEP 7: Quit the program and disconnect from the server
    //         NOTE: The server only permanently deletes messages on QUIT
    // ================================================================
    static void quitAndDisconnect() throws Exception {
        printDivider();
        sendCommand("QUIT");
        System.out.println("  Server: " + reader.readLine());
        socket.close();
        System.out.println("  Disconnected. Goodbye!");
        printDivider();
    }


    // ================================================================
    // HELPER: Send a command to the server
    // ================================================================
    static void sendCommand(String command) {
        writer.println(command);
    }


    // ================================================================
    // HELPER: Ask the user to type something and return their answer
    // ================================================================
    static String getUserInput(String prompt) {
        System.out.print("  " + prompt + ": ");
        return keyboard.nextLine().trim();
    }


    // ================================================================
    // HELPER: Print the top banner
    // ================================================================
    static void printHeader() {
        System.out.println();
        System.out.println("  ╔══════════════════════════════════════╗");
        System.out.println("  ║         MAILBOX MANAGER              ║");
        System.out.println("  ║         COS332 - Practical 7         ║");
        System.out.println("  ╚══════════════════════════════════════╝");
        System.out.println();
    }


    // ================================================================
    // HELPER: Print a divider line
    // ================================================================
    static void printDivider() {
        System.out.println("  ----------------------------------------");
    }


    // ================================================================
    // HELPER: Print the menu options
    // ================================================================
    static void printMenu() {
        System.out.println();
        printDivider();
        System.out.println("  MENU");
        printDivider();
        System.out.println("  1. Show all messages");
        System.out.println("  2. Read a message");
        System.out.println("  3. Search messages");
        System.out.println("  4. Delete message(s)");
        System.out.println("  5. Quit");
        printDivider();
    }


    // ================================================================
    // HELPER: Shorten a string if it is too long for the table
    // ================================================================
    static String shorten(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength - 3) + "...";
    }


    // ================================================================
    // HELPER: Pad a string with spaces so it fills a fixed width
    //         This makes the table columns line up neatly
    //         e.g. padRight("Hi", 5) returns "Hi   "
    // ================================================================
    static String padRight(String text, int width) {
        // If the text is already longer than the width, just return it as is
        if (text.length() >= width) {
            return text;
        }

        // Add spaces at the end until it reaches the right width
        String result = text;
        while (result.length() < width) {
            result = result + " ";
        }
        return result;
    }
}
