// MultipartParser.java
// COS332 Practical Assignment 4
//
// Parses a multipart/form-data request body.
// This is the format browsers use when a form contains a file upload.
//
// A multipart body looks like this:
// --boundary\r\n
// Content-Disposition: form-data; name="person"\r\n
// \r\n
// Dr Smith\r\n
// --boundary\r\n
// Content-Disposition: form-data; name="photo"; filename="face.jpg"\r\n
// Content-Type: image/jpeg\r\n
// \r\n
// [binary image bytes]
// --boundary--\r\n
//
// We parse this manually — no libraries used.

import java.util.HashMap;
import java.util.Map;

public class MultipartParser {

    // Holds the result of parsing one multipart field
    public static class Field {
        public String name;      // form field name  e.g. "person"
        public String filename;  // only set for file uploads
        public byte[] value;     // raw bytes of the field value

        // Convenience — get value as a plain string (for text fields)
        public String asString() {
            if (value == null) return "";
            return new String(value);
        }
    }

    // Parse the raw body bytes and boundary string
    // Returns a map of field name → Field object
    public static Map<String, Field> parse(byte[] body, String boundary) {
        Map<String, Field> fields = new HashMap<>();
        if (body == null || boundary == null) return fields;

        // The boundary delimiter in the body is "--" + boundary
        byte[] delimiter = ("--" + boundary).getBytes();

        // Split the body on the boundary to get each part
        int[] positions = findAll(body, delimiter);

        for (int i = 0; i < positions.length - 1; i++) {
            // Start just after the delimiter + CRLF
            int start = positions[i] + delimiter.length + 2;
            // End just before the next delimiter (minus CRLF)
            int end   = positions[i + 1] - 2;
            if (start >= end) continue;

            // Extract this part's bytes
            byte[] part = slice(body, start, end);

            // Split headers from body at the first blank line (\r\n\r\n)
            int blankLine = indexOf(part, "\r\n\r\n".getBytes(), 0);
            if (blankLine < 0) continue;

            String headers  = new String(slice(part, 0, blankLine));
            byte[] partBody = slice(part, blankLine + 4, part.length);

            // Parse Content-Disposition header to get name and filename
            Field field = new Field();
            field.value = partBody;

            for (String header : headers.split("\r\n")) {
                if (header.toLowerCase()
                          .startsWith("content-disposition")) {
                    field.name     = extractParam(header, "name");
                    field.filename = extractParam(header, "filename");
                }
            }

            if (field.name != null) {
                fields.put(field.name, field);
            }
        }
        return fields;
    }

    // Extract a parameter value from a header string
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

    // Find all positions of a byte pattern in a byte array
    private static int[] findAll(byte[] data, byte[] pattern) {
        int[] temp = new int[1000];
        int count  = 0;
        int pos    = 0;
        while (pos < data.length) {
            int found = indexOf(data, pattern, pos);
            if (found < 0) break;
            temp[count++] = found;
            pos = found + pattern.length;
        }
        // Copy to correctly sized array
        int[] result = new int[count];
        System.arraycopy(temp, 0, result, 0, count);
        return result;
    }

    // Find first occurrence of pattern in data starting at offset
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

    // Return a slice of a byte array from start (inclusive) to end (exclusive)
    private static byte[] slice(byte[] data, int start, int end) {
        if (start < 0) start = 0;
        if (end > data.length) end = data.length;
        if (start >= end) return new byte[0];
        byte[] result = new byte[end - start];
        System.arraycopy(data, start, result, 0, end - start);
        return result;
    }
}
