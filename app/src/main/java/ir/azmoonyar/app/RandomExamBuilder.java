package ir.azmoonyar.app;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.HashSet;
import java.util.Set;

/** Builds randomized exam variants while preserving each question's correct answer. */
public final class RandomExamBuilder {
    private RandomExamBuilder() {}

    public static JSONArray generateVariants(JSONArray bank, int variantCount, long seed) throws Exception {
        if (bank == null || bank.length() == 0) throw new IllegalArgumentException("بانک سؤال خالی است.");
        if (variantCount < 1 || variantCount > 10) throw new IllegalArgumentException("تعداد نسخه باید بین ۱ تا ۱۰ باشد.");
        JSONArray variants = new JSONArray();
        Random random = new Random(seed);
        Set<String> usedQuestionOrders = new HashSet<>();
        for (int v = 0; v < variantCount; v++) {
            List<JSONObject> questions = new ArrayList<>();
            for (int i = 0; i < bank.length(); i++) {
                JSONObject q = bank.optJSONObject(i);
                if (q != null) questions.add(q);
            }
            if (questions.isEmpty()) throw new IllegalArgumentException("بانک سؤال، سؤال معتبر ندارد.");
            boolean uniqueOrder = false;
            for (int attempt = 0; attempt < 1000 && !uniqueOrder; attempt++) {
                Collections.shuffle(questions, random);
                StringBuilder signature = new StringBuilder();
                for (JSONObject q : questions) {
                    signature.append(q.optString("id", q.optString("text", ""))).append("|");
                }
                uniqueOrder = usedQuestionOrders.add(signature.toString());
            }
            if (!uniqueOrder) {
                throw new IllegalArgumentException("برای این تعداد سؤال، مدل‌های بیشتری با ترتیب سؤال متفاوت پیدا نشد. تعداد مدل‌ها را کمتر کن یا سؤال‌های بیشتری اضافه کن.");
            }
            JSONArray outputQuestions = new JSONArray();
            StringBuilder answerKey = new StringBuilder();
            for (int position = 0; position < questions.size(); position++) {
                JSONObject source = questions.get(position);
                JSONArray sourceOptions = source.optJSONArray("options");
                if (sourceOptions == null || sourceOptions.length() != 4) {
                    throw new IllegalArgumentException("هر سؤال باید دقیقاً چهار گزینه داشته باشد.");
                }
                int correct = source.optInt("correct_index", -1);
                if (correct < 0 || correct > 3) throw new IllegalArgumentException("پاسخ صحیح یک سؤال معتبر نیست.");
                List<Integer> optionOrder = new ArrayList<>();
                for (int i = 0; i < 4; i++) optionOrder.add(i);
                Collections.shuffle(optionOrder, random);
                JSONArray options = new JSONArray();
                int newCorrect = -1;
                for (int i = 0; i < optionOrder.size(); i++) {
                    int originalIndex = optionOrder.get(i);
                    options.put(sourceOptions.optString(originalIndex, ""));
                    if (originalIndex == correct) newCorrect = i;
                }
                JSONObject item = new JSONObject();
                item.put("source_id", source.optString("id", ""));
                item.put("position", position + 1);
                item.put("text", source.optString("text", ""));
                item.put("topic", source.optString("topic", ""));
                item.put("options", options);
                item.put("correct_index", newCorrect);
                item.put("correct_letter", String.valueOf((char)('A' + newCorrect)));
                outputQuestions.put(item);
                answerKey.append((char)('A' + newCorrect));
            }
            JSONObject variant = new JSONObject();
            variant.put("variant_id", "V" + (v + 1));
            variant.put("questions", outputQuestions);
            variant.put("answer_key", answerKey.toString());
            variants.put(variant);
        }
        return variants;
    }
}
