import java.net.*;
import java.io.*;

public class SMTPProxy {

    static final int PROXY_PORT = 55555;
    static final String SMTP_HOST = "172.24.110.220"; // WSL IP
    static final int SMTP_PORT = 25;

    // Word replacement method
    static String replaceWords(String line) {
        line = line.replaceAll("(?i)\\bvery bad\\b", "plusungood");
        line = line.replaceAll("(?i)\\bvery good\\b", "plusgood");
        line = line.replaceAll("(?i)\\bvery fast\\b", "plusfast");
        line = line.replaceAll("(?i)\\bwarm\\b", "uncold");
        line = line.replaceAll("(?i)\\bbad\\b", "ungood");
        line = line.replaceAll("(?i)\\bfast\\b", "speedful");
        line = line.replaceAll("(?i)\\brapid\\b", "speedful");
        line = line.replaceAll("(?i)\\bquick\\b", "speedful");
        line = line.replaceAll("(?i)\\bslow\\b", "unspeedful");
        line = line.replaceAll("(?i)\\bran\\b", "runned");
        line = line.replaceAll("(?i)\\bstole\\b", "stealed");
        line = line.replaceAll("(?i)\\bbetter\\b", "gooder");
        line = line.replaceAll("(?i)\\bbest\\b", "goodest");
        return line;
    }

    // This method reads ALL lines of a server reply and passes them to client
    // SMTP replies that continue have a dash after the code e.g "250-Hello"
    // The last line has a space e.g "250 Hello" - no dash!
    static String readAndRelayServerReply(BufferedReader fromServer, PrintWriter toClient) throws IOException {
        String line;
        String lastLine = "";
        while ((line = fromServer.readLine()) != null) {
            System.out.println("Server: " + line);
            toClient.println(line); // Pass every line to client
            lastLine = line;
            // If 4th character is a space, this is the LAST line of the reply
            if (line.length() >= 4 && line.charAt(3) == ' ') {
                break; // Stop reading - reply is complete!
            }
        }
        return lastLine; // Return the last line
    }

    public static void main(String[] args) throws IOException {

        // Open our front door on port 55555
        ServerSocket serverSocket = new ServerSocket(PROXY_PORT);
        System.out.println("Proxy is listening on port " + PROXY_PORT);

        // Wait for email client to knock
        Socket clientSocket = serverSocket.accept();
        System.out.println("Email client connected!");

        // Connect to real email server
        Socket smtpSocket = new Socket(SMTP_HOST, SMTP_PORT);
        System.out.println("Connected to real email server!");

        // Ears (readers)
        BufferedReader fromClient = new BufferedReader(
            new InputStreamReader(clientSocket.getInputStream()));
        BufferedReader fromServer = new BufferedReader(
            new InputStreamReader(smtpSocket.getInputStream()));

        // Mouth (writers)
        PrintWriter toServer = new PrintWriter(
            smtpSocket.getOutputStream(), true);
        PrintWriter toClient = new PrintWriter(
            clientSocket.getOutputStream(), true);

        // Read server greeting and pass to client
        readAndRelayServerReply(fromServer, toClient);

        boolean inData = false;        // Are we inside email body?
        boolean illuminatiFound = false; // Was Illuminati found?
        StringBuilder emailBody = new StringBuilder(); // Store email body

        // Keep looping until connection closes
        while (true) {
            String line = fromClient.readLine();
            if (line == null) break; // Client disconnected
            System.out.println("Client: " + line);

            // Check if client sent DATA command
            if (line.equalsIgnoreCase("DATA")) {
                inData = true;
                illuminatiFound = false;
                emailBody = new StringBuilder();
                toServer.println(line);
                readAndRelayServerReply(fromServer, toClient);
                continue;
            }

            // If inside email body
            if (inData) {

                // Check for Illuminati
                if (line.toLowerCase().contains("illuminati")) {
                    illuminatiFound = true;
                }

                // Check for end of email (single dot)
                if (line.equals(".")) {
                    if (illuminatiFound) {
                        // Replace whole email with Hello world
                        toServer.println("Hello world");
                        System.out.println("Illuminati detected! Replaced!");
                    } else {
                        // Add disclaimer then end
                        toServer.println("Please do not take anything in this email seriously!");
                        System.out.println("Disclaimer added!");
                    }
                    toServer.println("."); // End the email
                    inData = false;
                    readAndRelayServerReply(fromServer, toClient);
                    continue;
                }

                // Replace bad words and send to server
                line = replaceWords(line);
                toServer.println(line);

            } else {
                // Not in email body - pass through and relay reply
                toServer.println(line);
                readAndRelayServerReply(fromServer, toClient);
            }
        }

        System.out.println("Connection closed!");
        clientSocket.close();
        smtpSocket.close();
        serverSocket.close();
    }
}