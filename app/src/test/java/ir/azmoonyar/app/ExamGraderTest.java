package ir.azmoonyar.app;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class ExamGraderTest {
    @Test public void gradesAnswersAndCountsBlankAndReviewSeparately() throws Exception {
        JSONObject variant = new JSONObject();
        variant.put("variant_id", "V1");
        variant.put("sheet_set_id", "qr-unique-1");
        variant.put("answer_key", "ABCD");
        ExamGrader.Result result = ExamGrader.grade(
                variant, new String[]{"A", "D", "", "?"});
        assertEquals(4, result.total);
        assertEquals(1, result.correct);
        assertEquals(2, result.incorrect);
        assertEquals(1, result.blank);
        assertEquals(1, result.review);
        assertEquals(25.0, result.percentage, 0.001);
    }

    @Test public void findsOnlyTheExactSavedQrSet() throws Exception {
        JSONArray variants = new JSONArray();
        variants.put(new JSONObject().put("sheet_set_id", "qr-a").put("variant_id", "V1"));
        variants.put(new JSONObject().put("sheet_set_id", "qr-b").put("variant_id", "V2"));
        assertEquals("V2", ExamGrader.findVariant(variants, "qr-b").optString("variant_id"));
        assertNull(ExamGrader.findVariant(variants, "qr-missing"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsMismatchedAnswerCount() throws Exception {
        JSONObject variant = new JSONObject().put("answer_key", "ABCD");
        ExamGrader.grade(variant, new String[]{"A", "B"});
    }
}
