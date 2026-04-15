import java.io.*;
import java.net.*;
import java.time.LocalDate;
import java.util.Scanner;

public class Reminder {
    public static void main(String[] args) {

        // 1. Setup the dates
        LocalDate today = LocalDate.now();
        LocalDate targetDate = today.plusDays(6);
        boolean foundAny = false;

        System.out.println("Searching for events on: " + targetDate);

        // 2. Open and read the events.txt file
        try {
            File myFile = new File("events.txt");
            Scanner fileScanner = new Scanner(myFile);

            while (fileScanner.hasNextLine()) {
                String line = fileScanner.nextLine();

                // Skip comments (#) or empty lines
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                // Split the line "21/04 Practice" into ["21/04", "Practice"]
                String[] parts = line.split(" ", 2);
                String dateString = parts[0]; 
                String description = parts[1];

                // Split "21/04" into [21, 04]
                String[] dateParts = dateString.split("/");
                int day = Integer.parseInt(dateParts[0]);
                int month = Integer.parseInt(dateParts[1]);
                int year = today.getYear();

                LocalDate eventDate = LocalDate.of(year, month, day);

                // If the date matches 6 days from now, send the email
                if (!eventDate.isBefore(today) && !eventDate.isAfter(targetDate)) {
                    System.out.println(" --- Match found! Sending email for: " + description);
                    //Function to send an email with the event description
                    sendEmail(description);
                    foundAny = true;
                }
            }
            fileScanner.close();

            if (!foundAny) {
                System.out.println("No events found for that date.");
            }

        } catch (Exception e) {
            System.out.println("An error occurred: " + e.getMessage());
        }
    }

    // This part handles the manual SMTP protocol conversation
    public static void sendEmail(String eventName) throws Exception {
        // Open a connection to the server on Port 25
        Socket socket = new Socket("localhost", 25);
        
        // Setup the 'Writer' to send text and 'Reader' to hear the server
        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

        // BufferedReader to read lines of text from the server
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

        // --- THE CONVERSATION ---
        
        // Listen to the server's welcome
        System.out.println("Server: " + in.readLine());

        // Say Hello
        out.println("HELO cos332.local");
        System.out.println("Server: " + in.readLine());

        // Tell them who is sending it
        out.println("MAIL FROM:<reminder@cos332.local>");
        System.out.println("Server: " + in.readLine());

        // Tell them who should receive it
        out.println("RCPT TO:<root@cos332.local>");
        System.out.println("Server: " + in.readLine());

        // Tell them we are ready to send the message content
        out.println("DATA");
        System.out.println("Server: " + in.readLine());

        // Write the email headers and body
        out.println("Subject: Event Reminder");
        out.println("From: reminder@cos332.local");
        out.println("To: root@cos332.local");
        out.println(); // Blank line is required here!
        out.println("Reminder: " + eventName + " is happening in 6 days.");
        
        // End the message with a single period
        out.println(".");
        System.out.println("Server: " + in.readLine());

        // Say goodbye
        out.println("QUIT");
        System.out.println("Server: " + in.readLine());

        // Close everything
        socket.close();
    }
}