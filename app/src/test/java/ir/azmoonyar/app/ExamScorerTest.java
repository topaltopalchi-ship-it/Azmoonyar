package ir.azmoonyar.app;

import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public class ExamScorerTest {
    private OMRAnswerClassifier.Result classify(double... scores) {
        return new OMRAnswerClassifier().classify(scores);
    }

    @Test public void scoresCorrectWrongBlankAndMultipleMarks() {
        ExamScorer.Result result = ExamScorer.score("ABCD", Arrays.asList(
                classify(0.8, 0.1, 0.1, 0.1),
                classify(0.1, 0.1, 0.8, 0.1),
                classify(0.05, 0.06, 0.08, 0.04),
                classify(0.7, 0.1, 0.68, 0.1)), 1.0, 0.0, 0.0);
        assertEquals(4, result.totalQuestions);
        assertEquals(1, result.correct);
        assertEquals(2, result.wrong);
        assertEquals(1, result.blank);
        assertEquals(1, result.multipleMarks);
        assertTrue(result.isComplete());
        assertEquals(25.0, result.percentage(), 0.001);
    }

    @Test public void reviewLeavesResultIncompleteAndPercentageUnavailable() {
        ExamScorer.Result result = ExamScorer.score("A", Arrays.asList(
                classify(0.40, 0.18, 0.12, 0.10)), 1.0, 0.0, 0.0);
        assertEquals(1, result.needsReview);
        assertFalse(result.isComplete());
        assertTrue(Double.isNaN(result.percentage()));
    }

    @Test public void appliesNegativeWrongAnswerPenalty() {
        ExamScorer.Result result = ExamScorer.score("A", Arrays.asList(
                classify(0.1, 0.8, 0.1, 0.1)), 2.0, -0.5, 0.0);
        assertEquals(-0.5, result.earnedPoints, 0.001);
    }

    @Test public void rejectsIncompleteKey() {
        try {
            ExamScorer.score("-", Arrays.asList(classify(0.8, 0.1, 0.1, 0.1)),
                    1.0, 0.0, 0.0);
            fail("Expected incomplete answer key to be rejected");
        } catch (IllegalArgumentException expected) { }
    }

    @Test public void rejectsMismatchedQuestionCounts() {
        try {
            ExamScorer.score("AB", Arrays.asList(classify(0.8, 0.1, 0.1, 0.1)),
                    1.0, 0.0, 0.0);
            fail("Expected mismatched question counts to be rejected");
        } catch (IllegalArgumentException expected) { }
    }
}
