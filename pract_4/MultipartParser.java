// MultipartParser.java
// COS332 Practical Assignment 4
//
// Manually parses a multipart/form-data request body.
// This is the format browsers use when a form contains a file upload.
//
// A multipart body looks like this:
//
//   --boundary\r\n
//   Content-Disposition: form-data; name="person"\r\n
//   \r\n
//   Dr Smith\r\n
//   --boundary\r\n
//   Content-Disposition: form-data; name="photo"; filename="face.jpg"\r\n
//   Content-Type: image/jpeg\r\n
//   \r\n
//   [binary image bytes]
//   --boundary--\r\n
//
// No external libraries are used — everything is done with byte arrays.

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MultipartParser {

    // Holds the result of parsing one multipart part
    public static class Field {
        public String name;      // form field name  e.g. "person"
        public String filename;  // only set for file upload parts
        public byte[] value;     // raw bytes of the part body

        // Convenience: interpret the bytes as a plain UTF-8 string
        public String asString() {
            if (value == null) return "";
            try {
                return new String(value, "UTF-8");
            } catch (Exception e) {
                return new String(value);
            }
        }
    }

    // ── Main entry point ─────────────────────────────────────
    // body     = the raw POST body bytes
    // boundary = the boundary string from the Content-Type header
    //            (WITHOUT the leading "--")
    public static Map<String, Field> parse(byte[] body, String boundary) {
        Map<String, Field> fields = new HashMap<>();
        if (body == null || body.length == 0
                || boundary == null || boundary.isEmpty())
            return fields;

        // The actual delimiter in the body is "--" + boundary
        byte[] delimiter = ("--" + boundary).getBytes();

        // Find all positions where the delimiter appears
        List<Integer> positions = findAll(body, delimiter);
        if (positions.size() < 2) return fields;

        // Each pair of consecutive positions brackets one part
        for (int i = 0; i < positions.size() - 1; i++) {
            // Skip past the delimiter and the \r\n that follows it
            int start = positions.get(i) + delimiter.length;
            // Skip the \r\n after the boundary line
            if (start + 1 < body.length
                    && body[start] == '\r' && body[start + 1] == '\n')
                start += 2;

            // The part ends just before the next delimiter,
            // minus the \r\n that precedes it
            int end = positions.get(i + 1);
            if (end >= 2
                    && body[end - 2] == '\r' && body[end - 1] == '\n')
                end -= 2;

            if (start >= end) continue;

            // Slice out this one part
            byte[] part = slice(body, start, end);

            // Find the blank line (\r\n\r\n) that separates headers
            // from the part body
            int blankLine = indexOf(part, "\r\n\r\n".getBytes(), 0);
            if (blankLine < 0) continue;

            String headerBlock = new String(slice(part, 0, blankLine));
            byte[] partBody    = slice(part, blankLine + 4, part.length);

            // Parse Content-Disposition to get name / filename
            Field field = new Field();
            field.value = partBody;

            for (String header : headerBlock.split("\r\n")) {
                if (header.toLowerCase()
                          .startsWith("content-disposition")) {
                    field.name     = extractParam(header, "name");
                    field.filename = extractParam(header, "filename");
                }
            }

            if (field.name != null)
                fields.put(field.name, field);
        }
        return fields;
    }

    // ── Extract a quoted parameter from a header ──────────────
    // e.g. extractParam("...name=\"photo\"...", "name") → "photo"
    private static String extractParam(String header, String param) {
        String search = param + "=\"";
        int start = header.indexOf(search);
        if (start < 0) return null;
        start += search.length();
        int end = header.indexOf("\"", start);
        if (end < 0) return null;
        return header.substring(start, end);
    }

    // ── Find all positions of a byte pattern ─────────────────
    private static List<Integer> findAll(byte[] data, byte[] pattern) {
        List<Integer> result = new ArrayList<>();
        int pos = 0;
        while (pos <= data.length - pattern.length) {
            int found = indexOf(data, pattern, pos);
            if (found < 0) break;
            result.add(found);
            pos = found + pattern.length;
        }
        return result;
    }

    // ── Find first occurrence of pattern starting at offset ──
    private static int indexOf(byte[] data, byte[] pattern, int offset) {
        outer:
        for (int i = offset; i <= data.length - pattern.length; i++) {
            for (int j = 0; j < pattern.length; j++) {
                if (data[i + j] != pattern[j]) continue outer;
            }
            return i;
        }
        return -1;
    }

    // ── Slice a byte array [start, end) ──────────────────────
    private static byte[] slice(byte[] data, int start, int end) {
        if (start < 0) start = 0;
        if (end > data.length) end = data.length;
        if (start >= end) return new byte[0];
        byte[] result = new byte[end - start];
        System.arraycopy(data, start, result, 0, end - start);
        return result;
    }
}
