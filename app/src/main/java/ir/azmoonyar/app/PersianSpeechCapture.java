package ir.azmoonyar.app;

import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.RecognitionListener;
import org.vosk.android.SpeechService;

/** One-shot on-device Persian speech capture used for participant names. */
public final class PersianSpeechCapture implements RecognitionListener {
    public interface Listener {
        void onText(String text);
        void onStatus(String status);
        void onError(String message);
    }

    private final String modelPath;
    private final Listener listener;
    private Model model;
    private SpeechService speechService;
    private volatile boolean delivered;
    private volatile String latestPartial = "";

    public PersianSpeechCapture(String modelPath, Listener listener) {
        this.modelPath = modelPath;
        this.listener = listener;
    }

    public void start() throws Exception {
        listener.onStatus("در حال بارگذاری مدل صوتی فارسی…");
        model = new Model(modelPath);
        Recognizer recognizer = new Recognizer(model, 16000.0f);
        speechService = new SpeechService(recognizer, 16000.0f);
        listener.onStatus("میکروفن آماده می‌شود؛ پس از پیام، نام را واضح بگو…");
        speechService.startListening(this);
        listener.onStatus("در حال شنیدن… نام و نام خانوادگی را واضح و با مکث کوتاه بگو.");
    }

    public void stop() {
        if (delivered) return;
        String partial = latestPartial.trim();
        if (!partial.isEmpty()) {
            delivered = true;
            stopResources();
            listener.onText(partial);
            return;
        }
        delivered = true;
        stopResources();
        listener.onError("صدای قابل‌تشخیصی دریافت نشد. مجوز میکروفن و صدای ورودی را بررسی کن؛ می‌توانی دوباره تلاش کنی یا نام را تایپ کنی.");
    }

    @Override public void onPartialResult(String hypothesis) {
        String text = extractText(hypothesis);
        if (!text.isEmpty()) {
            latestPartial = text;
            listener.onStatus("در حال تشخیص: " + text);
        }
    }

    @Override public void onResult(String hypothesis) {
        deliverRecognizedText(hypothesis);
    }

    @Override public void onFinalResult(String hypothesis) {
        deliverRecognizedText(hypothesis);
    }

    private String extractText(String hypothesis) {
        try {
            JSONObject result = new JSONObject(hypothesis);
            String text = result.optString("text", "").trim();
            // Vosk partial hypotheses use the "partial" field; final results use "text".
            if (text.isEmpty()) text = result.optString("partial", "").trim();
            return text;
        } catch (Exception ignored) {
            return "";
        }
    }

    private void deliverRecognizedText(String hypothesis) {
        if (delivered) return;
        String text = extractText(hypothesis);
        if (text.isEmpty()) text = latestPartial.trim();
        if (text.isEmpty()) return;
        delivered = true;
        stopResources();
        listener.onText(text);
    }

    @Override public void onError(Exception exception) {
        if (delivered) return;
        delivered = true;
        stopResources();
        String detail = exception == null ? "" : exception.getMessage();
        if (detail == null || detail.trim().isEmpty()) detail = exception == null ? "" : exception.getClass().getSimpleName();
        listener.onError("میکروفن یا موتور تشخیص گفتار خطا داد"
                + (detail.isEmpty() ? "." : " (" + detail + ").")
                + " مجوز میکروفن و تنظیمات ضبط صدا را بررسی کن.");
    }

    @Override public void onTimeout() {
        if (delivered) return;
        String partial = latestPartial.trim();
        if (!partial.isEmpty()) {
            delivered = true;
            stopResources();
            listener.onText(partial);
            return;
        }
        delivered = true;
        stopResources();
        listener.onError("در مدت شنیدن، گفتار قابل‌تشخیصی ثبت نشد. دکمه را دوباره بزن و نزدیک میکروفن، نام را واضح بگو.");
    }

    private void stopResources() {
        SpeechService service = speechService;
        speechService = null;
        if (service != null) {
            try { service.stop(); } catch (Exception ignored) { }
            try { service.shutdown(); } catch (Exception ignored) { }
        }
        Model loaded = model;
        model = null;
        if (loaded != null) {
            try { loaded.close(); } catch (Exception ignored) { }
        }
    }
}
