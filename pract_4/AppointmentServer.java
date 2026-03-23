// AppointmentServer.java
// COS332 Practical Assignment 4

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class AppointmentServer {

    // Change this single constant to move to a different port
    private static final int PORT = 55555;

    public static void main(String[] args) throws IOException {
        AppointmentStore store = new AppointmentStore();

        System.out.println("==========================================");
        System.out.println(" COS332 Appointment Server — Prac 4");
        System.out.println(" Port    : " + PORT);
        System.out.println(" Browser : http://127.0.0.1:" + PORT);
        System.out.println(" Photos  : ./photos/");
        System.out.println(" Data    : ./appointments.dat");
        System.out.println(" Stop    : Ctrl+C");
        System.out.println("==========================================");

        try (ServerSocket server = new ServerSocket(PORT)) {
            while (true) {
                Socket client = server.accept();
                // Each request runs in its own thread so multiple
                // browser tabs can be used simultaneously
                new Thread(new RequestHandler(client, store)).start();
            }
        }
    }
}
