package iq.haji.alhjexx.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/** إعدادات التطبيق محفوظة داخل قاعدة البيانات حتى تُشملها النسخة الاحتياطية. */
public final class Settings {

    public static final String RESTAURANT_NAME = "restaurant_name";
    public static final String RESTAURANT_PHONE = "restaurant_phone";
    public static final String RECEIPT_FOOTER = "receipt_footer";
    public static final String TABLES_ENABLED = "tables_enabled";
    public static final String CARD_ENABLED = "card_enabled";
    public static final String MIXED_ENABLED = "mixed_enabled";
    public static final String DISCOUNT_ENABLED = "discount_enabled";
    public static final String CASHIER_DISCOUNT = "cashier_discount_allowed";
    public static final String CASHIER_DISCOUNT_MAX_PERCENT = "cashier_discount_max_percent";
    public static final String EXPENSES_ENABLED = "expenses_enabled";
    public static final String PRINTER_ADDRESS = "printer_address";
    public static final String PRINTER_NAME = "printer_name";
    public static final String AUTO_PRINT = "auto_print_after_confirm";
    public static final String RECEIPT_COPIES = "receipt_copies";
    public static final String PRINT_FEED_LINES = "print_feed_lines";
    public static final String PRINT_CUT = "print_cut";

    public static final String DEFAULT_RESTAURANT_NAME = "مطعم الحجي";
    public static final String DEFAULT_FOOTER = "شكراً لزيارتكم";

    private final Context ctx;

    public Settings(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    private SQLiteDatabase db() {
        return Db.get(ctx).getWritableDatabase();
    }

    public String get(String key, String def) {
        try (Cursor c = db().rawQuery("SELECT value FROM settings WHERE key=?", new String[]{key})) {
            if (c.moveToFirst() && !c.isNull(0)) {
                return c.getString(0);
            }
        }
        return def;
    }

    public void set(String key, String value) {
        ContentValues cv = new ContentValues();
        cv.put("key", key);
        cv.put("value", value);
        db().insertWithOnConflict("settings", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public boolean getBool(String key, boolean def) {
        String v = get(key, null);
        return v == null ? def : "1".equals(v);
    }

    public void setBool(String key, boolean value) {
        set(key, value ? "1" : "0");
    }

    public int getInt(String key, int def) {
        try {
            return Integer.parseInt(get(key, String.valueOf(def)));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public void setInt(String key, int value) {
        set(key, String.valueOf(value));
    }

    public String restaurantName() {
        String n = get(RESTAURANT_NAME, DEFAULT_RESTAURANT_NAME).trim();
        return n.isEmpty() ? DEFAULT_RESTAURANT_NAME : n;
    }
}
