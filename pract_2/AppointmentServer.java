import java.io.*;
import java.net.*;

/**
 * COS 332 - Practical Assignment 2
 * Telnet-based Appointment Server
 *
 * Usage: java AppointmentServer [port]
 * Default port: 2323
 *
 * Connect with: telnet <host> <port>
 */
public class AppointmentServer {

    public static void main(String[] args) {
        int port = 2323;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                // silently use default port
            }
        }

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                // Each client gets its own thread for simultaneous connections
                Thread clientThread = new Thread(new ClientHandler(clientSocket));
                clientThread.setDaemon(true);
                clientThread.start();
            }
        } catch (IOException e) {
            // server closed or failed to bind - no console output during demo
        }
    }
}
