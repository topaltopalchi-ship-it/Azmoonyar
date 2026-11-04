package ir.azmoonyar.app;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import org.junit.Test;

import java.util.EnumMap;

import static org.junit.Assert.assertEquals;

public class ParticipantQrCodeTest {
    @Test public void persianParticipantPayloadSurvivesQrRoundTrip() throws Exception {
        String payload = "{\"participant_id\":\"ebd77ae1-f494-42e6-9259-fd848df1a1f8\","
                + "\"participant_name\":\"نام شرکت‌کننده\","
                + "\"exam_name\":\"نام آزمون\","
                + "\"model_id\":\"B\","
                + "\"sheet_set_id\":\"roster-ebd77ae1-f494-42e6-9259-fd848df1a1f8\"}";
        EnumMap<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        BitMatrix matrix = new QRCodeWriter().encode(
                payload, BarcodeFormat.QR_CODE, 320, 320, hints);
        int[] pixels = new int[320 * 320];
        for (int y = 0; y < 320; y++) {
            for (int x = 0; x < 320; x++) {
                pixels[y * 320 + x] = matrix.get(x, y) ? 0xff000000 : 0xffffffff;
            }
        }
        BinaryBitmap bitmap = new BinaryBitmap(
                new HybridBinarizer(new RGBLuminanceSource(320, 320, pixels)));
        Result decoded = new MultiFormatReader().decode(bitmap);
        assertEquals(payload, decoded.getText());
    }
}