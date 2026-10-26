package ir.azmoonyar.app;

import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import static org.junit.Assert.*;

public class SheetQrPayloadTest {
    private String payload(String exam, String setId, String model, String template,
                           String page, String pageCount) {
        String encoded = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(exam.getBytes(StandardCharsets.UTF_8));
        return "AZY3|" + encoded + "|" + setId + "|" + model + "|" + template
                + "|" + page + "|" + pageCount;
    }

    @Test public void parsesValidVersionedQr() {
        SheetQrPayload parsed = SheetQrPayload.parse(payload("آزمون نمونه", "set-123", "B", "T1", "2", "4"));
        assertNotNull(parsed);
        assertEquals("آزمون نمونه", parsed.examName);
        assertEquals("set-123", parsed.sheetSetId);
        assertEquals("B", parsed.modelId);
        assertEquals(2, parsed.page);
        assertEquals(4, parsed.pageCount);
    }

    @Test public void rejectsWrongFieldCountAndWrongPrefix() {
        assertNull(SheetQrPayload.parse("AZY3|abc|set|A|T1|1"));
        assertNull(SheetQrPayload.parse("OTHER|abc|set|A|T1|1|1"));
        assertNull(SheetQrPayload.parse(null));
    }

    @Test public void rejectsInvalidModelTemplateAndPageRange() {
        assertNull(SheetQrPayload.parse(payload("Exam", "set", "AA", "T1", "1", "1")));
        assertNull(SheetQrPayload.parse(payload("Exam", "set", "A", "T2", "1", "1")));
        assertNull(SheetQrPayload.parse(payload("Exam", "set", "A", "T1", "0", "1")));
        assertNull(SheetQrPayload.parse(payload("Exam", "set", "A", "T1", "2", "1")));
        assertNull(SheetQrPayload.parse(payload("Exam", "set", "A", "T1", "1", "51")));
    }

    @Test public void rejectsMalformedBase64AndEmptyIdentifiers() {
        assertNull(SheetQrPayload.parse("AZY3|%%%|set|A|T1|1|1"));
        assertNull(SheetQrPayload.parse(payload("Exam", "", "A", "T1", "1", "1")));
        String emptyExam = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("   ".getBytes(StandardCharsets.UTF_8));
        assertNull(SheetQrPayload.parse("AZY3|" + emptyExam + "|set|A|T1|1|1"));
    }

    @Test public void roundTripRetainsUnicodeExamName() {
        SheetQrPayload original = SheetQrPayload.parse(payload("آزمون-1", "set-x", "C", "T1", "1", "1"));
        assertNotNull(original);
        SheetQrPayload decodedAgain = SheetQrPayload.parse(String.join("|", original.toParts()));
        assertNotNull(decodedAgain);
        assertEquals(original.examName, decodedAgain.examName);
        assertEquals(original.sheetSetId, decodedAgain.sheetSetId);
    }
}
