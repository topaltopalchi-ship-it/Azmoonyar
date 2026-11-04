package ir.azmoonyar.app;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class ParticipantQrPayloadTest {
    private JSONObject roster() throws Exception {
        return new JSONObject().put("id", "p-1").put("name", "نام شرکت‌کننده").put("model_id", "B");
    }
    private JSONObject issued() throws Exception {
        return new JSONObject().put("exam_name", "آزمون نمونه")
                .put("sheet_set_id", "set-1").put("model_id", "B")
                .put("answer_key", "ABCDABCD");
    }
    private String qr() throws Exception {
        return new JSONObject().put("participant_id", "p-1")
                .put("participant_name", "نام شرکت‌کننده").put("exam_name", "آزمون نمونه")
                .put("model_id", "B").put("sheet_set_id", "set-1").toString();
    }

    @Test public void parsesContractAndMatchesIssuedModel() throws Exception {
        ParticipantQrPayload payload = ParticipantQrPayload.parse(qr());
        assertEquals("B", payload.group);
        assertTrue(payload.matches("آزمون نمونه", roster(), issued()));
    }

    @Test public void rejectsUnknownModel() throws Exception {
        JSONObject bad = new JSONObject(qr()).put("model_id", "C");
        try {
            ParticipantQrPayload.parse(bad.toString());
            fail("invalid model must be rejected");
        } catch (org.json.JSONException expected) { }
    }

    @Test public void rejectsParticipantOrSheetMismatch() throws Exception {
        ParticipantQrPayload payload = ParticipantQrPayload.parse(qr());
        assertFalse(payload.matches("آزمون نمونه",
                new JSONObject(roster().toString()).put("id", "other"), issued()));
        assertFalse(payload.matches("آزمون نمونه", roster(),
                new JSONObject(issued().toString()).put("sheet_set_id", "other")));
        assertFalse(payload.matches("آزمون نمونه", roster(),
                new JSONObject(issued().toString()).put("model_id", "A")));
    }

    @Test public void rejectsIncompleteIssuedKey() throws Exception {
        ParticipantQrPayload payload = ParticipantQrPayload.parse(qr());
        assertFalse(payload.matches("آزمون نمونه", roster(),
                new JSONObject(issued().toString()).put("answer_key", "AB-X")));
    }
}
