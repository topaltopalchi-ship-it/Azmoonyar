package ir.azmoonyar.app;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

/**
 * Offline-first local report inbox and privacy gates.
 *
 * Reports stay on-device. External delivery is intentionally not implemented here.
 * Guardian delivery must not be enabled until a real verification flow has succeeded.
 */
public final class ReportStore {
    private static final String PREFS = "azmoonyar_reports";
    private static final String KEY_INBOX = "inbox_json";
    private final SharedPreferences prefs;

    public ReportStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public JSONArray getInbox() {
        try { return new JSONArray(prefs.getString(KEY_INBOX, "[]")); }
        catch (Exception ignored) { return new JSONArray(); }
    }

    /** Stores a local in-app report once analysis has produced a real result. */
    public synchronized String addLocalReport(String personId, String title, String body, String reportJson) {
        String id = UUID.randomUUID().toString();
        JSONArray inbox = getInbox();
        JSONObject item = new JSONObject();
        try {
            item.put("id", id);
            item.put("person_id", personId == null ? "" : personId);
            item.put("title", title == null ? "گزارش ارزیابی" : title);
            item.put("body", body == null ? "" : body);
            item.put("report_json", reportJson == null ? "{}" : reportJson);
            item.put("created_at", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT).format(new Date()));
            item.put("read", false);
            inbox.put(item);
            prefs.edit().putString(KEY_INBOX, inbox.toString()).apply();
            return id;
        } catch (Exception ignored) {
            return null;
        }
    }

    public synchronized void markRead(String reportId) {
        JSONArray inbox = getInbox();
        for (int i = 0; i < inbox.length(); i++) {
            JSONObject item = inbox.optJSONObject(i);
            if (item != null && reportId.equals(item.optString("id"))) {
                try { item.put("read", true); } catch (Exception ignored) { }
            }
        }
        prefs.edit().putString(KEY_INBOX, inbox.toString()).apply();
    }

    public boolean isGuardianReportAllowed(String personId) {
        // Fail closed: no guardian report until a registered guardian's contact
        // has been verified by a future OTP/provider-backed workflow and consent exists.
        String key = "guardian_" + personId;
        return prefs.getBoolean(key + "_registered", false)
                && prefs.getBoolean(key + "_contact_verified", false)
                && prefs.getBoolean(key + "_consent", false);
    }

    public void setGuardianRegistration(String personId, boolean registered, boolean consent) {
        String key = "guardian_" + personId;
        prefs.edit().putBoolean(key + "_registered", registered)
                .putBoolean(key + "_consent", consent)
                // Never infer verification from registration or consent.
                .putBoolean(key + "_contact_verified", false).apply();
    }

    /** Reserved for a verified provider/OTP callback; do not call from UI input. */
    public void setGuardianContactVerifiedAfterTrustedVerification(String personId) {
        String key = "guardian_" + personId;
        if (prefs.getBoolean(key + "_registered", false) && prefs.getBoolean(key + "_consent", false)) {
            prefs.edit().putBoolean(key + "_contact_verified", true).apply();
        }
    }
}
