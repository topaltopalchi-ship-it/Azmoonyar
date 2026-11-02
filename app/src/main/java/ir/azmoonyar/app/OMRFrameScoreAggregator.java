package ir.azmoonyar.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Robust aggregation for independently aligned and scored OMR frames.
 * Each frame contains the per-question results produced by OMRSheetReader.
 * The median is calculated per option, then the unchanged classifier is applied
 * once to the aggregated scores.
 */
public final class OMRFrameScoreAggregator {
    public static final class AggregatedQuestion {
        public final int questionIndex;
        public final double[] darkness;
        public final OMRAnswerClassifier.Result classification;

        private AggregatedQuestion(int questionIndex, double[] darkness,
                OMRAnswerClassifier.Result classification) {
            this.questionIndex = questionIndex;
            this.darkness = darkness.clone();
            this.classification = classification;
        }
    }

    private OMRFrameScoreAggregator() {}

    public static double median(double[] values) {
        if (values == null || values.length == 0)
            throw new IllegalArgumentException("At least one frame is required.");
        double[] sorted = values.clone();
        for (double value : sorted) {
            if (Double.isNaN(value) || Double.isInfinite(value) || value < 0 || value > 1)
                throw new IllegalArgumentException("Scores must be between zero and one.");
        }
        java.util.Arrays.sort(sorted);
        int mid = sorted.length / 2;
        return sorted.length % 2 == 1 ? sorted[mid]
                : (sorted[mid - 1] + sorted[mid]) / 2.0;
    }

    public static List<AggregatedQuestion> aggregate(
            List<List<OMRSheetReader.QuestionResult>> frames,
            OMRAnswerClassifier classifier) {
        if (frames == null || frames.isEmpty() || classifier == null)
            throw new IllegalArgumentException("At least one valid frame and classifier are required.");
        int questionCount = -1;
        for (List<OMRSheetReader.QuestionResult> frame : frames) {
            if (frame == null || frame.isEmpty())
                throw new IllegalArgumentException("Frame results cannot be empty.");
            if (questionCount < 0) questionCount = frame.size();
            if (frame.size() != questionCount)
                throw new IllegalArgumentException("All frames must contain the same question count.");
            for (int q = 0; q < frame.size(); q++) {
                OMRSheetReader.QuestionResult result = frame.get(q);
                if (result == null || result.questionIndex != q || result.darkness == null
                        || result.darkness.length != 4)
                    throw new IllegalArgumentException("Frame question results are inconsistent.");
                classifier.classify(result.darkness); // validates each input score
            }
        }

        List<AggregatedQuestion> output = new ArrayList<>();
        for (int q = 0; q < questionCount; q++) {
            double[] scores = new double[4];
            for (int option = 0; option < 4; option++) {
                double[] samples = new double[frames.size()];
                for (int f = 0; f < frames.size(); f++)
                    samples[f] = frames.get(f).get(q).darkness[option];
                scores[option] = median(samples);
            }
            OMRAnswerClassifier.Result combined = classifier.classify(scores);
            int agreedOption = -2;
            boolean disagreement = false;
            for (List<OMRSheetReader.QuestionResult> frame : frames) {
                OMRAnswerClassifier.Result frameResult = frame.get(q).classification;
                if (frameResult.status == OMRAnswerClassifier.Status.ANSWERED) {
                    if (agreedOption == -2) agreedOption = frameResult.optionIndex;
                    else if (agreedOption != frameResult.optionIndex) disagreement = true;
                }
            }
            // Conflicting clear answers across frames are not silently resolved by the median.
            if (disagreement && combined.status != OMRAnswerClassifier.Status.BLANK) {
                combined = new OMRAnswerClassifier.Result(
                        OMRAnswerClassifier.Status.REVIEW, -1,
                        combined.margin, combined.strongestScore);
            }
            output.add(new AggregatedQuestion(q, scores, combined));
        }
        return Collections.unmodifiableList(output);
    }
}
