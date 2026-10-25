package ir.azmoonyar.app;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.HashSet;
import java.util.Set;

/** Builds distinct randomized exam variants while preserving each question's correct answer. */
public final class RandomExamBuilder {
    private RandomExamBuilder() {}

    public static JSONArray generateVariants(JSONArray bank, int variantCount, long seed) throws Exception {
        if (bank == null || bank.length() == 0) {
            throw new IllegalArgumentException("بانک سؤال خالی است.");
        }
        if (variantCount < 1 || variantCount > 10) {
            throw new IllegalArgumentException("تعداد نسخه باید بین ۱ تا ۱۰ باشد.");
        }

        List<JSONObject> sourceQuestions = new ArrayList<>();
        for (int i = 0; i < bank.length(); i++) {
            JSONObject q = bank.optJSONObject(i);
            if (q != null) {
                JSONArray options = q.optJSONArray("options");
                int correct = q.optInt("correct_index", -1);
                if (options == null || options.length() != 4) {
                    throw new IllegalArgumentException("هر سؤال باید دقیقاً چهار گزینه داشته باشد.");
                }
                if (correct < 0 || correct > 3) {
                    throw new IllegalArgumentException("پاسخ صحیح یک سؤال معتبر نیست.");
                }
                sourceQuestions.add(q);
            }
        }
        if (sourceQuestions.isEmpty()) {
            throw new IllegalArgumentException("بانک سؤال، سؤال معتبر ندارد.");
        }

        JSONArray variants = new JSONArray();
        Random random = new Random(seed);
        Set<String> usedVariantSignatures = new HashSet<>();

        for (int v = 0; v < variantCount; v++) {
            JSONObject acceptedVariant = null;
            String acceptedSignature = null;

            // Uniqueness includes both question order and option order. This allows
            // multiple genuinely different forms even when the bank has few questions.
            for (int attempt = 0; attempt < 1000 && acceptedVariant == null; attempt++) {
                List<JSONObject> questions = new ArrayList<>(sourceQuestions);
                Collections.shuffle(questions, random);
                JSONArray outputQuestions = new JSONArray();
                StringBuilder answerKey = new StringBuilder();
                StringBuilder signature = new StringBuilder();

                for (int position = 0; position < questions.size(); position++) {
                    JSONObject source = questions.get(position);
                    JSONArray sourceOptions = source.optJSONArray("options");
                    int correct = source.optInt("correct_index", -1);

                    List<Integer> optionOrder = new ArrayList<>();
                    for (int i = 0; i < 4; i++) optionOrder.add(i);
                    Collections.shuffle(optionOrder, random);

                    JSONArray options = new JSONArray();
                    int newCorrect = -1;
                    signature.append(source.optString("id", source.optString("text", ""))).append(':');
                    for (int i = 0; i < optionOrder.size(); i++) {
                        int originalIndex = optionOrder.get(i);
                        String optionText = sourceOptions.optString(originalIndex, "");
                        options.put(optionText);
                        signature.append(originalIndex).append(',');
                        if (originalIndex == correct) newCorrect = i;
                    }
                    signature.append('|');

                    JSONObject item = new JSONObject();
                    item.put("source_id", source.optString("id", ""));
                    item.put("position", position + 1);
                    item.put("text", source.optString("text", ""));
                    item.put("topic", source.optString("topic", ""));
                    item.put("options", options);
                    item.put("correct_index", newCorrect);
                    item.put("correct_letter", String.valueOf((char) ('A' + newCorrect)));
                    outputQuestions.put(item);
                    answerKey.append((char) ('A' + newCorrect));
                }

                String candidateSignature = signature.toString();
                if (usedVariantSignatures.add(candidateSignature)) {
                    JSONObject variant = new JSONObject();
                    variant.put("variant_id", "V" + (v + 1));
                    variant.put("questions", outputQuestions);
                    variant.put("answer_key", answerKey.toString());
                    acceptedVariant = variant;
                    acceptedSignature = candidateSignature;
                }
            }

            if (acceptedVariant == null) {
                throw new IllegalArgumentException(
                    "با گزینه‌ها و سؤال‌های فعلی نتوانستم " + variantCount
                    + " مدل متفاوت بسازم. تعداد سؤال‌ها یا تنوع گزینه‌ها را بررسی کن.");
            }
            variants.put(acceptedVariant);
        }
        return variants;
    }
}
