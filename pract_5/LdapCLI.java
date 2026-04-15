import java.io.*;
import java.net.*;
import java.util.*;

/**
 * COS332 Practical Assignment 5
 * Martin Airways — Interactive CLI Fleet Manager
 * Student: u26535272
 *
 * Connects to OpenLDAP on port 389 using raw sockets.
 * All LDAP messages are hand-crafted BER/ASN.1 — no LDAP libraries.
 *
 * RFC references demonstrated:
 *   RFC 4511 §4.2  — BindRequest / BindResponse
 *   RFC 4511 §4.5  — SearchRequest with equalityMatch and greaterOrEqual filters
 *   RFC 4511 §4.6  — ModifyRequest (replace attribute value)
 *   RFC 4511 §4.7  — AddRequest
 *   RFC 4511 §4.8  — DeleteRequest
 *   RFC 4511 §4.1.9 — ResultCode enumeration (full table)
 *   RFC 4512 §2    — Directory Information Tree / DN structure
 *   X.690          — BER encoding (TLV, definite length, constructed types)
 *
 * Features:
 *   1  list    — list all planes, sorted by speed (fastest first)
 *   2  search  — look up a plane, shows full DN (RFC 4512)
 *   3  add     — add plane with duplicate check
 *   4  delete  — remove plane with confirmation
 *   5  modify  — update max speed (ModifyRequest RFC 4511 §4.6)
 *   6  fast    — search by minimum speed (greaterOrEqual filter)
 *   7  ber     — toggle BER sniff mode (show raw bytes sent/received)
 *   8  quit
 *
 * Usage:
 *   javac LdapCLI.java
 *   java LdapCLI
 */
public class LdapCLI {

    // ---------------------------------------------------------------
    // Configuration
    // ---------------------------------------------------------------
    private static final String LDAP_HOST  = "martinairways.com";
    private static final int    LDAP_PORT  = 389;
    private static final String BASE_DN    = "ou=Planes,dc=martinairways,dc=com";
    private static final String DN_SUFFIX  = "dc=martinairways,dc=com";
    private static final String SPEED_ATTR = "description";

    // Credentials entered at login
    private static String  sessionDN   = "";
    private static String  sessionPass = "";

    // BER sniff mode — prints raw bytes when enabled
    private static boolean berSniff    = false;
    // ---------------------------------------------------------------

    // ANSI colours
    private static final String RESET  = "\u001B[0m";
    private static final String BOLD   = "\u001B[1m";
    private static final String CYAN   = "\u001B[36m";
    private static final String YELLOW = "\u001B[33m";
    private static final String GREEN  = "\u001B[32m";
    private static final String RED    = "\u001B[31m";
    private static final String DIM    = "\u001B[2m";
    private static final String PURPLE = "\u001B[35m";

    private static int msgID = 1;

    // ================================================================
    //  RFC 4511 §4.1.9 — ResultCode table (full)
    // ================================================================
    private static final Map<Integer,String> RESULT_CODES = new LinkedHashMap<>();
    static {
        RESULT_CODES.put(0,  "success");
        RESULT_CODES.put(1,  "operationsError");
        RESULT_CODES.put(2,  "protocolError");
        RESULT_CODES.put(3,  "timeLimitExceeded");
        RESULT_CODES.put(4,  "sizeLimitExceeded");
        RESULT_CODES.put(5,  "compareFalse");
        RESULT_CODES.put(6,  "compareTrue");
        RESULT_CODES.put(7,  "authMethodNotSupported");
        RESULT_CODES.put(8,  "strongerAuthRequired");
        RESULT_CODES.put(10, "referral");
        RESULT_CODES.put(11, "adminLimitExceeded");
        RESULT_CODES.put(12, "unavailableCriticalExtension");
        RESULT_CODES.put(13, "confidentialityRequired");
        RESULT_CODES.put(14, "saslBindInProgress");
        RESULT_CODES.put(16, "noSuchAttribute");
        RESULT_CODES.put(17, "undefinedAttributeType");
        RESULT_CODES.put(18, "inappropriateMatching");
        RESULT_CODES.put(19, "constraintViolation");
        RESULT_CODES.put(20, "attributeOrValueExists");
        RESULT_CODES.put(21, "invalidAttributeSyntax");
        RESULT_CODES.put(32, "noSuchObject");
        RESULT_CODES.put(33, "aliasProblem");
        RESULT_CODES.put(34, "invalidDNSyntax");
        RESULT_CODES.put(36, "aliasDereferencingProblem");
        RESULT_CODES.put(48, "inappropriateAuthentication");
        RESULT_CODES.put(49, "invalidCredentials");
        RESULT_CODES.put(50, "insufficientAccessRights");
        RESULT_CODES.put(51, "busy");
        RESULT_CODES.put(52, "unavailable");
        RESULT_CODES.put(53, "unwillingToPerform");
        RESULT_CODES.put(54, "loopDetect");
        RESULT_CODES.put(64, "namingViolation");
        RESULT_CODES.put(65, "objectClassViolation");
        RESULT_CODES.put(66, "notAllowedOnNonLeaf");
        RESULT_CODES.put(67, "notAllowedOnRDN");
        RESULT_CODES.put(68, "entryAlreadyExists");
        RESULT_CODES.put(69, "objectClassModsProhibited");
        RESULT_CODES.put(71, "affectsMultipleDSAs");
        RESULT_CODES.put(80, "other");
    }

    static String resultCodeName(int rc) {
        return RESULT_CODES.getOrDefault(rc, "unknown(" + rc + ")");
    }

    // ================================================================
    //  main
    // ================================================================
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        printBanner();

        // Login loop
        while (true) {
            System.out.println(DIM + "  ─────────────────────────────────────" + RESET);
            System.out.println(BOLD + "  LDAP Login  " + DIM + "(RFC 4511 §4.2 BindRequest)" + RESET);
            System.out.println(DIM + "  Server : " + LDAP_HOST + ":" + LDAP_PORT + RESET);
            System.out.println(DIM + "  BaseDN : " + BASE_DN + RESET);
            System.out.println(DIM + "  ─────────────────────────────────────" + RESET);

            System.out.print("  " + CYAN + "Username : " + RESET);
            String user = sc.nextLine().trim();
            System.out.print("  " + CYAN + "Password : " + RESET);
            String pass = sc.nextLine().trim();

            String dn = user.contains("=") ? user : "cn=" + user + "," + DN_SUFFIX;

            System.out.println();
            System.out.println(DIM + "  Sending BindRequest to " + LDAP_HOST + ":" + LDAP_PORT + RESET);
            System.out.println(DIM + "  DN   : " + dn + RESET);
            System.out.println(DIM + "  Pass : " + pass + "  (cleartext — RFC 4511 §4.2 simple auth)" + RESET);
            System.out.println();

            try (LdapConn test = new LdapConn(dn, pass)) {
                sessionDN   = dn;
                sessionPass = pass;
                System.out.println(GREEN + BOLD + "  ✔ Bind successful — welcome, " + user + RESET);
                System.out.println();
                break;
            } catch (Exception e) {
                System.out.println(RED + "  ✗ Login failed: " + e.getMessage() + RESET);
                System.out.println(DIM + "  (Is slapd running?  sudo service slapd start)" + RESET);
                System.out.println();
            }
        }

        // Main menu loop
        while (true) {
            printMenu();
            System.out.print(CYAN + "martin-airways> " + RESET);
            String input = sc.nextLine().trim().toLowerCase();
            switch (input) {
                case "1": case "list":   cmdList();       break;
                case "2": case "search": cmdSearch(sc);   break;
                case "3": case "add":    cmdAdd(sc);      break;
                case "4": case "delete": cmdDelete(sc);   break;
                case "5": case "modify": cmdModify(sc);   break;
                case "6": case "fast":   cmdFast(sc);     break;
                case "7": case "ber":    cmdToggleBer();  break;
                case "8": case "quit":
                case "q": case "exit":
                    System.out.println(DIM + "\nFlight plan closed. Goodbye.\n" + RESET);
                    return;
                default:
                    System.out.println(RED + "  Unknown command. Try 1-8." + RESET);
            }
        }
    }

    // ================================================================
    //  Commands
    // ================================================================

    static void cmdList() {
        System.out.println();
        try (LdapConn conn = new LdapConn(sessionDN, sessionPass)) {
            List<PlaneEntry> planes = conn.searchAll();
            if (planes.isEmpty()) {
                System.out.println(YELLOW + "  No aircraft found in fleet." + RESET);
            } else {
                planes.sort((a, b) -> {
                    try { return Integer.compare(Integer.parseInt(b.speed), Integer.parseInt(a.speed)); }
                    catch (NumberFormatException e) { return a.cn.compareTo(b.cn); }
                });
                System.out.println(BOLD + CYAN + "  ╔═══╦══════════════════════╦════════════════╦══════════════════════════════════════╗" + RESET);
                System.out.printf (BOLD + CYAN + "  ║ # ║ %-20s ║ %-14s ║ %-36s ║%n" + RESET, "AIRCRAFT", "MAX SPEED", "FULL DN (RFC 4512)");
                System.out.println(BOLD + CYAN + "  ╠═══╬══════════════════════╬════════════════╬══════════════════════════════════════╣" + RESET);
                int i = 1;
                for (PlaneEntry p : planes) {
                    String fullDN = "cn=" + p.cn + "," + BASE_DN;
                    System.out.printf("  ║ " + DIM + "%d" + RESET +
                            " ║ " + GREEN  + "%-20s" + RESET +
                            " ║ " + YELLOW + "%-10s km/h" + RESET +
                            " ║ " + DIM    + "%-36s" + RESET + " ║%n",
                            i++, p.cn, p.speed, fullDN);
                }
                System.out.println(BOLD + CYAN + "  ╚═══╩══════════════════════╩════════════════╩══════════════════════════════════════╝" + RESET);
                System.out.println(DIM + "  " + planes.size() + " aircraft · sorted fastest first · BaseDN: " + BASE_DN + RESET);
            }
        } catch (Exception e) {
            printError(e);
        }
        System.out.println();
    }

    static void cmdSearch(Scanner sc) {
        System.out.println();
        System.out.print("  " + CYAN + "Aircraft name: " + RESET);
        String name = sc.nextLine().trim();
        if (name.isEmpty()) { System.out.println(RED + "  Name cannot be empty." + RESET + "\n"); return; }

        try (LdapConn conn = new LdapConn(sessionDN, sessionPass)) {
            PlaneEntry p = conn.searchOne(name);
            if (p == null) {
                System.out.println(RED + "\n  ✗ '" + name + "' not found in fleet." + RESET);
            } else {
                String fullDN = "cn=" + p.cn + "," + BASE_DN;
                System.out.println();
                System.out.println(BOLD + GREEN + "  ✔ Aircraft found" + RESET +
                        DIM + "  (RFC 4511 §4.5 equalityMatch filter)" + RESET);
                System.out.println("  ┌──────────────────────────────────────────────────────────┐");
                System.out.printf ("  │  Name      : " + YELLOW + "%-44s" + RESET + " │%n", p.cn);
                System.out.printf ("  │  Max Speed : " + YELLOW + "%-44s" + RESET + " │%n", p.speed + " km/h");
                System.out.printf ("  │  Full DN   : " + CYAN   + "%-44s" + RESET + " │%n", fullDN);
                System.out.printf ("  │  BaseDN    : " + DIM    + "%-44s" + RESET + " │%n", BASE_DN);
                System.out.println("  └──────────────────────────────────────────────────────────┘");
            }
        } catch (Exception e) {
            printError(e);
        }
        System.out.println();
    }

    static void cmdAdd(Scanner sc) {
        System.out.println();
        System.out.print("  " + CYAN + "Aircraft name : " + RESET);
        String name = sc.nextLine().trim();
        if (name.isEmpty()) { System.out.println(RED + "  Name cannot be empty." + RESET + "\n"); return; }

        System.out.print("  " + CYAN + "Max speed (km/h): " + RESET);
        String speed = sc.nextLine().trim();
        if (speed.isEmpty()) { System.out.println(RED + "  Speed cannot be empty." + RESET + "\n"); return; }
        try { Integer.parseInt(speed); } catch (NumberFormatException e) {
            System.out.println(RED + "  Speed must be a number." + RESET + "\n"); return;
        }

        try (LdapConn conn = new LdapConn(sessionDN, sessionPass)) {
            System.out.println(DIM + "  Checking for duplicates (SearchRequest)..." + RESET);
            PlaneEntry existing = conn.searchOne(name);
            if (existing != null) {
                System.out.println(RED + "  ✗ '" + name + "' already exists." + RESET);
                System.out.println(DIM + "  resultCode 68 = entryAlreadyExists (RFC 4511 §4.1.9)" + RESET);
                System.out.println(DIM + "  Use option 5 (modify) to update its speed." + RESET);
                System.out.println();
                return;
            }
            conn.addPlane(name, speed);
            System.out.println(GREEN + "\n  ✔ " + name + " added to fleet." + RESET);
            System.out.println(DIM + "  DN   : cn=" + name + "," + BASE_DN + RESET);
            System.out.println(DIM + "  Speed: " + speed + " km/h" + RESET);
        } catch (Exception e) {
            printError(e);
        }
        System.out.println();
    }

    static void cmdDelete(Scanner sc) {
        System.out.println();
        System.out.print("  " + CYAN + "Aircraft name to delete: " + RESET);
        String name = sc.nextLine().trim();
        if (name.isEmpty()) { System.out.println(RED + "  Name cannot be empty." + RESET + "\n"); return; }

        System.out.print("  " + YELLOW + "  Confirm delete '" + name + "'? (yes/no): " + RESET);
        String confirm = sc.nextLine().trim().toLowerCase();
        if (!confirm.equals("yes") && !confirm.equals("y")) {
            System.out.println(DIM + "  Cancelled." + RESET + "\n"); return;
        }

        try (LdapConn conn = new LdapConn(sessionDN, sessionPass)) {
            conn.deletePlane(name);
            System.out.println(GREEN + "\n  ✔ " + name + " removed from fleet." + RESET);
            System.out.println(DIM + "  DN: cn=" + name + "," + BASE_DN + " deleted." + RESET);
        } catch (Exception e) {
            printError(e);
        }
        System.out.println();
    }

    static void cmdModify(Scanner sc) {
        System.out.println();
        System.out.print("  " + CYAN + "Aircraft name to update: " + RESET);
        String name = sc.nextLine().trim();
        if (name.isEmpty()) { System.out.println(RED + "  Name cannot be empty." + RESET + "\n"); return; }

        try (LdapConn conn = new LdapConn(sessionDN, sessionPass)) {
            PlaneEntry existing = conn.searchOne(name);
            if (existing == null) {
                System.out.println(RED + "  ✗ '" + name + "' not found in fleet." + RESET + "\n");
                return;
            }
            System.out.println(DIM + "  Current speed: " + existing.speed + " km/h" + RESET);
            System.out.print("  " + CYAN + "New max speed (km/h): " + RESET);
            String newSpeed = sc.nextLine().trim();
            if (newSpeed.isEmpty()) { System.out.println(RED + "  Speed cannot be empty." + RESET + "\n"); return; }
            try { Integer.parseInt(newSpeed); } catch (NumberFormatException e) {
                System.out.println(RED + "  Speed must be a number." + RESET + "\n"); return;
            }
            conn.modifySpeed(name, newSpeed);
            System.out.println(GREEN + "\n  ✔ " + name + " updated." + RESET);
            System.out.println(DIM + "  " + existing.speed + " km/h  →  " + newSpeed + " km/h" + RESET);
            System.out.println(DIM + "  RFC 4511 §4.6 ModifyRequest · operation=replace · attr=" + SPEED_ATTR + RESET);
        } catch (Exception e) {
            printError(e);
        }
        System.out.println();
    }

    static void cmdFast(Scanner sc) {
        System.out.println();
        System.out.print("  " + CYAN + "Minimum speed (km/h): " + RESET);
        String minSpeed = sc.nextLine().trim();
        if (minSpeed.isEmpty()) { System.out.println(RED + "  Speed cannot be empty." + RESET + "\n"); return; }
        try { Integer.parseInt(minSpeed); } catch (NumberFormatException e) {
            System.out.println(RED + "  Speed must be a number." + RESET + "\n"); return;
        }

        try (LdapConn conn = new LdapConn(sessionDN, sessionPass)) {
            List<PlaneEntry> planes = conn.searchByMinSpeed(minSpeed);
            System.out.println();
            System.out.println(DIM + "  Filter: greaterOrEqual [5]  " + SPEED_ATTR +
                    " >= " + minSpeed + "  (RFC 4511 §4.5.1)" + RESET);
            System.out.println();
            if (planes.isEmpty()) {
                System.out.println(YELLOW + "  No aircraft found with speed >= " + minSpeed + " km/h." + RESET);
            } else {
                planes.sort((a, b) -> {
                    try { return Integer.compare(Integer.parseInt(b.speed), Integer.parseInt(a.speed)); }
                    catch (NumberFormatException e) { return a.cn.compareTo(b.cn); }
                });
                System.out.println(BOLD + CYAN + "  Aircraft with speed >= " + minSpeed + " km/h:" + RESET);
                System.out.println(BOLD + CYAN + "  ╔══════════════════════╦════════════════╦══════════════════════════════════════╗" + RESET);
                System.out.printf (BOLD + CYAN + "  ║ %-20s ║ %-14s ║ %-36s ║%n" + RESET, "AIRCRAFT", "MAX SPEED", "FULL DN (RFC 4512)");
                System.out.println(BOLD + CYAN + "  ╠══════════════════════╬════════════════╬══════════════════════════════════════╣" + RESET);
                for (PlaneEntry p : planes) {
                    String fullDN = "cn=" + p.cn + "," + BASE_DN;
                    System.out.printf("  ║ " + GREEN  + "%-20s" + RESET +
                            " ║ " + YELLOW + "%-10s km/h" + RESET +
                            " ║ " + DIM    + "%-36s" + RESET + " ║%n",
                            p.cn, p.speed, fullDN);
                }
                System.out.println(BOLD + CYAN + "  ╚══════════════════════╩════════════════╩══════════════════════════════════════╝" + RESET);
                System.out.println(DIM + "  " + planes.size() + " aircraft found." + RESET);
            }
        } catch (Exception e) {
            printError(e);
        }
        System.out.println();
    }

    static void cmdToggleBer() {
        berSniff = !berSniff;
        if (berSniff) {
            System.out.println(PURPLE + "\n  ✔ BER sniff mode ON — raw bytes will be printed." + RESET);
            System.out.println(DIM + "  X.690 BER encoding: Tag | Length | Value  (hex)" + RESET);
            System.out.println(DIM + "  0x30=SEQUENCE  0x02=INTEGER  0x04=OCTET STRING" + RESET);
            System.out.println(DIM + "  0x60=BindReq  0x63=SearchReq  0x66=ModifyReq" + RESET);
            System.out.println(DIM + "  0x68=AddReq   0x4A=DelReq    0xA3=equalityMatch" + RESET);
            System.out.println(DIM + "  0xA5=greaterOrEqual  0x0A=ENUMERATED" + RESET);
        } else {
            System.out.println(DIM + "\n  BER sniff mode OFF." + RESET);
        }
        System.out.println();
    }

    // ================================================================
    //  UI helpers
    // ================================================================

    static void printBanner() {
        System.out.println(BOLD + YELLOW);
        System.out.println("  ███╗   ███╗ █████╗ ██████╗ ████████╗██╗███╗   ██╗");
        System.out.println("  ████╗ ████║██╔══██╗██╔══██╗╚══██╔══╝██║████╗  ██║");
        System.out.println("  ██╔████╔██║███████║██████╔╝   ██║   ██║██╔██╗ ██║");
        System.out.println("  ██║╚██╔╝██║██╔══██║██╔══██╗   ██║   ██║██║╚██╗██║");
        System.out.println("  ██║ ╚═╝ ██║██║  ██║██║  ██║   ██║   ██║██║ ╚████║");
        System.out.println("  ╚═╝     ╚═╝╚═╝  ╚═╝╚═╝  ╚═╝   ╚═╝   ╚═╝╚═╝  ╚═══╝");
        System.out.println(CYAN + "              A I R W A Y S  ✈  F L E E T  C L I" + RESET);
        System.out.println(DIM + "         LDAP Fleet Manager — COS332 Practical 5 — u26535272" + RESET);
        System.out.println(DIM + "         RFC 4511 · RFC 4512 · X.690 BER" + RESET);
        System.out.println();
    }

    static void printMenu() {
        String berStatus = berSniff ? PURPLE + " [ON]" + RESET : DIM + " [OFF]" + RESET;
        System.out.println(DIM + "  ─────────────────────────────────────────────────────" + RESET);
        System.out.println("  " + BOLD + "[1]" + RESET + " list    — all aircraft, fastest first");
        System.out.println("  " + BOLD + "[2]" + RESET + " search  — look up aircraft (shows full DN)");
        System.out.println("  " + BOLD + "[3]" + RESET + " add     — add aircraft (with duplicate check)");
        System.out.println("  " + BOLD + "[4]" + RESET + " delete  — remove aircraft");
        System.out.println("  " + BOLD + "[5]" + RESET + " modify  — update max speed " + DIM + "(RFC 4511 §4.6)" + RESET);
        System.out.println("  " + BOLD + "[6]" + RESET + " fast    — filter by minimum speed " + DIM + "(greaterOrEqual)" + RESET);
        System.out.println("  " + BOLD + "[7]" + RESET + " ber     — toggle BER sniff mode " + berStatus);
        System.out.println("  " + BOLD + "[8]" + RESET + " quit");
        System.out.println(DIM + "  ─────────────────────────────────────────────────────" + RESET);
    }

    static void printError(Exception e) {
        System.out.println(RED + "\n  ✗ LDAP error: " + e.getMessage() + RESET);
        System.out.println(DIM + "  (Is slapd running?  sudo service slapd start)" + RESET);
    }

    static void printBer(String label, byte[] data) {
        if (!berSniff) return;
        StringBuilder sb = new StringBuilder();
        sb.append("\n").append(PURPLE).append("  [BER ").append(label).append("]\n  ").append(RESET);
        for (int i = 0; i < data.length; i++) {
            sb.append(String.format("%02X ", data[i] & 0xFF));
            if ((i + 1) % 16 == 0 && i + 1 < data.length) sb.append("\n  ");
        }
        System.out.println(sb.toString().trim());
        System.out.println(DIM + "  (" + data.length + " bytes)" + RESET);
    }

    // ================================================================
    //  LDAP Connection wrapper
    // ================================================================

    static class LdapConn implements AutoCloseable {
        private final Socket       socket;
        private final OutputStream out;
        private final InputStream  in;

        LdapConn(String dn, String pass) throws Exception {
            socket = new Socket(LDAP_HOST, LDAP_PORT);
            out    = socket.getOutputStream();
            in     = socket.getInputStream();
            byte[] req = buildBindRequest(nextID(), dn, pass);
            printBer("SEND BindRequest", req);
            out.write(req);
            out.flush();
            readAndCheckBind(in);
        }

        List<PlaneEntry> searchAll() throws Exception {
            byte[] req = buildSearchAllRequest(nextID(), BASE_DN, SPEED_ATTR);
            printBer("SEND SearchRequest(all)", req);
            out.write(req); out.flush();
            return readAllSearchResults(in, SPEED_ATTR);
        }

        PlaneEntry searchOne(String cn) throws Exception {
            byte[] req = buildSearchRequest(nextID(), BASE_DN, cn, SPEED_ATTR);
            printBer("SEND SearchRequest(cn=" + cn + ")", req);
            out.write(req); out.flush();
            String speed = readSearchResponse(in, SPEED_ATTR);
            return speed == null ? null : new PlaneEntry(cn, speed);
        }

        List<PlaneEntry> searchByMinSpeed(String minSpeed) throws Exception {
            byte[] req = buildSearchMinSpeedRequest(nextID(), BASE_DN, minSpeed, SPEED_ATTR);
            printBer("SEND SearchRequest(greaterOrEqual " + minSpeed + ")", req);
            out.write(req); out.flush();
            return readAllSearchResults(in, SPEED_ATTR);
        }

        void addPlane(String cn, String speed) throws Exception {
            String dn = "cn=" + cn + "," + BASE_DN;
            byte[] req = buildAddRequest(nextID(), dn, cn, speed);
            printBer("SEND AddRequest", req);
            out.write(req); out.flush();
            readAndCheckResult(in, "Add");
        }

        void deletePlane(String cn) throws Exception {
            String dn = "cn=" + cn + "," + BASE_DN;
            byte[] req = buildDeleteRequest(nextID(), dn);
            printBer("SEND DeleteRequest", req);
            out.write(req); out.flush();
            readAndCheckResult(in, "Delete");
        }

        void modifySpeed(String cn, String newSpeed) throws Exception {
            String dn = "cn=" + cn + "," + BASE_DN;
            byte[] req = buildModifyRequest(nextID(), dn, SPEED_ATTR, newSpeed);
            printBer("SEND ModifyRequest", req);
            out.write(req); out.flush();
            readAndCheckResult(in, "Modify");
        }

        @Override
        public void close() {
            try { out.write(buildUnbindRequest(nextID())); out.flush(); socket.close(); }
            catch (Exception ignored) {}
        }
    }

    static synchronized int nextID() { return msgID++; }

    // ================================================================
    //  BER packet builders  (RFC 4511 + X.690)
    // ================================================================

    static byte[] buildBindRequest(int id, String dn, String pass) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(tlv(0x02, new byte[]{0x03}));
        body.write(tlv(0x04, dn.getBytes("UTF-8")));
        body.write(tlv(0x80, pass.getBytes("UTF-8")));
        return wrap(id, tlv(0x60, body.toByteArray()));
    }

    static byte[] buildSearchRequest(int id, String baseDN, String cn, String attr) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(tlv(0x04, baseDN.getBytes("UTF-8")));
        body.write(tlv(0x0A, new byte[]{0x02}));
        body.write(tlv(0x0A, new byte[]{0x00}));
        body.write(tlv(0x02, new byte[]{0x00}));
        body.write(tlv(0x02, new byte[]{0x00}));
        body.write(tlv(0x01, new byte[]{0x00}));
        ByteArrayOutputStream ava = new ByteArrayOutputStream();
        ava.write(tlv(0x04, "cn".getBytes("UTF-8")));
        ava.write(tlv(0x04, cn.getBytes("UTF-8")));
        body.write(tlv(0xA3, ava.toByteArray()));
        ByteArrayOutputStream attrs = new ByteArrayOutputStream();
        attrs.write(tlv(0x04, attr.getBytes("UTF-8")));
        body.write(tlv(0x30, attrs.toByteArray()));
        return wrap(id, tlv(0x63, body.toByteArray()));
    }

    static byte[] buildSearchAllRequest(int id, String baseDN, String attr) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(tlv(0x04, baseDN.getBytes("UTF-8")));
        body.write(tlv(0x0A, new byte[]{0x01}));
        body.write(tlv(0x0A, new byte[]{0x00}));
        body.write(tlv(0x02, new byte[]{0x00}));
        body.write(tlv(0x02, new byte[]{0x00}));
        body.write(tlv(0x01, new byte[]{0x00}));
        ByteArrayOutputStream ava = new ByteArrayOutputStream();
        ava.write(tlv(0x04, "objectClass".getBytes("UTF-8")));
        ava.write(tlv(0x04, "device".getBytes("UTF-8")));
        body.write(tlv(0xA3, ava.toByteArray()));
        ByteArrayOutputStream attrs = new ByteArrayOutputStream();
        attrs.write(tlv(0x04, "cn".getBytes("UTF-8")));
        attrs.write(tlv(0x04, attr.getBytes("UTF-8")));
        body.write(tlv(0x30, attrs.toByteArray()));
        return wrap(id, tlv(0x63, body.toByteArray()));
    }

    /**
     * greaterOrEqual filter — RFC 4511 §4.5.1
     * Filter CHOICE tag [5] constructed = 0xA5
     */
    static byte[] buildSearchMinSpeedRequest(int id, String baseDN,
                                              String minSpeed, String attr) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(tlv(0x04, baseDN.getBytes("UTF-8")));
        body.write(tlv(0x0A, new byte[]{0x01}));
        body.write(tlv(0x0A, new byte[]{0x00}));
        body.write(tlv(0x02, new byte[]{0x00}));
        body.write(tlv(0x02, new byte[]{0x00}));
        body.write(tlv(0x01, new byte[]{0x00}));
        ByteArrayOutputStream ava = new ByteArrayOutputStream();
        ava.write(tlv(0x04, attr.getBytes("UTF-8")));
        ava.write(tlv(0x04, minSpeed.getBytes("UTF-8")));
        body.write(tlv(0xA5, ava.toByteArray()));   // [5] greaterOrEqual
        ByteArrayOutputStream attrs = new ByteArrayOutputStream();
        attrs.write(tlv(0x04, "cn".getBytes("UTF-8")));
        attrs.write(tlv(0x04, attr.getBytes("UTF-8")));
        body.write(tlv(0x30, attrs.toByteArray()));
        return wrap(id, tlv(0x63, body.toByteArray()));
    }

    static byte[] buildAddRequest(int id, String dn, String cn, String speed) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(tlv(0x04, dn.getBytes("UTF-8")));
        ByteArrayOutputStream attrList = new ByteArrayOutputStream();
        attrList.write(buildAttribute("objectClass", "device"));
        attrList.write(buildAttribute("cn", cn));
        attrList.write(buildAttribute(SPEED_ATTR, speed));
        body.write(tlv(0x30, attrList.toByteArray()));
        return wrap(id, tlv(0x68, body.toByteArray()));
    }

    /**
     * ModifyRequest — RFC 4511 §4.6
     * [APPLICATION 6] = 0x66
     * operation: replace = 2
     */
    static byte[] buildModifyRequest(int id, String dn,
                                      String attr, String newVal) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(tlv(0x04, dn.getBytes("UTF-8")));
        ByteArrayOutputStream change = new ByteArrayOutputStream();
        change.write(tlv(0x0A, new byte[]{0x02}));          // operation=replace
        ByteArrayOutputStream pa = new ByteArrayOutputStream();
        pa.write(tlv(0x04, attr.getBytes("UTF-8")));
        ByteArrayOutputStream vals = new ByteArrayOutputStream();
        vals.write(tlv(0x04, newVal.getBytes("UTF-8")));
        pa.write(tlv(0x31, vals.toByteArray()));
        change.write(tlv(0x30, pa.toByteArray()));
        ByteArrayOutputStream changes = new ByteArrayOutputStream();
        changes.write(tlv(0x30, change.toByteArray()));
        body.write(tlv(0x30, changes.toByteArray()));
        return wrap(id, tlv(0x66, body.toByteArray()));
    }

    static byte[] buildDeleteRequest(int id, String dn) throws IOException {
        return wrap(id, tlv(0x4A, dn.getBytes("UTF-8")));
    }

    static byte[] buildUnbindRequest(int id) throws IOException {
        return wrap(id, tlv(0x42, new byte[0]));
    }

    static byte[] buildAttribute(String type, String value) throws IOException {
        ByteArrayOutputStream attr = new ByteArrayOutputStream();
        attr.write(tlv(0x04, type.getBytes("UTF-8")));
        ByteArrayOutputStream vals = new ByteArrayOutputStream();
        vals.write(tlv(0x04, value.getBytes("UTF-8")));
        attr.write(tlv(0x31, vals.toByteArray()));
        return tlv(0x30, attr.toByteArray());
    }

    // ================================================================
    //  Response parsers
    // ================================================================

    static void readAndCheckBind(InputStream in) throws IOException {
        byte[] msg = readMessage(in);
        printBer("RECV BindResponse", msg);
        for (int i = 0; i < msg.length - 2; i++) {
            if ((msg[i] & 0xFF) == 0x0A) {
                int rc = msg[i + 2] & 0xFF;
                if (rc != 0) throw new IOException(
                        "Bind failed — resultCode " + rc + " = " + resultCodeName(rc));
                return;
            }
        }
    }

    static void readAndCheckResult(InputStream in, String op) throws IOException {
        byte[] msg = readMessage(in);
        printBer("RECV " + op + "Response", msg);
        for (int i = 0; i < msg.length - 2; i++) {
            if ((msg[i] & 0xFF) == 0x0A) {
                int rc = msg[i + 2] & 0xFF;
                if (rc != 0) {
                    String diag = extractDiagnostic(msg, i + 3);
                    throw new IOException(op + " failed — resultCode " + rc +
                            " = " + resultCodeName(rc) +
                            (diag.isEmpty() ? "" : " (" + diag + ")"));
                }
                return;
            }
        }
    }

    static String extractDiagnostic(byte[] msg, int afterRC) {
        try {
            int pos = afterRC;
            if (pos >= msg.length) return "";
            if ((msg[pos] & 0xFF) == 0x04) {
                pos++;
                int len = readLength(msg, pos);
                pos += lengthBytes(msg, pos) + len;
            }
            if (pos < msg.length && (msg[pos] & 0xFF) == 0x04) {
                pos++;
                int len = readLength(msg, pos);
                pos += lengthBytes(msg, pos);
                return new String(msg, pos, len, "UTF-8");
            }
        } catch (Exception ignored) {}
        return "";
    }

    static String readSearchResponse(InputStream in, String attrName) throws IOException {
        String result = null;
        while (true) {
            byte[] msg = readMessage(in);
            if (msg.length == 0) break;
            printBer("RECV SearchMsg", msg);
            int offset = 2 + (msg[1] & 0xFF);
            int opTag  = msg[offset] & 0xFF;
            if      (opTag == 0x64) result = parseEntry(msg, offset, attrName);
            else if (opTag == 0x65) break;
        }
        return result;
    }

    static List<PlaneEntry> readAllSearchResults(InputStream in, String attrName) throws IOException {
        List<PlaneEntry> list = new ArrayList<>();
        while (true) {
            byte[] msg = readMessage(in);
            if (msg.length == 0) break;
            printBer("RECV SearchMsg", msg);
            int offset = 2 + (msg[1] & 0xFF);
            int opTag  = msg[offset] & 0xFF;
            if (opTag == 0x64) {
                String cn    = parseEntry(msg, offset, "cn");
                String speed = parseEntry(msg, offset, attrName);
                if (cn != null) list.add(new PlaneEntry(cn, speed != null ? speed : "N/A"));
            } else if (opTag == 0x65) {
                break;
            }
        }
        return list;
    }

    static String parseEntry(byte[] msg, int appOffset, String targetAttr) throws IOException {
        int pos = appOffset + 1;
        pos += lengthBytes(msg, pos);
        pos++;
        int dnLen = readLength(msg, pos);
        pos += lengthBytes(msg, pos) + dnLen;
        pos++;
        int attrListLen = readLength(msg, pos);
        pos += lengthBytes(msg, pos);
        int attrListEnd = pos + attrListLen;
        while (pos < attrListEnd) {
            pos++;
            int paLen = readLength(msg, pos);
            pos += lengthBytes(msg, pos);
            int paEnd = pos + paLen;
            pos++;
            int typeLen = readLength(msg, pos);
            pos += lengthBytes(msg, pos);
            String attrType = new String(msg, pos, typeLen, "UTF-8");
            pos += typeLen;
            pos++;
            int setLen = readLength(msg, pos);
            pos += lengthBytes(msg, pos);
            if (attrType.equalsIgnoreCase(targetAttr)) {
                pos++;
                int valLen = readLength(msg, pos);
                pos += lengthBytes(msg, pos);
                return new String(msg, pos, valLen, "UTF-8");
            }
            pos = paEnd;
        }
        return null;
    }

    // ================================================================
    //  Low-level BER helpers  (X.690)
    // ================================================================

    static byte[] wrap(int messageID, byte[] protocolOp) throws IOException {
        ByteArrayOutputStream inner = new ByteArrayOutputStream();
        inner.write(tlv(0x02, encodeInt(messageID)));
        inner.write(protocolOp);
        return tlv(0x30, inner.toByteArray());
    }

    static byte[] tlv(int tag, byte[] value) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(tag);
        int len = value.length;
        if      (len < 128) { out.write(len); }
        else if (len < 256) { out.write(0x81); out.write(len); }
        else                { out.write(0x82); out.write((len >> 8) & 0xFF); out.write(len & 0xFF); }
        out.write(value);
        return out.toByteArray();
    }

    static byte[] encodeInt(int v) {
        if (v < 128)   return new byte[]{(byte) v};
        if (v < 32768) return new byte[]{(byte)(v >> 8), (byte)(v & 0xFF)};
        return new byte[]{(byte)(v >> 16), (byte)((v >> 8) & 0xFF), (byte)(v & 0xFF)};
    }

    static byte[] readMessage(InputStream in) throws IOException {
        int tag = in.read();
        if (tag == -1) return new byte[0];
        int first = in.read() & 0xFF;
        int totalLen;
        if ((first & 0x80) == 0) {
            totalLen = first;
        } else {
            int numBytes = first & 0x7F;
            totalLen = 0;
            for (int i = 0; i < numBytes; i++) totalLen = (totalLen << 8) | (in.read() & 0xFF);
        }
        byte[] body = new byte[totalLen];
        int read = 0;
        while (read < totalLen) {
            int r = in.read(body, read, totalLen - read);
            if (r == -1) throw new IOException("Unexpected end of stream");
            read += r;
        }
        return body;
    }

    static int readLength(byte[] msg, int pos) {
        int first = msg[pos] & 0xFF;
        if ((first & 0x80) == 0) return first;
        int numBytes = first & 0x7F;
        int len = 0;
        for (int i = 1; i <= numBytes; i++) len = (len << 8) | (msg[pos + i] & 0xFF);
        return len;
    }

    static int lengthBytes(byte[] msg, int pos) {
        int first = msg[pos] & 0xFF;
        if ((first & 0x80) == 0) return 1;
        return 1 + (first & 0x7F);
    }

    // ================================================================
    //  Data class
    // ================================================================

    static class PlaneEntry {
        String cn, speed;
        PlaneEntry(String cn, String speed) { this.cn = cn; this.speed = speed; }
    }
}
