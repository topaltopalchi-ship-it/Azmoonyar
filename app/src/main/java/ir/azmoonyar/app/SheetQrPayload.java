package ir.azmoonyar.app;

import java.nio.charset.StandardCharsets;

/** Strict, Android-independent parser for the seven-field AZY3 printed-sheet QR payload. */
public final class SheetQrPayload {
    public final String examName;
    private final String encodedExamName;
    public final String sheetSetId;
    public final String modelId;
    public final String templateId;
    public final int page;
    public final int pageCount;

    private SheetQrPayload(String examName, String encodedExamName, String sheetSetId, String modelId,
                           String templateId, int page, int pageCount) {
        this.examName = examName;
        this.encodedExamName = encodedExamName;
        this.sheetSetId = sheetSetId;
        this.modelId = modelId;
        this.templateId = templateId;
        this.page = page;
        this.pageCount = pageCount;
    }

    /** Returns null for any malformed payload; does not throw for untrusted QR contents. */
    public static SheetQrPayload parse(String payload) {
        if (payload == null) return null;
        String[] parts = payload.split("\\|", -1);
        if (parts.length != 7 || !"AZY3".equals(parts[0])) return null;

        try {
            String examName = new String(decodeUrlBase64(parts[1]), StandardCharsets.UTF_8).trim();
            String sheetSetId = parts[2].trim();
            String modelId = parts[3].trim();
            String templateId = parts[4].trim();
            if (examName.isEmpty() || sheetSetId.isEmpty() || sheetSetId.length() > 100) return null;
            if (!modelId.matches("[A-Z]") || !"T1".equals(templateId)) return null;
            if (parts[5].isEmpty() || parts[6].isEmpty()) return null;

            int page = Integer.parseInt(parts[5]);
            int pageCount = Integer.parseInt(parts[6]);
            if (pageCount < 1 || pageCount > 50 || page < 1 || page > pageCount) return null;
            return new SheetQrPayload(examName, parts[1], sheetSetId, modelId, templateId, page, pageCount);
        } catch (IllegalArgumentException invalidEncodingOrNumber) {
            return null;
        }
    }

    private static byte[] decodeUrlBase64(String input) {
        if (input == null || input.isEmpty() || !input.matches("[A-Za-z0-9_-]+={0,2}")) {
            throw new IllegalArgumentException("Invalid URL-safe Base64");
        }
        String normalized = input.replace('-', '+').replace('_', '/');
        int padding = normalized.indexOf('=');
        if (padding >= 0) normalized = normalized.substring(0, padding);
        int remainder = normalized.length() % 4;
        if (remainder == 1) throw new IllegalArgumentException("Invalid Base64 length");
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int accumulator = 0;
        int bits = 0;
        for (int i = 0; i < normalized.length(); i++) {
            int value = alphabet.indexOf(normalized.charAt(i));
            if (value < 0) throw new IllegalArgumentException("Invalid Base64 character");
            accumulator = (accumulator << 6) | value;
            bits += 6;
            if (bits >= 8) {
                bits -= 8;
                out.write((accumulator >> bits) & 0xff);
            }
        }
        if (bits > 0 && (accumulator & ((1 << bits) - 1)) != 0) {
            throw new IllegalArgumentException("Invalid Base64 trailing bits");
        }
        return out.toByteArray();
    }

    String[] toParts() {
        return new String[]{"AZY3", encodedExamName, sheetSetId, modelId, templateId,
                Integer.toString(page), Integer.toString(pageCount)};
    }
}
