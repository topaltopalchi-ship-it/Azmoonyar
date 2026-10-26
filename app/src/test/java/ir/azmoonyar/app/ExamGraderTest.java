package ir.azmoonyar.app;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

public class ExamGraderTest {
    private JSONObject variant(String sheetSetId, String variantId, String answerKey) throws Exception {
        return new JSONObject()
                .put("sheet_set_id", sheetSetId)
                .put("variant_id", variantId)
                .put("answer_key", answerKey);
    }

    @Test public void gradesAnswersAndCountsBlankAndReviewSeparately() throws Exception {
        JSONObject variant = variant("qr-unique-1", "V1", "ABCD");
        ExamGrader.Result result = ExamGrader.grade(
                variant, new String[]{"A", "D", "", "?"});
        assertEquals(4, result.total);
        assertEquals(1, result.correct);
        assertEquals(2, result.incorrect);
        assertEquals(1, result.blank);
        assertEquals(1, result.review);
        assertEquals(25.0, result.percentage, 0.001);
        assertEquals("V1", result.variantId);
        assertEquals(4, result.itemResults.length());
    }

    @Test public void findsOnlyTheExactSavedQrSet() throws Exception {
        JSONArray variants = new JSONArray();
        variants.put(variant("qr-a", "V1", "ABCD"));
        variants.put(variant("qr-b", "V2", "DCBA"));
        assertEquals("V2", ExamGrader.findVariant(variants, "qr-b").optString("variant_id"));
        assertNull(ExamGrader.findVariant(variants, "qr-missing"));
        assertNull(ExamGrader.findVariant(variants, ""));
        assertNull(ExamGrader.findVariant(null, "qr-a"));
    }

    @Test public void blankAndReviewAreNotCountedAsIncorrect() throws Exception {
        ExamGrader.Result result = ExamGrader.grade(
                variant("qr-a", "V1", "ABCD"), new String[]{"", "?", "BLANK", "REVIEW"});
        assertEquals(0, result.correct);
        assertEquals(0, result.incorrect);
        assertEquals(2, result.blank);
        assertEquals(2, result.review);
    }

    @Test public void serializesStableGradeSummary() throws Exception {
        ExamGrader.Result result = ExamGrader.grade(
                variant("qr-a", "V1", "AB"), new String[]{"A", "B"});
        JSONObject json = result.toJson();
        assertEquals("V1", json.getString("variant_id"));
        assertEquals(2, json.getInt("correct"));
        assertEquals(100.0, json.getDouble("percentage"), 0.001);
        assertEquals(2, json.getJSONArray("items").length());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsMismatchedAnswerCount() throws Exception {
        ExamGrader.grade(variant("qr-a", "V1", "ABCD"), new String[]{"A", "B"});
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsMissingVariant() throws Exception {
        ExamGrader.grade(null, new String[]{"A"});
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsMissingAnswerKey() throws Exception {
        ExamGrader.grade(variant("qr-a", "V1", ""), new String[]{});
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsInvalidOption() throws Exception {
        ExamGrader.grade(variant("qr-a", "V1", "AB"), new String[]{"E", "B"});
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsInvalidAnswerKey() throws Exception {
        ExamGrader.grade(variant("qr-a", "V1", "AX"), new String[]{"A", "B"});
    }
}