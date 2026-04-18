// These are our imports - they give us access to Java's built-in tools
import java.io.*;    // Allows us to read and write data
import java.net.*;   // Allows us to make network connections (POP3 and SMTP)
import java.util.*; // Gives us access to HashSet to track who we replied to

// This is our main class - in Java everything must be inside a class
// The class name must match the file name (Prac7)
public class Prac7 {

    // This is the main method - this is where Java starts running our program
    public static void main(String[] args) {

        // These are the settings for our mail server
        // We use localhost because our mail server is on the same computer
        String host = "localhost";      // The server we are connecting to
        int pop3Port = 110;             // POP3 always listens on port 110
        int smtpPort = 25;              // SMTP always listens on port 25
        String username = "testuser";   // Our test user (root is blocked by Dovecot)
        String password = "test123";    // Password we set for testuser

        // This Set will remember who we have already replied to
        // This prevents us from sending multiple replies to the same person
        // A HashSet automatically ignores duplicates - perfect for our use
        Set<String> repliedTo = new HashSet<>();

        // We wrap everything in a try-catch block
        // This means if anything goes wrong Java wont crash - it will show us the error
        try {

            // ===================== POP3 CONNECTION =====================
            // A Socket is like a "phone line" between our program and the server
            // We connect to the server using the host and port number
            Socket pop3Socket = new Socket(host, pop3Port);

            // This lets us READ what the server sends us line by line
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(pop3Socket.getInputStream()));

            // This lets us WRITE/SEND commands to the server
            PrintWriter writer = new PrintWriter(
                new OutputStreamWriter(pop3Socket.getOutputStream()), true);

            // Read the greeting message from the server
            // The POP3 server always says hello first when we connect
            // A good response starts with "+OK"
            String response = reader.readLine();
            System.out.println("Connected to POP3: " + response);

            // ===================== LOGIN =====================
            // Send our username to the server - this is the POP3 USER command
            writer.println("USER " + username);
            System.out.println("Sent: USER " + username);
            System.out.println("Server: " + reader.readLine());

            // Send our password to the server - this is the POP3 PASS command
            writer.println("PASS " + password);
            System.out.println("Sent: PASS");
            System.out.println("Server: " + reader.readLine());

            // ===================== LIST EMAILS =====================
            // The LIST command asks the server how many emails are in the mailbox
            // The server replies with the number of messages and their sizes
            writer.println("LIST");
            System.out.println("Sent: LIST");

            // Read the first line of the LIST response
            // It looks like "+OK 2 messages" meaning there are 2 emails
            response = reader.readLine();
            System.out.println("Server: " + response);

            // Now we read each email one by one
            // We keep reading until the server sends a "." which means end of list
            List<Integer> messageNumbers = new ArrayList<>();
            String line;
            while (!(line = reader.readLine()).equals(".")) {
                // Each line looks like "1 512" meaning message 1 is 512 bytes
                // We split the line and get the message number (first part)
                int msgNum = Integer.parseInt(line.trim().split(" ")[0]);
                messageNumbers.add(msgNum); // Add message number to our list
            }

            System.out.println("Found " + messageNumbers.size() + " emails");

            // ===================== READ EACH EMAIL =====================
            // Now we go through each email one by one
            for (int msgNum : messageNumbers) {

                // The RETR command retrieves/downloads a specific email
                // We are NOT deleting it - just reading it (as the prac requires)
                writer.println("RETR " + msgNum);
                System.out.println("\nRetrieving message " + msgNum);
                reader.readLine(); // Read the +OK response line

                // These variables will store the email details we find
                String from = "";       // Who sent the email
                String subject = "";    // The subject of the email

                // Read the email headers line by line
                // Headers are at the top of every email (From, Subject, Date etc)
                // Headers end when we reach a blank line
                while (!(line = reader.readLine()).isEmpty()) {
                    // Check if this line contains the FROM address
                    if (line.startsWith("From:")) {
                        from = line.substring(5).trim(); // Get everything after "From:"
                    }
                    // Check if this line contains the SUBJECT
                    if (line.startsWith("Subject:")) {
                        subject = line.substring(8).trim(); // Get everything after "Subject:"
                    }
                }

                // Read and discard the rest of the email body
                // We only care about the headers for this program
                while (!(line = reader.readLine()).equals(".")) {
                    // Just reading through the body without doing anything with it
                }

                System.out.println("From: " + from);
                System.out.println("Subject: " + subject);

                // ===================== CHECK AND REPLY =====================
                // The prac says we must ONLY respond to emails with subject "prac7"
                // We also check if we have already replied to this person
                // If we already replied we skip them to avoid reply loops
                if (subject.equalsIgnoreCase("prac7") && !repliedTo.contains(from)) {

                    // Send a vacation reply using SMTP
                    // We call our sendVacationReply method (defined below)
                    sendVacationReply(host, smtpPort, from);

                    // Add this person to our repliedTo set
                    // This makes sure we never reply to them again
                    repliedTo.add(from);
                    System.out.println("Replied to: " + from);

                } else if (repliedTo.contains(from)) {
                    System.out.println("Already replied to: " + from + " - skipping");
                } else {
                    System.out.println("Subject is not prac7 - skipping");
                }
            }

            // ===================== DISCONNECT =====================
            // The QUIT command politely closes our connection to the POP3 server
            // Importantly - QUIT without DELE means NO emails are deleted
            writer.println("QUIT");
            System.out.println("\nSent: QUIT");
            System.out.println("Server: " + reader.readLine());

            // Close the socket - like hanging up the phone
            pop3Socket.close();
            System.out.println("Disconnected from POP3 server");

        } catch (Exception e) {
            // If anything goes wrong print the error so we can see what happened
            System.out.println("Something went wrong: " + e.getMessage());
            e.printStackTrace(); // This prints the full details of the error
        }

    } // end of main method

    // ===================== SMTP VACATION REPLY METHOD =====================
    // This is a separate method that handles sending the vacation reply
    // We separated it from main to keep our code neat and organised
    // Parameters: host = server, smtpPort = port 25, recipient = who to reply to
    static void sendVacationReply(String host, int smtpPort, String recipient) {

        try {
            // Connect to the SMTP server using a socket - same idea as POP3
            Socket smtpSocket = new Socket(host, smtpPort);

            // Set up reading and writing - same as we did for POP3
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(smtpSocket.getInputStream()));
            PrintWriter writer = new PrintWriter(
                new OutputStreamWriter(smtpSocket.getOutputStream()), true);

            // Read the SMTP greeting - server says hello first
            System.out.println("SMTP: " + reader.readLine());

            // HELO command - we introduce ourselves to the SMTP server
            // This is required by RFC 821 before sending any email
            writer.println("HELO localhost");
            System.out.println("SMTP: " + reader.readLine());

            // MAIL FROM - we tell the server who is sending the email
            writer.println("MAIL FROM:<testuser@localhost>");
            System.out.println("SMTP: " + reader.readLine());

            // RCPT TO - we tell the server who to deliver the email to
            writer.println("RCPT TO:<" + recipient + ">");
            System.out.println("SMTP: " + reader.readLine());

            // DATA command - we tell the server we are about to send the email content
            writer.println("DATA");
            System.out.println("SMTP: " + reader.readLine());

            // Now we write the actual email content
            // These are the email headers as described in RFC 561
            writer.println("From: testuser@localhost");        // Who sent it
            writer.println("To: " + recipient);               // Who receives it
            writer.println("Subject: Re: prac7 - Out of Office"); // Subject line
            writer.println("Date: " + new Date().toString()); // Current date and time
            writer.println();                                  // Blank line separates headers from body

            // This is the body of the vacation email
            writer.println("Hello,");
            writer.println();
            writer.println("Thank you for your email.");
            writer.println("I am currently away on vacation and will not be able to respond immediately.");
            writer.println("I will get back to you as soon as I return.");
            writer.println();
            writer.println("Kind regards,");
            writer.println("Testuser");
            writer.println();

            // A single dot on its own line tells SMTP the email content is finished
            // This is defined in RFC 821 - it is how SMTP knows the message is done
            writer.println(".");
            System.out.println("SMTP: " + reader.readLine());

            // QUIT command - politely disconnect from the SMTP server
            writer.println("QUIT");
            System.out.println("SMTP: " + reader.readLine());

            // Close the connection
            smtpSocket.close();
            System.out.println("Vacation reply sent to: " + recipient);

        } catch (Exception e) {
            System.out.println("Failed to send reply: " + e.getMessage());
        }

    } // end of sendVacationReply method

} // end of class Prac7