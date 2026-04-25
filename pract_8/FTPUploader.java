import java.io.*;
import java.net.*;
import java.util.*;

public class FTPUploader {

    private FTPConnection conn;

    public FTPUploader(FTPConnection conn) {
        this.conn = conn;
    }

    public void upload(File localFile) throws IOException {
        String fileName = localFile.getName();
        
        // Tell server we are sending a "Binary" file (raw data)
        conn.sendCommand("TYPE I");
        conn.readReply();

        // Ask server for a "Data Port" (Passive Mode)
        conn.sendCommand("PASV");
        String pasvReply = conn.readReply();
        
        // Extract IP and Port from the server's message
        String ip = extractIP(pasvReply);
        int port = extractPort(pasvReply);

        // Tell server the filename and start the transfer
        conn.sendCommand("STOR " + Main.BACKUP_FOLDER + fileName);
        
        // Open a new connection just for the file data
        Socket dataSocket = new Socket(ip, port);
        OutputStream dataOut = dataSocket.getOutputStream();

        conn.readReply(); // Server says "150 Opening data connection"

        // Read the file from your PC and push it to the server
        FileInputStream fileIn = new FileInputStream(localFile);
        byte[] buffer = new byte[4096];
        int bytesRead;
        
        while ((bytesRead = fileIn.read(buffer)) != -1) {
            dataOut.write(buffer, 0, bytesRead);
        }
        
        fileIn.close();
        dataOut.close(); // Closing the data pipe tells the server we are finished
        dataSocket.close();

        conn.readReply(); // Server says "226 Transfer complete"
        System.out.println("SUCCESS: Uploaded " + fileName);
    }

    private String extractIP(String msg) {
        // Simple logic: find numbers between parentheses (1,2,3,4,5,6)
        String content = msg.substring(msg.indexOf('(') + 1, msg.indexOf(')'));
        String[] parts = content.split(",");
        return parts[0] + "." + parts[1] + "." + parts[2] + "." + parts[3];
    }

    private int extractPort(String msg) {
        String content = msg.substring(msg.indexOf('(') + 1, msg.indexOf(')'));
        String[] parts = content.split(",");
        return (Integer.parseInt(parts[4]) * 256) + Integer.parseInt(parts[5]);
    }
}