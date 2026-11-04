package ir.azmoonyar.app;

import org.json.JSONException;
import org.json.JSONObject;

/** Strict participant identity carried by the QR printed on an issued answer sheet. */
public final class ParticipantQrPayload {
    public final String participantId;
    public final String participantName;
    public final String examName;
    /** A/B form model; this value is encoded only in QR, never printed as visible text. */
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
        if (raw == null || raw.trim().isEmpty()) throw new JSONException("QR خالی است.");
        JSONObject json = new JSONObject(raw);
        String id = required(json, "participant_id");
        String name = required(json, "participant_name");
        String exam = required(json, "exam_name");
        String model = required(json, "model_id").toUpperCase(java.util.Locale.ROOT);
        String sheet = required(json, "sheet_set_id");
        if (!"A".equals(model) && !"B".equals(model))
            throw new JSONException("گروه شرکت‌کننده در QR باید A یا B باشد.");
        return new ParticipantQrPayload(id, name, exam, model, sheet);
    }

    /** Fails closed unless roster identity and immutable issued model all match the QR. */
    public boolean matches(String expectedExamName, JSONObject rosterEntry, JSONObject issuedExam) {
        if (rosterEntry == null || issuedExam == null || expectedExamName == null) return false;
        String issuedKey = issuedExam.optString("answer_key", "");
        return participantId.equals(clean(rosterEntry.optString("id", "")))
                && participantName.equals(clean(rosterEntry.optString("name", "")))
                && group.equals(clean(rosterEntry.optString("model_id", "A")))
                && examName.equals(clean(expectedExamName))
                && examName.equals(clean(issuedExam.optString("exam_name", "")))
                && sheetSetId.equals(clean(issuedExam.optString("sheet_set_id", "")))
                && group.equals(clean(issuedExam.optString("model_id", "")))
                && issuedKey.matches("[A-D]{1,100}");
    }

    public boolean belongsToExam(String expectedExamName) {
        return expectedExamName != null && examName.equals(expectedExamName.trim());
    }

    private static String required(JSONObject json, String key) throws JSONException {
        String value = clean(json.optString(key, ""));
        if (value.isEmpty() || "null".equalsIgnoreCase(value))
            throw new JSONException("فیلد " + key + " در QR وجود ندارد.");
        return value;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
