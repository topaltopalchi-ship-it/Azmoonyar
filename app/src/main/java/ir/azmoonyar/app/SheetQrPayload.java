package ir.azmoonyar.app;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Strict, Android-independent parser for the seven-field AZY3 printed-sheet QR payload. */
public final class SheetQrPayload {
    public final String examName;
    public final String sheetSetId;
    public final String modelId;
    public final String templateId;
    public final int page;
    public final int pageCount;

    private SheetQrPayload(String examName, String sheetSetId, String modelId,
                           String templateId, int page, int pageCount) {
        this.examName = examName;
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
            String examName = new String(
                    Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8).trim();
            String sheetSetId = parts[2].trim();
            String modelId = parts[3].trim();
            String templateId = parts[4].trim();
            if (examName.isEmpty() || sheetSetId.isEmpty() || sheetSetId.length() > 100) return null;
            if (!modelId.matches("[A-Z]") || !"T1".equals(templateId)) return null;
            if (parts[5].isEmpty() || parts[6].isEmpty()) return null;

            int page = Integer.parseInt(parts[5]);
            int pageCount = Integer.parseInt(parts[6]);
            if (pageCount < 1 || pageCount > 50 || page < 1 || page > pageCount) return null;
            return new SheetQrPayload(examName, sheetSetId, modelId, templateId, page, pageCount);
        } catch (IllegalArgumentException invalidEncodingOrNumber) {
            return null;
        }
    }

    String[] toParts() {
        return new String[]{"AZY3",
                Base64.getUrlEncoder().withoutPadding().encodeToString(examName.getBytes(StandardCharsets.UTF_8)),
                sheetSetId, modelId, templateId, Integer.toString(page), Integer.toString(pageCount)};
    }
}
