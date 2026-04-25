import java.io.*;
import java.net.*;

public class FTPConnection {
    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;

    // Connect to the FTP server
    public void connect() throws IOException {
        System.out.println("STATUS: Connecting to " + Main.SERVER_IP + "...");
        socket = new Socket(Main.SERVER_IP, Main.SERVER_PORT);
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        writer = new PrintWriter(socket.getOutputStream(), true);
        readReply(); 
    }

    // Login to the FTP server
    public void login() throws IOException {
        sendCommand("USER " + Main.USERNAME);
        readReply();
        sendCommand("PASS " + Main.PASSWORD);
        readReply();
    }

    // Send a command to the server
    public void sendCommand(String command) {
        System.out.println("SENT: " + command); // Visible debugging
        writer.println(command);
    }

    // Read the server's reply
    public String readReply() throws IOException {
        String line;
        String fullReply = "";
        while ((line = reader.readLine()) != null) {
            System.out.println("RECEIVED: " + line); // Visible debugging
            fullReply = line;
            // FTP signals the end of a message with a space after the code (e.g., "226 ")
            if (line.length() >= 4 && line.charAt(3) == ' ') break;
        }
        return fullReply;
    }

    // Disconnect from the server
    public void disconnect() throws IOException {
        sendCommand("QUIT");
        readReply();
        socket.close();
        System.out.println("STATUS: Connection closed.");
    }
}