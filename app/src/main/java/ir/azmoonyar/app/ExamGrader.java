package ir.azmoonyar.app;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Grades already-recognized answers against the answer key stored with a saved exam variant.
 * Camera geometry, bubble extraction and confidence/review decisions are separate OMR stages.
 */
public final class ExamGrader {
    private ExamGrader() {}

    public static final class Result {
        public final String variantId;
        public final int total;
        public final int correct;
        public final int incorrect;
        public final int blank;
        public final int review;
        public final double percentage;
        public final JSONArray itemResults;

        private Result(String variantId, int total, int correct, int incorrect,
                       int blank, int review, JSONArray itemResults) {
            this.variantId = variantId;
            this.total = total;
            this.correct = correct;
            this.incorrect = incorrect;
            this.blank = blank;
            this.review = review;
            this.percentage = total == 0 ? 0.0 : (100.0 * correct / total);
            this.itemResults = itemResults;
        }

        public JSONObject toJson() throws Exception {
            JSONObject out = new JSONObject();
            out.put("variant_id", variantId);
            out.put("total", total);
            out.put("correct", correct);
            out.put("incorrect", incorrect);
            out.put("blank", blank);
            out.put("review", review);
            out.put("percentage", percentage);
            out.put("items", itemResults);
            return out;
        }
    }

    /** Finds a saved variant by its stable sheet_set_id; no fallback to a guessed model. */
    public static JSONObject findVariant(JSONArray savedVariants, String sheetSetId) {
        if (savedVariants == null || sheetSetId == null || sheetSetId.trim().isEmpty()) return null;
        for (int i = 0; i < savedVariants.length(); i++) {
            JSONObject variant = savedVariants.optJSONObject(i);
            if (variant != null && sheetSetId.equals(variant.optString("sheet_set_id", ""))) {
                return variant;
            }
        }
        return null;
    }

    /**
     * recognizedAnswers has one entry per printed question: A-D for a classified mark,
     * empty string for blank, and "?" for ambiguous/multiple marks requiring manual review.
     */
    public static Result grade(JSONObject variant, String[] recognizedAnswers) throws Exception {
        if (variant == null) throw new IllegalArgumentException("نسخهٔ آزمون پیدا نشد.");
        String key = variant.optString("answer_key", "").trim().toUpperCase(java.util.Locale.ROOT);
        if (key.isEmpty()) throw new IllegalArgumentException("کلید تصحیح در نسخهٔ ذخیره‌شده موجود نیست.");
        if (recognizedAnswers == null || recognizedAnswers.length != key.length()) {
            throw new IllegalArgumentException("تعداد پاسخ‌های خوانده‌شده با تعداد سؤال‌های نسخه برابر نیست.");
        }
        int correct = 0, incorrect = 0, blank = 0, review = 0;
        JSONArray items = new JSONArray();
        for (int i = 0; i < key.length(); i++) {
            char expected = key.charAt(i);
            if (expected < 'A' || expected > 'D') {
                throw new IllegalArgumentException("کلید تصحیح نسخه دارای مقدار نامعتبر است.");
            }
            String raw = recognizedAnswers[i] == null ? "" : recognizedAnswers[i].trim().toUpperCase(java.util.Locale.ROOT);
            String status;
            String answer = raw;
            if (raw.isEmpty() || "BLANK".equals(raw)) {
                blank++;
                status = "BLANK";
                answer = "";
            } else if ("?".equals(raw) || "REVIEW".equals(raw) || "MULTIPLE".equals(raw)) {
                review++;
                status = "REVIEW";
                answer = "?";
            } else if (raw.length() == 1 && raw.charAt(0) >= 'A' && raw.charAt(0) <= 'D') {
                if (raw.charAt(0) == expected) {
                    correct++;
                    status = "CORRECT";
                } else {
                    incorrect++;
                    status = "INCORRECT";
                }
            } else {
                throw new IllegalArgumentException("پاسخ سؤال " + (i + 1) + " نامعتبر است.");
            }
            JSONObject item = new JSONObject();
            item.put("question", i + 1);
            item.put("recognized", answer);
            item.put("correct_answer", expected);
            item.put("status", status);
            items.put(item);
        }
        return new Result(variant.optString("variant_id", ""),
                key.length(), correct, incorrect, blank, review, items);
    }
}
