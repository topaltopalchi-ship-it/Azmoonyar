package ir.azmoonyar.app;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class RandomExamBuilderTest {
    private JSONArray validBank() throws Exception {
        JSONArray bank = new JSONArray();
        JSONObject q = new JSONObject();
        q.put("id", "q1");
        q.put("text", "۲ + ۲ چند می‌شود؟");
        q.put("options", new JSONArray().put("۳").put("۴").put("۵").put("۶"));
        q.put("correct_index", 1);
        bank.put(q);
        return bank;
    }

    @Test public void rejectsQuestionWithoutCorrectAnswer() throws Exception {
        JSONArray bank = validBank();
        bank.getJSONObject(0).put("correct_index", -1);
        try {
            RandomExamBuilder.generateVariants(bank, 1, 7L);
            fail("Expected a missing answer key to be rejected");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("پاسخ صحیح"));
        }
    }

    @Test public void rejectsEmptyOption() throws Exception {
        JSONArray bank = validBank();
        bank.getJSONObject(0).getJSONArray("options").put(2, "");
        try {
            RandomExamBuilder.generateVariants(bank, 1, 7L);
            fail("Expected an empty option to be rejected");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("خالی است"));
        }
    }

    @Test public void generatedKeyMatchesShuffledCorrectOption() throws Exception {
        JSONArray bank = validBank();
        JSONArray variants = RandomExamBuilder.generateVariants(bank, 1, 7L);
        JSONObject variant = variants.getJSONObject(0);
        JSONObject question = variant.getJSONArray("questions").getJSONObject(0);
        int correctIndex = question.getInt("correct_index");
        assertEquals(String.valueOf((char) ('A' + correctIndex)), variant.getString("answer_key"));
        assertEquals("۴", question.getJSONArray("options").getString(correctIndex));
    }
}
