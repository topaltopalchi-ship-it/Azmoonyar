package ir.azmoonyar.app;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.vosk.Model;
import org.vosk.Recognizer;
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

/** Downloads and runs the Persian speech model locally; audio is not sent to Google. */
public final class PersianVosk {
    private static final String MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-fa-0.42.zip";
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public interface Callback {
        void onText(String text);
        void onStatus(String status);
    }

    private Model model;
    private Recognizer recognizer;
    private SpeechService speechService;
    private volatile boolean delivered;

    public PersianVosk() {}

    public static void ensureModel(Context context, Callback callback) {
        Context app = context.getApplicationContext();
        IO.execute(() -> {
            try {
                File dir = modelDir(app);
                if (!isModelReady(dir)) {
                    callbackStatus(callback, "در حال دریافت مدل صوتی فارسی؛ لطفاً اینترنت را روشن نگه دار.");
                    downloadAndExtract(dir, callback);
                }
                callbackStatus(callback, "مدل آفلاین فارسی آماده است.");
            } catch (Exception e) {
                String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                callbackStatus(callback, "دریافت مدل صوتی فارسی ناموفق بود: " + reason + "؛ اینترنت را بررسی کن و دوباره تلاش کن.");
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
                                recognizer = new Recognizer(model, 16000.0f);
                                speechService = new SpeechService(recognizer, 16000.0f);
                                speechService.startListening(new RecognitionListener() {
                                    @Override public void onPartialResult(String hypothesis) {
                                        try {
                                            org.json.JSONObject obj = new org.json.JSONObject(hypothesis);
                                            String partial = obj.optString("partial", "").trim();
                                            if (!partial.isEmpty() && !delivered) {
                                                callback.onStatus("صدای شما دریافت شد: " + partial);
                                            }
                                        } catch (Exception ignored) {}
                                    }
                                    @Override public void onResult(String hypothesis) { deliver(hypothesis, callback); }
                                    @Override public void onFinalResult(String hypothesis) { deliver(hypothesis, callback); }
                                    @Override public void onError(Exception e) {
                                        String detail = e == null ? "" : e.getClass().getSimpleName()
                                                + (e.getMessage() == null ? "" : ": " + e.getMessage());
                                        callback.onStatus("خطای میکروفون/تشخیص صوت: " + detail);
                                    }
                                    @Override public void onTimeout() {
                                        callback.onStatus("ضبط بدون دریافت گفتار پایان یافت؛ اجازه میکروفون و کلید حریم خصوصی میکروفون را بررسی کن و دوباره بزن.");
                                    }
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
                        callbackStatus(callback, "بارگذاری مدل صوتی فارسی ناموفق بود.");
                    }
                });
            }
        });
    }

    public void stop() {
        try { if (speechService != null) speechService.stop(); } catch (Exception ignored) {}
        try { if (speechService != null) speechService.shutdown(); } catch (Exception ignored) {}
        speechService = null;
        try { if (recognizer != null) recognizer.close(); } catch (Exception ignored) {}
        recognizer = null;
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

    private static void downloadAndExtract(File target, Callback callback) throws Exception {
        File parent = target.getParentFile();
        File zip = new File(parent, "vosk-fa.zip");
        File partial = new File(parent, "vosk-fa.zip.part");
        File temp = new File(parent, "vosk-fa-extract");
        deleteTree(temp);
        zip.delete();
        partial.delete();

        Exception lastError = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            HttpURLConnection connection = null;
            callbackStatus(callback, "در حال اتصال برای دریافت مدل صوتی فارسی؛ تلاش " + attempt + " از 3...");
            try {
                URL url = new URL(MODEL_URL);
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(30000);
                connection.setReadTimeout(30000);
                connection.setInstanceFollowRedirects(true);
                connection.setRequestProperty("User-Agent", "Azmoonyar-Android/0.2");
                connection.setRequestProperty("Accept-Encoding", "identity");
                int response = connection.getResponseCode();
                if (response != HttpURLConnection.HTTP_OK) {
                    throw new java.io.IOException("پاسخ ناموفق سرور: HTTP " + response);
                }
                callbackStatus(callback, "ارتباط برقرار شد؛ منتظر دریافت اولین داده از سرور هستیم.");
                try (InputStream in = new BufferedInputStream(connection.getInputStream());
                     FileOutputStream out = new FileOutputStream(partial)) {
                    byte[] buffer = new byte[65536];
                    int n;
                    long downloaded = 0L;
                    long total = connection.getContentLengthLong();
                    long nextNotice = 256L * 1024L;
                    boolean firstBytesReported = false;
                    while ((n = in.read(buffer)) != -1) {
                        out.write(buffer, 0, n);
                        downloaded += n;
                        if (!firstBytesReported) {
                            firstBytesReported = true;
                            callbackStatus(callback, "دریافت مدل صوتی فارسی شروع شد.");
                        }
                        if (downloaded >= nextNotice) {
                            String progress = total > 0
                                ? String.format(java.util.Locale.US, "در حال دریافت مدل صوتی فارسی: %.1f از %.1f مگابایت", downloaded / 1048576.0, total / 1048576.0)
                                : String.format(java.util.Locale.US, "دریافت مدل صوتی فارسی: %.1f مگابایت", downloaded / 1048576.0);
                            callbackStatus(callback, progress);
                            nextNotice = downloaded + 256L * 1024L;
                        }
                    }
                }
                if (!partial.isFile() || partial.length() < 1000000L) {
                    throw new java.io.IOException("سرور فایل کامل مدل صوتی فارسی را نفرستاد؛ دریافت دوباره تلاش می‌شود.");
                }
                if (!partial.renameTo(zip)) {
                    throw new java.io.IOException("could not finalize downloaded ZIP");
                }
                lastError = null;
                break;
            } catch (Exception e) {
                lastError = e;
                partial.delete();
                callbackStatus(callback, "تلاش " + attempt + " برای دریافت مدل صوتی فارسی با خطا روبه‌رو شد: "
                        + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
                if (attempt < 3) {
                    try { Thread.sleep(1500L * attempt); }
                    catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new java.io.IOException("download interrupted", interrupted);
                    }
                }
            } finally {
                if (connection != null) connection.disconnect();
            }
        }
        if (lastError != null) throw new java.io.IOException(
                "پس از ۳ تلاش، دریافت مدل صوتی فارسی کامل نشد (" + lastError.getMessage() + ")", lastError);

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
                if (!outFile.getCanonicalPath().startsWith(canonicalRoot)) {
                    throw new java.io.IOException("invalid model archive path");
                }
                if (entry.isDirectory()) outFile.mkdirs();
                else {
                    File outputParent = outFile.getParentFile();
                    if (outputParent != null) outputParent.mkdirs();
                    try (FileOutputStream out = new FileOutputStream(outFile)) {
                        byte[] buffer = new byte[32768];
                        int n;
                        while ((n = zin.read(buffer)) != -1) out.write(buffer, 0, n);
                    }
                }
                zin.closeEntry();
            }
        } catch (Exception e) {
            deleteTree(temp);
            throw new java.io.IOException("could not extract model ZIP: " + e.getMessage(), e);
        }
        if (!isModelReady(temp)) {
            deleteTree(temp);
            throw new java.io.IOException("model ZIP does not contain the expected Persian model files");
        }
        deleteTree(target);
        if (!temp.renameTo(target)) throw new java.io.IOException("could not install model");
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
