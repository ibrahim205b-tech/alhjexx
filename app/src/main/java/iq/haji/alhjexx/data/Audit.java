package iq.haji.alhjexx.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

import iq.haji.alhjexx.core.Dates;

/** سجل العمليات الإدارية والحساسة. لا يُعدَّل ولا يُحذف من داخل التطبيق. */
public final class Audit {

    private Audit() {
    }

    public static void log(Context ctx, String userName, String action, String details) {
        log(Db.get(ctx).getWritableDatabase(), userName, action, details);
    }

    public static void log(SQLiteDatabase db, String userName, String action, String details) {
        ContentValues cv = new ContentValues();
        cv.put("at", System.currentTimeMillis());
        cv.put("user_name", userName);
        cv.put("action", action);
        cv.put("details", details);
        db.insert("audit_log", null, cv);
    }

    public static List<String> recent(Context ctx, int limit) {
        List<String> out = new ArrayList<>();
        try (Cursor c = Db.get(ctx).getReadableDatabase().rawQuery(
                "SELECT at,user_name,action,details FROM audit_log ORDER BY id DESC LIMIT ?",
                new String[]{String.valueOf(limit)})) {
            while (c.moveToNext()) {
                out.add(Dates.dateTime(c.getLong(0)) + "  •  " + (c.isNull(1) ? "-" : c.getString(1))
                        + "\n" + c.getString(2) + (c.isNull(3) ? "" : ": " + c.getString(3)));
            }
        }
        return out;
    }
}
