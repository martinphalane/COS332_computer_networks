// AppointmentServer.java
// COS332 Practical Assignment 4

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class AppointmentServer {

    // Change port here only
    private static final int PORT = 55555;

    public static void main(String[] args) throws IOException {
        AppointmentStore store = new AppointmentStore();

        System.out.println("====================================");
        System.out.println(" COS332 Appointment Server");
        System.out.println(" Port    : " + PORT);
        System.out.println(" Browser : http://127.0.0.1:" + PORT);
        System.out.println(" Stop    : Ctrl+C");
        System.out.println("====================================");
        System.out.println(" Photos stored in: ./photos/");
        System.out.println("====================================");

        try (ServerSocket server = new ServerSocket(PORT)) {
            while (true) {
                Socket client = server.accept();
                new Thread(new RequestHandler(client, store)).start();
            }
        }
    }
}
