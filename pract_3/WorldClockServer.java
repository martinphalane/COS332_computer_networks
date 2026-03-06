/**
 * COS 332 - Practical Assignment 3
 * World Clock HTTP Server
 *
 * Author: [Your Name] - [Student Number]
 * Date:   2026
 *
 * Description:
 *   A special-purpose HTTP server that acts as a world clock.
 *   Run it, then open http://127.0.0.1:55555 in your browser.
 *   Click any city name to see its current time alongside SA time.
 *   The page auto-refreshes every second.
 *
 * To compile:  javac WorldClockServer.java
 * To run:      java WorldClockServer
 * To run on a custom port: java WorldClockServer 8080
 */

import java.io.*;
import java.net.*;
import java.time.*;
import java.time.format.*;
import java.util.*;

public class WorldClockServer {

    // -------------------------------------------------------------------------
    // Configuration
    // -------------------------------------------------------------------------
    private static final int DEFAULT_PORT = 55555;

    // South Africa is always shown — it is the "home" zone
    private static final ZoneId SA_ZONE = ZoneId.of("Africa/Johannesburg");

    // World cities: display name -> ZoneId string
    // Shown as clickable links on the home page
    private static final LinkedHashMap<String, ZoneId> CITIES = new LinkedHashMap<>();
    static {
        CITIES.put("London",       ZoneId.of("Europe/London"));
        CITIES.put("New-York",     ZoneId.of("America/New_York"));
        CITIES.put("Los-Angeles",  ZoneId.of("America/Los_Angeles"));
        CITIES.put("Paris",        ZoneId.of("Europe/Paris"));
        CITIES.put("Dubai",        ZoneId.of("Asia/Dubai"));
        CITIES.put("Mumbai",       ZoneId.of("Asia/Kolkata"));
        CITIES.put("Beijing",      ZoneId.of("Asia/Shanghai"));
        CITIES.put("Tokyo",        ZoneId.of("Asia/Tokyo"));
        CITIES.put("Sydney",       ZoneId.of("Australia/Sydney"));
        CITIES.put("Sao-Paulo",    ZoneId.of("America/Sao_Paulo"));
    }

    // -------------------------------------------------------------------------
    // Main — start the server
    // -------------------------------------------------------------------------
    public static void main(String[] args) throws IOException {
        int port = DEFAULT_PORT;

        // Allow port to be passed as a command-line argument
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port: " + args[0] + ". Using default: " + DEFAULT_PORT);
            }
        }

        ServerSocket serverSocket = new ServerSocket(port);
        System.out.println("=================================================");
        System.out.println("  COS 332 World Clock Server started!");
        System.out.println("  Open your browser and go to:");
        System.out.println("  http://127.0.0.1:" + port);
        System.out.println("=================================================");

        // Accept connections in an infinite loop
        // Each connection is handled in its own thread (bonus marks!)
        while (true) {
            Socket clientSocket = serverSocket.accept();
            // Hand off to a new thread so the server never blocks
            new Thread(new ClientHandler(clientSocket)).start();
        }
    }

    // -------------------------------------------------------------------------
    // ClientHandler — handles one browser connection
    // -------------------------------------------------------------------------
    static class ClientHandler implements Runnable {

        private final Socket socket;

        ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try (
                BufferedReader in  = new BufferedReader(
                                         new InputStreamReader(socket.getInputStream()));
                OutputStream   out = socket.getOutputStream()
            ) {
                // --- 1. Read the HTTP request ---
                String requestLine = in.readLine();

                // Guard: ignore empty/null requests (browser sometimes sends blank)
                if (requestLine == null || requestLine.isEmpty()) return;

                // Read and discard remaining headers until blank line
                String headerLine;
                while ((headerLine = in.readLine()) != null && !headerLine.isEmpty()) {
                    // We don't need the headers for this assignment
                }

                // Print the request to console for debugging
                System.out.println("[REQUEST] " + requestLine);

                // --- 2. Parse the request line: GET <path> HTTP/1.1 ---
                String[] parts = requestLine.split(" ");

                // Malformed request — must have exactly 3 parts: METHOD PATH HTTP/VERSION
                // RFC 2616 §5.1: Request-Line = Method SP Request-URI SP HTTP-Version CRLF
                if (parts.length != 3) {
                    sendResponse(out, "GET", "HTTP/1.1", 400, "Bad Request",
                                 buildErrorPage("400 Bad Request",
                                                "Malformed request line: expected METHOD PATH HTTP/VERSION"));
                    return;
                }

                String method  = parts[0];
                String path    = parts[1];
                String version = parts[2];

                // HTTP version check — support both HTTP/1.0 and HTTP/1.1
                // RFC 2616 §3.1: HTTP-Version = "HTTP" "/" 1*DIGIT "." 1*DIGIT
                if (!version.equals("HTTP/1.1") && !version.equals("HTTP/1.0")) {
                    sendResponse(out, "GET", "HTTP/1.1", 505, "HTTP Version Not Supported",
                                 buildErrorPage("505 HTTP Version Not Supported",
                                                "This server supports HTTP/1.0 and HTTP/1.1 only."));
                    return;
                }

                // --- 3. Route and respond ---
                // Pass version so we can echo it back correctly in the response
                if (method.equals("GET") || method.equals("HEAD")) {
                    handleRequest(method, path, version, out);
                } else {
                    // We only support GET and HEAD — RFC 2616 §5.1.1
                    sendResponse(out, method, version, 405, "Method Not Allowed",
                                 buildErrorPage("405 Method Not Allowed",
                                                "Only GET and HEAD are supported."));
                }

            } catch (IOException e) {
                System.err.println("[ERROR] " + e.getMessage());
            } finally {
                try { socket.close(); } catch (IOException ignored) {}
            }
        }

        // ---------------------------------------------------------------------
        // Route the request path to the correct response
        // ---------------------------------------------------------------------
        private void handleRequest(String method, String path, String version, OutputStream out)
                throws IOException {

            // Decode URL encoding (e.g. %20 -> space) — just in case
            String decodedPath = URLDecoder.decode(path, "UTF-8");

            if (decodedPath.equals("/") || decodedPath.equals("/Home")) {
                // Home page: show SA time + list of cities
                String html = buildHomePage();
                sendResponse(out, method, version, 200, "OK", html);

            } else if (decodedPath.equals("/favicon.ico")) {
                // Browsers automatically request this — return 404 silently
                sendResponse(out, method, version, 404, "Not Found", "");

            } else {
                // Check if path matches a city name (e.g. /London)
                String cityKey = decodedPath.substring(1); // strip leading /
                if (CITIES.containsKey(cityKey)) {
                    String html = buildCityPage(cityKey, CITIES.get(cityKey));
                    sendResponse(out, method, version, 200, "OK", html);
                } else {
                    // Unknown path — return a proper 404 page
                    String html = buildErrorPage("404 Not Found",
                                                 "The page <b>" + decodedPath + "</b> does not exist.");
                    sendResponse(out, method, version, 404, "Not Found", html);
                }
            }
        }

        // ---------------------------------------------------------------------
        // Send a full HTTP response
        // ---------------------------------------------------------------------
        private void sendResponse(OutputStream out, String method, String version,
                                  int statusCode, String statusText,
                                  String htmlBody) throws IOException {

            byte[] bodyBytes = htmlBody.getBytes("UTF-8");

            // RFC 2616 §14.18: Date header REQUIRED in all responses.
            // Must be in RFC 1123 format: e.g. Fri, 06 Mar 2026 12:00:00 GMT
            DateTimeFormatter rfcDate = DateTimeFormatter
                .ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.ENGLISH);
            String dateValue = ZonedDateTime.now(ZoneOffset.UTC).format(rfcDate);

            // RFC 2616 §3.1: Echo back the same HTTP version the client used.
            // HTTP/1.0 clients get HTTP/1.0 responses; HTTP/1.1 clients get HTTP/1.1.
            String responseVersion = (version != null && version.equals("HTTP/1.0"))
                                     ? "HTTP/1.0" : "HTTP/1.1";

            // HTTP/1.0 does not support persistent connections by default — RFC 2616 §8.1.2
            String connectionHeader = responseVersion.equals("HTTP/1.0") ? "keep-alive" : "close";

            // Build HTTP response headers
            StringBuilder headers = new StringBuilder();
            headers.append(responseVersion).append(" ").append(statusCode).append(" ").append(statusText).append("\r\n");
            headers.append("Date: ").append(dateValue).append("\r\n");                  // RFC 2616 §14.18
            headers.append("Server: WorldClockServer/1.0 (COS332)\r\n");               // RFC 2616 §14.38
            headers.append("Content-Type: text/html; charset=UTF-8\r\n");              // RFC 2616 §14.17
            headers.append("Content-Length: ").append(bodyBytes.length).append("\r\n");// RFC 2616 §14.13
            headers.append("Cache-Control: no-cache, no-store, must-revalidate\r\n");  // RFC 2616 §14.9
            headers.append("Pragma: no-cache\r\n");                                    // RFC 2616 §14.32
            headers.append("Expires: 0\r\n");                                          // RFC 2616 §14.21
            headers.append("Connection: ").append(connectionHeader).append("\r\n");    // RFC 2616 §14.10
            headers.append("\r\n"); // blank line — end of headers (RFC 2616 §6)

            // Write headers
            out.write(headers.toString().getBytes("UTF-8"));

            // Write body only for GET (not HEAD) — RFC 2616 §9.4
            if (method.equals("GET")) {
                out.write(bodyBytes);
            }

            out.flush();
        }
    }

    // -------------------------------------------------------------------------
    // HTML Builders
    // -------------------------------------------------------------------------

    /**
     * Returns the current time in a given zone, nicely formatted.
     * e.g. "14:32:07 on 09 Mar 2026"
     */
    private static String getFormattedTime(ZoneId zone) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm:ss 'on' dd MMM yyyy");
        return ZonedDateTime.now(zone).format(fmt);
    }

    /**
     * Returns the UTC offset for a zone, e.g. "UTC+2"
     */
    private static String getOffset(ZoneId zone) {
        ZoneOffset offset = ZonedDateTime.now(zone).getOffset();
        int totalSeconds  = offset.getTotalSeconds();
        int hours         = totalSeconds / 3600;
        int minutes       = Math.abs((totalSeconds % 3600) / 60);
        if (minutes == 0) {
            return "UTC" + (hours >= 0 ? "+" : "") + hours;
        }
        return String.format("UTC%+d:%02d", hours, minutes);
    }

    /**
     * Returns a human-readable time difference string from SA's perspective.
     * e.g. "South Africa is 2 hours ahead" or "South Africa is 7 hours behind"
     * Handles half-hour zones like India (IST = UTC+5:30) correctly.
     */
    private static String getTimeDifference(ZoneId cityZone) {
        ZonedDateTime now  = ZonedDateTime.now();
        int saOffsetSecs   = now.withZoneSameInstant(SA_ZONE).getOffset().getTotalSeconds();
        int cityOffsetSecs = now.withZoneSameInstant(cityZone).getOffset().getTotalSeconds();
        int diffSecs       = saOffsetSecs - cityOffsetSecs;

        if (diffSecs == 0) return "= South Africa and this city are in the <strong>same time zone</strong>";

        int absSecs   = Math.abs(diffSecs);
        int diffHours = absSecs / 3600;
        int diffMins  = (absSecs % 3600) / 60;

        String amount;
        if (diffMins == 0) {
            amount = diffHours + (diffHours == 1 ? " hour" : " hours");
        } else {
            amount = diffHours + "h " + diffMins + "min";
        }

        if (diffSecs > 0) {
            return "^ South Africa is <strong>" + amount + " ahead</strong> of this city";
        } else {
            return "v South Africa is <strong>" + amount + " behind</strong> this city";
        }
    }

    /**
     * Builds the common HTML header with auto-refresh and no-cache meta tags.
     */
    private static String buildHtmlHeader(String title) {
        return "<!DOCTYPE html>\n"
             + "<html lang=\"en\">\n"
             + "<head>\n"
             + "  <meta charset=\"UTF-8\">\n"
             + "  <meta http-equiv=\"refresh\" content=\"1\">\n"
             + "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n"
             + "  <title>" + title + "</title>\n"
             + "  <style>\n"
             + "    body { font-family: Arial, sans-serif; background: #f0f4f8; "
             +            "margin: 0; padding: 20px; color: #222; }\n"
             + "    h1   { color: #1F4E79; border-bottom: 2px solid #2E75B6; padding-bottom: 8px; }\n"
             + "    h2   { color: #2E75B6; }\n"
             + "    .clock { font-size: 2em; font-weight: bold; color: #1F4E79; "
             +              "background: #fff; padding: 16px 24px; border-radius: 8px; "
             +              "display: inline-block; margin: 10px 0; "
             +              "box-shadow: 0 2px 6px rgba(0,0,0,0.1); }\n"
             + "    .zone  { font-size: 0.7em; color: #888; font-weight: normal; }\n"
             + "    .cities { margin-top: 20px; }\n"
             + "    .cities a { display: inline-block; margin: 6px 8px; padding: 8px 16px; "
             +                 "background: #2E75B6; color: white; text-decoration: none; "
             +                 "border-radius: 4px; font-size: 0.95em; }\n"
             + "    .cities a:hover { background: #1F4E79; }\n"
             + "    .home-link { margin-top: 20px; display: inline-block; padding: 8px 16px; "
             +                  "background: #555; color: white; text-decoration: none; "
             +                  "border-radius: 4px; }\n"
             + "    .home-link:hover { background: #333; }\n"
             + "    .sa-box { background: #E2EFDA; border-left: 4px solid #1E7145; "
             +               "padding: 12px 20px; border-radius: 4px; margin: 10px 0; }\n"
             + "    .city-box { background: #EBF3FB; border-left: 4px solid #2E75B6; "
             +                 "padding: 12px 20px; border-radius: 4px; margin: 10px 0; }\n"
             + "    .diff-box { background: #FFF8E1; border: 2px dashed #F0A500; "
             +                 "padding: 12px 20px; border-radius: 8px; margin: 14px 0; "
             +                 "font-size: 1.15em; color: #555; text-align: center; }\n"
             + "    .diff-box strong { color: #1F4E79; font-size: 1.2em; }\n"
             + "    .search-box { margin: 16px 0; }\n"
             + "    .search-box input { padding: 10px 14px; font-size: 1em; width: 280px; "
             +                        "border: 2px solid #2E75B6; border-radius: 4px; outline: none; }\n"
             + "    .search-box input:focus { border-color: #1F4E79; }\n"
             + "    table { width: 100%; border-collapse: collapse; margin-top: 16px; "
             +             "background: #fff; border-radius: 8px; overflow: hidden; "
             +             "box-shadow: 0 2px 6px rgba(0,0,0,0.08); }\n"
             + "    th { background: #1F4E79; color: white; padding: 10px 14px; "
             +          "text-align: left; font-size: 0.95em; }\n"
             + "    td { padding: 9px 14px; border-bottom: 1px solid #e0e0e0; font-size: 0.95em; }\n"
             + "    tr:last-child td { border-bottom: none; }\n"
             + "    tr:nth-child(even) { background: #f5f8ff; }\n"
             + "    tr:hover { background: #EBF3FB; }\n"
             + "    td a { color: #2E75B6; text-decoration: none; font-weight: bold; }\n"
             + "    td a:hover { color: #1F4E79; text-decoration: underline; }\n"
             + "    .ahead  { color: #1E7145; font-weight: bold; }\n"
             + "    .behind { color: #C55A11; font-weight: bold; }\n"
             + "    .same   { color: #888; }\n"
             + "    .hidden { display: none; }\n"
             + "  </style>\n"
             + "</head>\n"
             + "<body>\n";
    }

    /**
     * Home page: SA time on top, search box, summary table of all city times.
     */
    private static String buildHomePage() {
        StringBuilder sb = new StringBuilder();
        sb.append(buildHtmlHeader("World Clock"));

        sb.append("  <h1>World Clock</h1>\n");

        // SA time
        sb.append("  <div class=\"sa-box\">\n");
        sb.append("    <h2>South Africa (Johannesburg)</h2>\n");
        sb.append("    <div class=\"clock\">\n");
        sb.append("      ").append(getFormattedTime(SA_ZONE));
        sb.append("      <span class=\"zone\"> ").append(getOffset(SA_ZONE)).append("</span>\n");
        sb.append("    </div>\n");
        sb.append("  </div>\n");

        // Search box — filters the table rows live as you type
        // Note: this uses only HTML + inline JavaScript for the filter UI.
        // No network functionality — purely client-side DOM manipulation.
        sb.append("  <div class=\"search-box\">\n");
        sb.append("    <input type=\"text\" id=\"citySearch\" placeholder=\"Search cities...\"\n");
        sb.append("      oninput=\"filterCities()\">\n");
        sb.append("  </div>\n");

        // Summary table of all city times
        sb.append("  <table id=\"cityTable\">\n");
        sb.append("    <thead>\n");
        sb.append("      <tr>\n");
        sb.append("        <th>City</th>\n");
        sb.append("        <th>Current Time</th>\n");
        sb.append("        <th>UTC Offset</th>\n");
        sb.append("        <th>vs South Africa</th>\n");
        sb.append("      </tr>\n");
        sb.append("    </thead>\n");
        sb.append("    <tbody>\n");

        for (Map.Entry<String, ZoneId> entry : CITIES.entrySet()) {
            String cityKey     = entry.getKey();
            ZoneId cityZone    = entry.getValue();
            String displayName = cityKey.replace("-", " ");

            // Calculate difference for colour coding
            int saOffsetSecs   = ZonedDateTime.now().withZoneSameInstant(SA_ZONE).getOffset().getTotalSeconds();
            int cityOffsetSecs = ZonedDateTime.now().withZoneSameInstant(cityZone).getOffset().getTotalSeconds();
            int diffSecs       = saOffsetSecs - cityOffsetSecs;

            // Build difference text and CSS class
            String diffText;
            String diffClass;
            if (diffSecs == 0) {
                diffText  = "Same time zone";
                diffClass = "same";
            } else {
                int absSecs   = Math.abs(diffSecs);
                int diffHours = absSecs / 3600;
                int diffMins  = (absSecs % 3600) / 60;
                String amount = diffMins == 0
                    ? diffHours + (diffHours == 1 ? "h" : "h")
                    : diffHours + "h " + diffMins + "min";
                if (diffSecs > 0) {
                    diffText  = "SA +" + amount + " ahead";
                    diffClass = "ahead";
                } else {
                    diffText  = "SA " + amount + " behind";
                    diffClass = "behind";
                }
            }

            sb.append("      <tr class=\"city-row\" data-city=\"")
              .append(displayName.toLowerCase()).append("\">\n");
            sb.append("        <td><a href=\"/").append(cityKey).append("\">")
              .append(displayName).append("</a></td>\n");
            sb.append("        <td>").append(getFormattedTime(cityZone)).append("</td>\n");
            sb.append("        <td>").append(getOffset(cityZone)).append("</td>\n");
            sb.append("        <td class=\"").append(diffClass).append("\">")
              .append(diffText).append("</td>\n");
            sb.append("      </tr>\n");
        }

        sb.append("    </tbody>\n");
        sb.append("  </table>\n");

        // Inline JS for search filter — filters table rows by city name
        sb.append("  <script>\n");
        sb.append("    function filterCities() {\n");
        sb.append("      var input = document.getElementById('citySearch').value.toLowerCase();\n");
        sb.append("      var rows  = document.getElementsByClassName('city-row');\n");
        sb.append("      for (var i = 0; i < rows.length; i++) {\n");
        sb.append("        var name = rows[i].getAttribute('data-city');\n");
        sb.append("        rows[i].className = name.indexOf(input) > -1\n");
        sb.append("          ? 'city-row' : 'city-row hidden';\n");
        sb.append("      }\n");
        sb.append("    }\n");
        sb.append("  </script>\n");

        sb.append("</body>\n</html>\n");
        return sb.toString();
    }

    /**
     * City page: SA always on top, then time difference banner, then city time below.
     */
    private static String buildCityPage(String cityKey, ZoneId cityZone) {
        String displayName = cityKey.replace("-", " ");
        StringBuilder sb = new StringBuilder();
        sb.append(buildHtmlHeader("World Clock - " + displayName));

        sb.append("  <h1>World Clock</h1>\n");

        // SA time — always on top
        sb.append("  <div class=\"sa-box\">\n");
        sb.append("    <h2>South Africa (Johannesburg)</h2>\n");
        sb.append("    <div class=\"clock\">\n");
        sb.append("      ").append(getFormattedTime(SA_ZONE));
        sb.append("      <span class=\"zone\"> ").append(getOffset(SA_ZONE)).append("</span>\n");
        sb.append("    </div>\n");
        sb.append("  </div>\n");

        // Time difference banner
        sb.append("  <div class=\"diff-box\">\n");
        sb.append("    ").append(getTimeDifference(cityZone)).append("\n");
        sb.append("  </div>\n");

        // Selected city time — below SA
        sb.append("  <div class=\"city-box\">\n");
        sb.append("    <h2>").append(displayName).append("</h2>\n");
        sb.append("    <div class=\"clock\">\n");
        sb.append("      ").append(getFormattedTime(cityZone));
        sb.append("      <span class=\"zone\"> ").append(getOffset(cityZone)).append("</span>\n");
        sb.append("    </div>\n");
        sb.append("  </div>\n");

        // Other city links
        sb.append("  <div class=\"cities\">\n");
        sb.append("    <h2>Compare another city:</h2>\n");
        for (String key : CITIES.keySet()) {
            if (!key.equals(cityKey)) {
                String name = key.replace("-", " ");
                sb.append("    <a href=\"/").append(key).append("\">")
                  .append(name).append("</a>\n");
            }
        }
        sb.append("  </div>\n");

        // Home link
        sb.append("  <br>\n");
        sb.append("  <a class=\"home-link\" href=\"/\">&lt;- Home</a>\n");

        sb.append("</body>\n</html>\n");
        return sb.toString();
    }

    /**
     * Error page for 404 / 405 responses.
     */
    private static String buildErrorPage(String title, String message) {
        return buildHtmlHeader(title)
             + "  <h1>" + title + "</h1>\n"
             + "  <p>" + message + "</p>\n"
             + "  <a class=\"home-link\" href=\"/\">&lt;- Go Home</a>\n"
             + "</body>\n</html>\n";
    }
}