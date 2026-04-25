public class settings {

    // The IP address of your FTP server
    static String SERVER_IP = getLocalIP();
 
    // The port number for FTP (usually 21)
    static int    SERVER_PORT   = 21;
 
    // Your FTP username
    static String USERNAME    = "cos332";
 
    // Your FTP password
    static String PASSWORD   = "admin";
 
    // The folder on your computer to watch for changes
    static String WATCH_FOLDER  = "/mnt/c/Users/Mart/Documents/cos332";
 
    // The folder on the server where files will be saved
    static String BACKUP_FOLDER = "/backup/";
 
    // How often to check for changes (in seconds)
    static int    CHECK_EVERY   = 10;         // How often to check for changes (in seconds)
    // This method finds your machine's IP automatically


    
    private static String getLocalIP() {
        try {
            return java.net.InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            System.out.println("Could not get IP, using localhost instead.");
            return "127.0.0.1"; // fallback — works if server and client are on same machine
        }
    }
}
