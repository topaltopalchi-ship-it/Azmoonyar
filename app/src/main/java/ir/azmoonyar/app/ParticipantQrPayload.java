package ir.azmoonyar.app;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Parsed identity carried by participant response-sheet QR codes.
 *
 * The existing QR schema uses model_id for the concealed A/B form group.
 * Group is intentionally not a printed label on the paper.
 */
public final class ParticipantQrPayload {
    public final String participantId;
    public final String participantName;
    public final String examName;
    public final String group;
    public final String sheetSetId;

    private ParticipantQrPayload(String participantId, String participantName,
                                  String examName, String group, String sheetSetId) {
        this.participantId = participantId;
        this.participantName = participantName;
        this.examName = examName;
        this.group = group;
        this.sheetSetId = sheetSetId;
    }

    public static ParticipantQrPayload parse(String raw) throws JSONException {
        if (raw == null || raw.trim().isEmpty()) {
            throw new JSONException("QR خالی است.");
        }
        JSONObject json = new JSONObject(raw);
        String participantId = required(json, "participant_id");
        String participantName = required(json, "participant_name");
        String examName = required(json, "exam_name");
        String group = required(json, "model_id").trim().toUpperCase(java.util.Locale.ROOT);
        String sheetSetId = required(json, "sheet_set_id");

        if (!"A".equals(group) && !"B".equals(group)) {
            throw new JSONException("گروه شرکت‌کننده در QR باید A یا B باشد.");
        }
        return new ParticipantQrPayload(participantId, participantName, examName, group, sheetSetId);
    }

    public boolean belongsToExam(String expectedExamName) {
        return expectedExamName != null && examName.equals(expectedExamName.trim());
    }

    private static String required(JSONObject json, String key) throws JSONException {
        String value = json.optString(key, "").trim();
        if (value.isEmpty() || "null".equalsIgnoreCase(value)) {
            throw new JSONException("فیلد " + key + " در QR وجود ندارد.");
        }
        return value;
    }
}
