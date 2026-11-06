package ir.azmoonyar.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** Versioned offline backup for user data. The downloaded Vosk model is intentionally excluded. */
public final class LocalBackupManager {
    private static final String[] PREF_FILES = {"azmoonyar_local", "azmoonyar_reports"};
    private static final String PHOTO_DIR = "answer_sheet_photos";
    private static final String MANIFEST = "azmoonyar-backup.json";
    private static final int FORMAT_VERSION = 1;
    private static final int MAX_MANIFEST_BYTES = 16 * 1024 * 1024;

    private LocalBackupManager() {}

    public static String exportBackup(Context context, Uri destination) {
        if (destination == null) return "مسیر ذخیره‌سازی انتخاب نشده است.";
        try (OutputStream raw = context.getContentResolver().openOutputStream(destination);
             ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(raw))) {
            if (raw == null) throw new java.io.IOException("output stream unavailable");
            JSONObject root = new JSONObject();
            root.put("format", "azmoonyar-local-backup");
            root.put("version", FORMAT_VERSION);
            root.put("createdAt", System.currentTimeMillis());
            JSONObject preferences = new JSONObject();
            for (String name : PREF_FILES) {
                SharedPreferences prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE);
                JSONObject values = new JSONObject();
                for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
                    values.put(entry.getKey(), encodeValue(entry.getValue()));
                }
                preferences.put(name, values);
            }
            root.put("preferences", preferences);
            putBytes(zip, MANIFEST, root.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));

            File photos = new File(context.getFilesDir(), PHOTO_DIR);
            if (photos.isDirectory()) addFiles(zip, photos, PHOTO_DIR + "/");
            zip.finish();
            return "پشتیبان‌گیری با موفقیت انجام شد. اطلاعات آزمون‌ها، نتایج، صف بازبینی و گزارش‌های محلی ذخیره شدند.";
        } catch (Exception e) {
            return "ساخت نسخه پشتیبان ناموفق بود: " + safeMessage(e);
        }
    }

    public static String inspectBackup(Context context, Uri source) {
        try (InputStream raw = context.getContentResolver().openInputStream(source);
             ZipInputStream zip = new ZipInputStream(new BufferedInputStream(raw))) {
            if (raw == null) throw new java.io.IOException("input stream unavailable");
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!MANIFEST.equals(entry.getName())) continue;
                JSONObject root = new JSONObject(readLimited(zip, MAX_MANIFEST_BYTES));
                validateManifest(root);
                JSONObject preferences = root.getJSONObject("preferences");
                int keys = 0;
                for (String name : PREF_FILES) keys += preferences.getJSONObject(name).length();
                return "نسخه پشتیبان معتبر است.\nنسخه قالب: " + root.getInt("version")
                        + "\nتعداد تنظیمات و داده‌های ثبت‌شده: " + keys
                        + "\nتاریخ ایجاد: " + root.optLong("createdAt");
            }
            return "فایل انتخاب‌شده، نسخه پشتیبان معتبر آزمون‌یار نیست.";
        } catch (Exception e) {
            return "بررسی نسخه پشتیبان ناموفق بود: " + safeMessage(e);
        }
    }

    public static String restoreBackup(Context context, Uri source) {
        File stagedPhotos = new File(context.getCacheDir(), "azmoonyar-restore-photos");
        File oldPhotos = new File(context.getCacheDir(), "azmoonyar-previous-photos");
        File livePhotos = new File(context.getFilesDir(), PHOTO_DIR);
        deleteTree(stagedPhotos);
        if (oldPhotos.exists()) {
            return "یک نسخه موقت از عکس‌های قبلی برای بازیابی ایمن باقی مانده است؛ ابتدا وضعیت آن باید بررسی شود.";
        }
        JSONObject manifest = null;
        boolean hasPhotos = false;
        try (InputStream raw = context.getContentResolver().openInputStream(source);
             ZipInputStream zip = new ZipInputStream(new BufferedInputStream(raw))) {
            if (raw == null) throw new java.io.IOException("input stream unavailable");
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (MANIFEST.equals(name)) {
                    manifest = new JSONObject(readLimited(zip, MAX_MANIFEST_BYTES));
                } else if (name.startsWith(PHOTO_DIR + "/") && !entry.isDirectory()) {
                    String relative = name.substring((PHOTO_DIR + "/").length());
                    if (relative.isEmpty() || relative.contains("..") || relative.startsWith("/")) {
                        throw new java.io.IOException("invalid photo path");
                    }
                    File out = new File(stagedPhotos, relative);
                    String rootPath = stagedPhotos.getCanonicalPath() + File.separator;
                    if (!out.getCanonicalPath().startsWith(rootPath)) throw new java.io.IOException("invalid photo path");
                    File parent = out.getParentFile();
                    if (parent != null && !parent.mkdirs() && !parent.isDirectory()) throw new java.io.IOException("cannot create photo folder");
                    try (FileOutputStream fileOut = new FileOutputStream(out)) {
                        copy(zip, fileOut);
                    }
                    hasPhotos = true;
                }
                zip.closeEntry();
            }
            if (manifest == null) throw new java.io.IOException("backup manifest missing");
            validateManifest(manifest);
            JSONObject preferences = manifest.getJSONObject("preferences");

            JSONObject oldLocal = encodePreferences(context.getSharedPreferences(PREF_FILES[0], Context.MODE_PRIVATE));
            JSONObject oldReports = encodePreferences(context.getSharedPreferences(PREF_FILES[1], Context.MODE_PRIVATE));
            boolean localChanged = false;
            boolean reportsChanged = false;
            boolean photosMoved = false;
            try {
                restorePreferences(context.getSharedPreferences(PREF_FILES[0], Context.MODE_PRIVATE),
                        preferences.getJSONObject(PREF_FILES[0]));
                localChanged = true;
                restorePreferences(context.getSharedPreferences(PREF_FILES[1], Context.MODE_PRIVATE),
                        preferences.getJSONObject(PREF_FILES[1]));
                reportsChanged = true;
                if (hasPhotos) {
                    if (livePhotos.exists() && !livePhotos.renameTo(oldPhotos)) {
                        throw new java.io.IOException("cannot preserve current photos before restore");
                    }
                    if (!stagedPhotos.renameTo(livePhotos)) {
                        if (oldPhotos.exists()) oldPhotos.renameTo(livePhotos);
                        throw new java.io.IOException("cannot install restored photos");
                    }
                    photosMoved = true;
                }
                deleteTree(oldPhotos);
                return "بازیابی با موفقیت انجام شد. داده‌های فعلی پیش از جایگزینی در حافظه موقت حفظ شده بودند.";
            } catch (Exception restoreError) {
                if (reportsChanged) restorePreferences(context.getSharedPreferences(PREF_FILES[1], Context.MODE_PRIVATE), oldReports);
                if (localChanged) restorePreferences(context.getSharedPreferences(PREF_FILES[0], Context.MODE_PRIVATE), oldLocal);
                if (photosMoved) {
                    deleteTree(livePhotos);
                    if (oldPhotos.exists()) oldPhotos.renameTo(livePhotos);
                }
                throw restoreError;
            }
        } catch (Exception e) {
            return "بازیابی انجام نشد و تا حد امکان اطلاعات قبلی حفظ شدند: " + safeMessage(e);
        } finally {
            deleteTree(stagedPhotos);
            // oldPhotos is deliberately retained if rollback could not move it back.
        }
    }

    private static JSONObject encodePreferences(SharedPreferences prefs) throws Exception {
        JSONObject values = new JSONObject();
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) values.put(entry.getKey(), encodeValue(entry.getValue()));
        return values;
    }

    private static JSONObject encodeValue(Object value) throws Exception {
        JSONObject item = new JSONObject();
        if (value instanceof Boolean) { item.put("type", "boolean"); item.put("value", value); }
        else if (value instanceof Integer) { item.put("type", "int"); item.put("value", value); }
        else if (value instanceof Long) { item.put("type", "long"); item.put("value", value); }
        else if (value instanceof Float) { item.put("type", "float"); item.put("value", value); }
        else if (value instanceof String) { item.put("type", "string"); item.put("value", value); }
        else if (value instanceof java.util.Set) {
            item.put("type", "string_set");
            JSONArray array = new JSONArray();
            for (Object member : (java.util.Set<?>) value) array.put(String.valueOf(member));
            item.put("value", array);
        } else throw new IllegalArgumentException("unsupported preference value");
        return item;
    }

    private static void restorePreferences(SharedPreferences prefs, JSONObject values) throws Exception {
        SharedPreferences.Editor editor = prefs.edit().clear();
        java.util.Iterator<String> keys = values.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            JSONObject item = values.getJSONObject(key);
            String type = item.getString("type");
            Object value = item.get("value");
            if ("boolean".equals(type)) editor.putBoolean(key, (Boolean) value);
            else if ("int".equals(type)) editor.putInt(key, ((Number) value).intValue());
            else if ("long".equals(type)) editor.putLong(key, ((Number) value).longValue());
            else if ("float".equals(type)) editor.putFloat(key, ((Number) value).floatValue());
            else if ("string".equals(type)) editor.putString(key, (String) value);
            else if ("string_set".equals(type)) {
                java.util.Set<String> set = new java.util.HashSet<>();
                JSONArray array = (JSONArray) value;
                for (int i = 0; i < array.length(); i++) set.add(array.getString(i));
                editor.putStringSet(key, set);
            } else throw new IllegalArgumentException("unsupported preference type");
        }
        if (!editor.commit()) throw new java.io.IOException("could not commit preferences");
    }

    private static void validateManifest(JSONObject root) throws Exception {
        if (!"azmoonyar-local-backup".equals(root.optString("format"))
                || root.optInt("version", -1) != FORMAT_VERSION) {
            throw new java.io.IOException("unsupported backup format or version");
        }
        JSONObject preferences = root.getJSONObject("preferences");
        for (String name : PREF_FILES) preferences.getJSONObject(name);
    }

    private static void addFiles(ZipOutputStream zip, File folder, String prefix) throws Exception {
        File[] files = folder.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isDirectory()) addFiles(zip, file, prefix + file.getName() + "/");
            else {
                zip.putNextEntry(new ZipEntry(prefix + file.getName()));
                try (InputStream in = new FileInputStream(file)) { copy(in, zip); }
                zip.closeEntry();
            }
        }
    }

    private static void putBytes(ZipOutputStream zip, String name, byte[] bytes) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(bytes);
        zip.closeEntry();
    }

    private static String readLimited(InputStream in, int limit) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = in.read(buffer)) != -1) {
            if (out.size() + count > limit) throw new java.io.IOException("backup manifest too large");
            out.write(buffer, 0, count);
        }
        return out.toString("UTF-8");
    }

    private static void copy(InputStream in, OutputStream out) throws Exception {
        byte[] buffer = new byte[32768];
        int count;
        while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
    }

    private static void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) deleteTree(child);
        }
        file.delete();
    }

    private static String safeMessage(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }
}
