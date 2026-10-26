package ir.azmoonyar.app;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.vosk.Model;
import org.vosk.android.SpeechService;
import org.vosk.android.RecognitionListener;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Downloads and runs the Persian Vosk model locally; audio is not sent to Google. */
public final class PersianVosk {
    private static final String MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-fa-0.42.zip";
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public interface Callback {
        void onText(String text);
        void onStatus(String status);
    }

    private Model model;
    private SpeechService speechService;
    private boolean delivered;

    public PersianVosk() {}

    public static void ensureModel(Context context, Callback callback) {
        Context app = context.getApplicationContext();
        IO.execute(() -> {
            try {
                File dir = modelDir(app);
                if (!isModelReady(dir)) {
                    callbackStatus(callback, "در حال دانلود مدل فارسی Vosk (حدود ۵۳ مگابایت)…");
                    downloadAndExtract(dir);
                }
                callbackStatus(callback, "مدل آفلاین فارسی آماده است.");
            } catch (Exception e) {
                callbackStatus(callback, "دانلود مدل Vosk ناموفق بود؛ اینترنت و فضای خالی را بررسی کن.");
            }
        });
    }

    public static void prepareOnFirstLaunch(Context context, Callback callback) {
        ensureModel(context, callback);
    }

    public void start(Activity activity, Callback callback) {
        delivered = false;
        ensureModel(activity, new Callback() {
            @Override public void onText(String text) { callback.onText(text); }
            @Override public void onStatus(String status) {
                callback.onStatus(status);
                if (!status.equals("مدل آفلاین فارسی آماده است.")) return;
                IO.execute(() -> {
                    try {
                        model = new Model(modelDir(activity.getApplicationContext()).getAbsolutePath());
                        MAIN.post(() -> {
                            try {
                                speechService = new SpeechService(model, 16000.0f);
                                speechService.startListening(new RecognitionListener() {
                                    @Override public void onPartialResult(String hypothesis) {}
                                    @Override public void onResult(String hypothesis) { deliver(hypothesis, callback); }
                                    @Override public void onFinalResult(String hypothesis) { deliver(hypothesis, callback); }
                                    @Override public void onError(Exception e) { callback.onStatus("خطا در تشخیص صوت آفلاین؛ دوباره تلاش کن."); }
                                    @Override public void onTimeout() { callback.onStatus("زمان ضبط تمام شد؛ دوباره تلاش کن."); }
                                    private void deliver(String json, Callback cb) {
                                        if (delivered) return;
                                        try {
                                            org.json.JSONObject obj = new org.json.JSONObject(json);
                                            String text = obj.optString("text", "").trim();
                                            if (!text.isEmpty()) { delivered = true; cb.onText(text); }
                                        } catch (Exception ignored) {}
                                    }
                                });
                                callback.onStatus("گوش می‌دهم؛ نام را فارسی بگو.");
                            } catch (Exception e) { callback.onStatus("شروع میکروفون ناموفق بود."); }
                        });
                    } catch (Exception e) {
                        callbackStatus(callback, "بارگذاری مدل فارسی Vosk ناموفق بود.");
                    }
                });
            }
        });
    }

    public void stop() {
        try { if (speechService != null) speechService.stop(); } catch (Exception ignored) {}
        try { if (speechService != null) speechService.shutdown(); } catch (Exception ignored) {}
        speechService = null;
        try { if (model != null) model.close(); } catch (Exception ignored) {}
        model = null;
    }

    private static void callbackStatus(Callback callback, String status) {
        MAIN.post(() -> callback.onStatus(status));
    }

    private static File modelDir(Context context) {
        return new File(context.getFilesDir(), "vosk-model-fa");
    }

    private static boolean isModelReady(File dir) {
        return dir.isDirectory() && new File(dir, "am").isDirectory()
                && new File(dir, "conf").isDirectory() && new File(dir, "graph").isDirectory();
    }

    private static void downloadAndExtract(File target) throws Exception {
        File zip = new File(target.getParentFile(), "vosk-fa.zip");
        File temp = new File(target.getParentFile(), "vosk-fa-extract");
        deleteTree(temp);
        HttpURLConnection connection = (HttpURLConnection) new URL(MODEL_URL).openConnection();
        connection.setConnectTimeout(20000);
        connection.setReadTimeout(60000);
        connection.setInstanceFollowRedirects(true);
        try (InputStream in = new BufferedInputStream(connection.getInputStream());
             FileOutputStream out = new FileOutputStream(zip)) {
            byte[] buffer = new byte[32768]; int n;
            while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
        } finally { connection.disconnect(); }
        temp.mkdirs();
        try (ZipInputStream zin = new ZipInputStream(new BufferedInputStream(new java.io.FileInputStream(zip)))) {
            ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                String name = entry.getName();
                String prefix = "vosk-model-small-fa-0.42/";
                if (!name.startsWith(prefix)) { zin.closeEntry(); continue; }
                String relative = name.substring(prefix.length());
                if (relative.isEmpty()) { zin.closeEntry(); continue; }
                File outFile = new File(temp, relative);
                String canonicalRoot = temp.getCanonicalPath() + File.separator;
                if (!outFile.getCanonicalPath().startsWith(canonicalRoot)) throw new java.io.IOException("Invalid archive path");
                if (entry.isDirectory()) outFile.mkdirs();
                else {
                    File parent = outFile.getParentFile();
                    if (parent != null) parent.mkdirs();
                    try (FileOutputStream out = new FileOutputStream(outFile)) {
                        byte[] buffer = new byte[32768]; int n;
                        while ((n = zin.read(buffer)) != -1) out.write(buffer, 0, n);
                    }
                }
                zin.closeEntry();
            }
        }
        if (!isModelReady(temp)) { deleteTree(temp); throw new java.io.IOException("Model archive incomplete"); }
        deleteTree(target);
        if (!temp.renameTo(target)) throw new java.io.IOException("Could not install model");
        zip.delete();
    }

    private static void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) deleteTree(child);
        }
        file.delete();
    }
}
