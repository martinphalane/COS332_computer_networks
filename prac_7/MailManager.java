// These are our imports - they give us access to Java's built-in tools
import java.io.*;                // Allows us to read and write data
import java.net.*;               // Allows us to make network connections (POP3)
import java.util.ArrayList;      // Gives us the ArrayList class to store emails
import java.util.List;           // Gives us the List interface
// Note: We import ArrayList and List specifically instead of java.util.*
// This is because java.awt.* also has a List class and Java would get confused
import javax.swing.*;            // Gives us all the GUI components (windows, buttons, checkboxes)
import java.awt.*;               // Gives us layout managers and colors for the GUI
import java.awt.event.*;         // Allows us to listen for button clicks

// This is our main class for the Mail Manager program
// It extends JFrame which means our class IS a window
// JFrame is Java's built in window class from the Swing library
public class MailManager extends JFrame {

    // These are the server settings - same as Prac7
    private String host = "localhost";      // The server we connect to
    private int pop3Port = 110;             // POP3 always listens on port 110
    private String username = "testuser";   // Our test user
    private String password = "test123";    // Our test user password

    // These variables will hold our network connection objects
    // We keep them as class variables so all methods can use them
    private Socket pop3Socket;              // The connection to the POP3 server
    private BufferedReader reader;          // For reading server responses
    private PrintWriter writer;             // For sending commands to server

    // This list will store all the email information we retrieve
    // Each entry is an array containing [messageNumber, from, subject, size]
    private List<String[]> emails = new ArrayList<>();

    // This panel will hold all the email checkboxes
    // We keep it as a class variable so we can access it from different methods
    private JPanel emailPanel;

    // This label shows status messages to the user at the bottom of the window
    private JLabel statusLabel;

    // ===================== CONSTRUCTOR =====================
    // The constructor sets up our GUI window when the program starts
    // Think of it as the "setup" method that runs first
    // It is called automatically when we do "new MailManager()" in main
    public MailManager() {

        // Set the title of the window that appears in the title bar
        setTitle("Mail Manager - POP3 Client");

        // Set the size of the window (width x height in pixels)
        setSize(700, 500);

        // Make the program exit completely when the window is closed
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // Center the window in the middle of the screen
        setLocationRelativeTo(null);

        // Set a nice dark background color for the whole window
        // Color uses RGB values (Red, Green, Blue) from 0 to 255
        getContentPane().setBackground(new Color(30, 30, 40));

        // Use BorderLayout - this divides the window into 5 areas
        // NORTH (top), SOUTH (bottom), EAST (right), WEST (left), CENTER (middle)
        setLayout(new BorderLayout());

        // ===================== TOP PANEL =====================
        // This is the top section of the window with the title and refresh button
        JPanel topPanel = new JPanel();
        topPanel.setBackground(new Color(40, 40, 60)); // Dark blue background
        topPanel.setLayout(new BorderLayout());
        topPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10)); // Add padding

        // Create the title label at the top of the window
        JLabel titleLabel = new JLabel("  Mail Manager");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 20)); // Big bold font
        titleLabel.setForeground(new Color(100, 200, 255));   // Light blue color
        topPanel.add(titleLabel, BorderLayout.WEST);

        // Create a nice refresh button to reload emails from the server
        JButton refreshButton = new JButton("Refresh");
        refreshButton.setBackground(new Color(60, 120, 200)); // Blue background
        refreshButton.setForeground(Color.WHITE);              // White text
        refreshButton.setFont(new Font("Arial", Font.BOLD, 13));
        refreshButton.setBorder(BorderFactory.createEmptyBorder(8, 15, 8, 15));
        refreshButton.setFocusPainted(false); // Remove dotted border when clicked
        refreshButton.setCursor(new Cursor(Cursor.HAND_CURSOR)); // Hand cursor on hover

        // When refresh button is clicked call the loadEmails method
        // This is called an ActionListener - it listens for button clicks
        refreshButton.addActionListener(e -> loadEmails());
        topPanel.add(refreshButton, BorderLayout.EAST);

        // Add the top panel to the NORTH area of the window
        add(topPanel, BorderLayout.NORTH);

        // ===================== CENTER PANEL =====================
        // This is the middle section that shows the list of emails
        // We wrap it in a scroll pane so we can scroll if there are many emails
        emailPanel = new JPanel();
        emailPanel.setLayout(new BoxLayout(emailPanel, BoxLayout.Y_AXIS)); // Stack emails vertically
        emailPanel.setBackground(new Color(30, 30, 40)); // Dark background
        emailPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // JScrollPane adds a scrollbar automatically if the content is too long
        JScrollPane scrollPane = new JScrollPane(emailPanel);
        scrollPane.setBackground(new Color(30, 30, 40));
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getViewport().setBackground(new Color(30, 30, 40));

        // Add the scroll pane to the CENTER area of the window
        add(scrollPane, BorderLayout.CENTER);

        // ===================== BOTTOM PANEL =====================
        // This is the bottom section with the status label and delete button
        JPanel bottomPanel = new JPanel();
        bottomPanel.setBackground(new Color(40, 40, 60)); // Dark blue background
        bottomPanel.setLayout(new BorderLayout());
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Status label shows messages like "Connected" or "Deleted 2 emails"
        statusLabel = new JLabel("  Click Refresh to load emails");
        statusLabel.setForeground(new Color(150, 150, 150)); // Grey color
        statusLabel.setFont(new Font("Arial", Font.ITALIC, 12));
        bottomPanel.add(statusLabel, BorderLayout.WEST);

        // Create a nice red delete button at the bottom right
        JButton deleteButton = new JButton("Delete Selected");
        deleteButton.setBackground(new Color(200, 50, 50)); // Red background
        deleteButton.setForeground(Color.WHITE);             // White text
        deleteButton.setFont(new Font("Arial", Font.BOLD, 13));
        deleteButton.setBorder(BorderFactory.createEmptyBorder(8, 15, 8, 15));
        deleteButton.setFocusPainted(false);
        deleteButton.setCursor(new Cursor(Cursor.HAND_CURSOR));

        // When delete button is clicked call the deleteSelected method
        deleteButton.addActionListener(e -> deleteSelected());
        bottomPanel.add(deleteButton, BorderLayout.EAST);

        // Add the bottom panel to the SOUTH area of the window
        add(bottomPanel, BorderLayout.SOUTH);

        // Make the window visible on screen
        setVisible(true);

        // Automatically load emails when the program starts
        loadEmails();

    } // end of constructor

    // ===================== LOAD EMAILS METHOD =====================
    // This method connects to the POP3 server and retrieves the list of emails
    // PURPOSE: Show the user what emails are waiting in their mailbox
    // It uses the TOP command to get headers WITHOUT downloading full email bodies
    // This is more efficient and demonstrates knowledge of the POP3 RFC
    private void loadEmails() {

        // Update the status label to show we are connecting
        statusLabel.setText("  Connecting to POP3 server...");
        statusLabel.setForeground(new Color(255, 200, 50)); // Yellow color

        // Clear the previous list of emails before loading new ones
        emails.clear();
        emailPanel.removeAll(); // Remove all checkboxes from the panel

        try {
            // ===================== CONNECT TO POP3 =====================
            // Create a socket connection to the POP3 server on port 110
            // This is exactly like making a phone call to the server
            pop3Socket = new Socket(host, pop3Port);

            // Set up reader to receive data from the server
            reader = new BufferedReader(new InputStreamReader(pop3Socket.getInputStream()));

            // Set up writer to send commands to the server
            writer = new PrintWriter(new OutputStreamWriter(pop3Socket.getOutputStream()), true);

            // Read the greeting message the server sends when we connect
            String response = reader.readLine();
            System.out.println("Connected: " + response);

            // ===================== LOGIN =====================
            // Send our username using the POP3 USER command
            writer.println("USER " + username);
            reader.readLine(); // Read the +OK response from server

            // Send our password using the POP3 PASS command
            writer.println("PASS " + password);
            reader.readLine(); // Read the +OK response from server

            // ===================== LIST EMAILS =====================
            // The LIST command asks the server for a list of all emails and their sizes
            // This is important - we get sizes WITHOUT downloading the full emails
            // This satisfies the requirement of not downloading messages we dont want
            writer.println("LIST");
            response = reader.readLine(); // Read "+OK X messages"
            System.out.println("LIST response: " + response);

            // Read each line of the list until we reach "." which means end of list
            // Each line looks like "1 512" meaning message 1 is 512 bytes in size
            String line;
            List<String[]> tempList = new ArrayList<>();
            while (!(line = reader.readLine()).equals(".")) {
                // Split the line into message number and size
                String[] parts = line.trim().split(" ");
                String msgNum = parts[0];  // The message number (1, 2, 3 etc)
                String size = parts[1];    // The size in bytes
                tempList.add(new String[]{msgNum, "", "", size}); // Store temporarily
            }

            // ===================== GET EMAIL HEADERS ONLY =====================
            // We use the TOP command to get just the headers of each email
            // TOP X 0 means "give me headers of message X with 0 lines of body"
            // This is MUCH better than RETR because it doesnt download the full body
            // This demonstrates understanding of the POP3 RFC (RFC 1939)
            for (String[] emailInfo : tempList) {
                writer.println("TOP " + emailInfo[0] + " 0");
                reader.readLine(); // Read +OK response

                String from = "";
                String subject = "";

                // Read the headers line by line until we reach "."
                while (!(line = reader.readLine()).equals(".")) {
                    // Extract the From address from the header
                    if (line.startsWith("From:")) {
                        from = line.substring(5).trim();
                    }
                    // Extract the Subject from the header
                    if (line.startsWith("Subject:")) {
                        subject = line.substring(8).trim();
                    }
                }

                // Store the complete email info in our list
                // Format: [messageNumber, from, subject, size]
                emails.add(new String[]{emailInfo[0], from, subject, emailInfo[3]});
            }

            // ===================== DISPLAY EMAILS IN GUI =====================
            // Now create a nice looking row for each email in the GUI
            // Each row has a checkbox, sender/subject info, and size
            for (String[] emailInfo : emails) {

                // Create a panel for this email row
                JPanel rowPanel = new JPanel();
                rowPanel.setLayout(new BorderLayout());
                rowPanel.setBackground(new Color(45, 45, 60)); // Slightly lighter background
                rowPanel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createEmptyBorder(3, 0, 3, 0),
                    BorderFactory.createLineBorder(new Color(60, 60, 80), 1)));
                rowPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));

                // Create a checkbox on the left for selecting this email for deletion
                // The user ticks this box to mark an email for deletion
                JCheckBox checkBox = new JCheckBox();
                checkBox.setBackground(new Color(45, 45, 60));
                checkBox.setForeground(Color.WHITE);
                checkBox.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
                rowPanel.add(checkBox, BorderLayout.WEST);

                // Create a label showing the sender and subject of the email
                // We use HTML formatting to make it look nice with colors
                JLabel emailLabel = new JLabel(
                    "<html><b style='color:#64C8FF'>" + emailInfo[1] + "</b>" +
                    "<br><span style='color:#CCCCCC'>" + emailInfo[2] + "</span></html>");
                emailLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
                rowPanel.add(emailLabel, BorderLayout.CENTER);

                // Create a label showing the size of the email on the right side
                JLabel sizeLabel = new JLabel(emailInfo[3] + " bytes  ");
                sizeLabel.setForeground(new Color(150, 150, 150)); // Grey color
                sizeLabel.setFont(new Font("Arial", Font.ITALIC, 11));
                rowPanel.add(sizeLabel, BorderLayout.EAST);

                // Store the message number in the checkbox
                // We use this later to know WHICH email to delete
                checkBox.setActionCommand(emailInfo[0]);

                // Add this email row to the email panel
                emailPanel.add(rowPanel);
            }

            // Update status label to show how many emails were found
            statusLabel.setText("  Found " + emails.size() + " emails");
            statusLabel.setForeground(new Color(100, 200, 100)); // Green color

            // Tell Java to refresh the panel so the new emails appear on screen
            emailPanel.revalidate();
            emailPanel.repaint();

        } catch (Exception e) {
            // If anything goes wrong show the error in the status label
            statusLabel.setText("  Error: " + e.getMessage());
            statusLabel.setForeground(new Color(255, 80, 80)); // Red color
            System.out.println("Error loading emails: " + e.getMessage());
            e.printStackTrace();
        }

    } // end of loadEmails method

    // ===================== DELETE SELECTED METHOD =====================
    // This method deletes all emails that the user has ticked/checked
    // PURPOSE: Remove unwanted emails from the server without downloading them
    // It uses the POP3 DELE command to mark emails for deletion
    // IMPORTANT: Emails are only permanently deleted when we send QUIT
    // This is how POP3 works according to RFC 1939
    private void deleteSelected() {

        // Count how many emails we are going to delete
        int deleteCount = 0;

        try {
            // Go through each row in the email panel
            // Each row is a JPanel containing a checkbox
            for (Component comp : emailPanel.getComponents()) {
                if (comp instanceof JPanel) {
                    JPanel rowPanel = (JPanel) comp;

                    // Find the checkbox inside this row
                    for (Component inner : rowPanel.getComponents()) {
                        if (inner instanceof JCheckBox) {
                            JCheckBox checkBox = (JCheckBox) inner;

                            // If the checkbox is ticked send the DELE command
                            if (checkBox.isSelected()) {
                                // Get the message number we stored in the checkbox
                                String msgNum = checkBox.getActionCommand();

                                // DELE command tells the server to mark this email for deletion
                                // The email is NOT actually deleted until we send QUIT
                                // This is defined in RFC 1939 - the POP3 standard
                                writer.println("DELE " + msgNum);
                                String response = reader.readLine();
                                System.out.println("DELE " + msgNum + ": " + response);
                                deleteCount++;
                            }
                        }
                    }
                }
            }

            // ===================== QUIT TO CONFIRM DELETIONS =====================
            // Sending QUIT tells the server to permanently delete all DELE marked emails
            // If we had sent RSET instead of QUIT it would UNDO all the deletions
            // This two step process (DELE then QUIT) is how POP3 works by design
            writer.println("QUIT");
            reader.readLine(); // Read the goodbye message from server
            pop3Socket.close(); // Close the connection cleanly

            // Update status to show the result of the deletion
            if (deleteCount > 0) {
                statusLabel.setText("  Deleted " + deleteCount + " email(s). Click Refresh to update.");
                statusLabel.setForeground(new Color(255, 150, 50)); // Orange color
            } else {
                statusLabel.setText("  No emails selected. Tick a checkbox first!");
                statusLabel.setForeground(new Color(150, 150, 150)); // Grey color
            }

        } catch (Exception e) {
            // If anything goes wrong show the error
            statusLabel.setText("  Error deleting: " + e.getMessage());
            statusLabel.setForeground(new Color(255, 80, 80)); // Red color
            System.out.println("Error deleting: " + e.getMessage());
        }

    } // end of deleteSelected method

    // ===================== MAIN METHOD =====================
    // This is where Java starts running the MailManager program
    // PURPOSE: Create and display the GUI window
    public static void main(String[] args) {
        // SwingUtilities.invokeLater makes sure the GUI is created on the
        // correct thread - this is the proper way to start a Swing application
        SwingUtilities.invokeLater(() -> new MailManager());
    }

} // end of class MailManager
