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

    public PersianSpeechCapture(String modelPath, Listener listener) {
        this.modelPath = modelPath;
        this.listener = listener;
    }

    public void start() throws Exception {
        model = new Model(modelPath);
        Recognizer recognizer = new Recognizer(model, 16000.0f);
        speechService = new SpeechService(recognizer, 16000.0f);
        speechService.startListening(this);
        listener.onStatus("اکنون نام را بگو…");
    }

    public void stop() {
        stopResources();
        if (!delivered) {
            delivered = true;
            listener.onError("گفتار واضحی دریافت نشد؛ نام را تایپ کن یا دوباره تلاش کن.");
        }
    }

    @Override public void onPartialResult(String hypothesis) { }
    @Override public void onResult(String hypothesis) { }

    @Override public void onFinalResult(String hypothesis) {
        if (delivered) return;
        delivered = true;
        String text = "";
        try { text = new JSONObject(hypothesis).optString("text", "").trim(); }
        catch (Exception ignored) { }
        stopResources();
        if (text.isEmpty()) listener.onError("نام تشخیص داده نشد؛ لطفاً واضح‌تر بگو یا تایپ کن.");
        else listener.onText(text);
    }

    @Override public void onError(Exception exception) {
        if (delivered) return;
        delivered = true;
        stopResources();
        listener.onError("خطا در تشخیص گفتار؛ می‌توانی نام را تایپ کنی.");
    }

    @Override public void onTimeout() {
        if (delivered) return;
        delivered = true;
        stopResources();
        listener.onError("زمان شنیدن گفتار تمام شد؛ دوباره تلاش کن.");
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
