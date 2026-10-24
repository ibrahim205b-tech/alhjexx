package iq.haji.alhjexx.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import iq.haji.alhjexx.core.AppException;
import iq.haji.alhjexx.core.CashDrawer;
import iq.haji.alhjexx.core.Money;
import iq.haji.alhjexx.model.Shift;
import iq.haji.alhjexx.model.User;

/**
 * الورديات. سياسة النظام: وردية مفتوحة واحدة فقط على الجهاز (مفروضة أيضاً بفهرس فريد).
 * الوردية المغلقة لا تُعدَّل ولا تُحذف؛ إعادة فتحها إجراء إداري مسجَّل بسببه.
 */
public final class ShiftRepo {

    private static final String COLS = "id,shift_no,user_id,cashier_name,opened_at,opening_cash,status,"
            + "closed_at,closed_by,counted_cash,expected_cash,difference,close_note";

    private final Context ctx;

    public ShiftRepo(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    private SQLiteDatabase db() {
        return Db.get(ctx).getWritableDatabase();
    }

    private static Shift read(Cursor c) {
        Shift s = new Shift();
        s.id = c.getLong(0);
        s.shiftNo = c.getLong(1);
        s.userId = c.getLong(2);
        s.cashierName = c.getString(3);
        s.openedAt = c.getLong(4);
        s.openingCash = c.getLong(5);
        s.status = c.getString(6);
        s.closedAt = c.getLong(7);
        s.closedBy = c.isNull(8) ? null : c.getString(8);
        s.countedCash = c.getLong(9);
        s.expectedCash = c.getLong(10);
        s.difference = c.getLong(11);
        s.closeNote = c.isNull(12) ? null : c.getString(12);
        return s;
    }

    public Shift openShift() {
        try (Cursor c = db().rawQuery("SELECT " + COLS + " FROM shifts WHERE status='OPEN' LIMIT 1", null)) {
            return c.moveToFirst() ? read(c) : null;
        }
    }

    public Shift get(long id) {
        try (Cursor c = db().rawQuery("SELECT " + COLS + " FROM shifts WHERE id=?",
                new String[]{String.valueOf(id)})) {
            return c.moveToFirst() ? read(c) : null;
        }
    }

    public List<Shift> recent(int limit) {
        List<Shift> out = new ArrayList<>();
        try (Cursor c = db().rawQuery("SELECT " + COLS + " FROM shifts ORDER BY id DESC LIMIT ?",
                new String[]{String.valueOf(limit)})) {
            while (c.moveToNext()) {
                out.add(read(c));
            }
        }
        return out;
    }

    /** يفتح وردية جديدة برقم تلقائي متسلسل. */
    public Shift open(User user, long openingCash) {
        if (user == null) {
            throw new AppException("سجّل الدخول أولاً");
        }
        if (openingCash < 0 || openingCash > Money.MAX) {
            throw new AppException("مبلغ النقدية الافتتاحية غير صالح");
        }
        SQLiteDatabase db = db();
        db.beginTransaction();
        try {
            if (openShift() != null) {
                throw new AppException("توجد وردية مفتوحة بالفعل. أغلقها قبل فتح وردية جديدة.");
            }
            long next = 1;
            try (Cursor c = db.rawQuery("SELECT COALESCE(MAX(shift_no),0)+1 FROM shifts", null)) {
                if (c.moveToFirst()) {
                    next = c.getLong(0);
                }
            }
            ContentValues cv = new ContentValues();
            cv.put("uuid", UUID.randomUUID().toString());
            cv.put("shift_no", next);
            cv.put("user_id", user.id);
            cv.put("cashier_name", user.name);
            cv.put("opened_at", System.currentTimeMillis());
            cv.put("opening_cash", openingCash);
            cv.put("status", Shift.OPEN);
            long id = db.insertOrThrow("shifts", null, cv);
            Audit.log(db, user.name, "فتح وردية", "#" + next + " نقدية افتتاحية " + Money.number(openingCash));
            db.setTransactionSuccessful();
            return get(id);
        } finally {
            db.endTransaction();
        }
    }

    /**
     * يغلق الوردية ويحفظ نتائجها نهائياً: المتوقع، الفعلي، الفرق، وملخص كامل بصيغة JSON.
     */
    public Shift close(long shiftId, long countedCash, String note, String byUser) {
        if (countedCash < 0 || countedCash > Money.MAX) {
            throw new AppException("المبلغ الفعلي غير صالح");
        }
        SQLiteDatabase db = db();
        db.beginTransaction();
        try {
            Shift s = get(shiftId);
            if (s == null || !s.isOpen()) {
                throw new AppException("الوردية غير مفتوحة");
            }
            ReportRepo.Summary sum = new ReportRepo(ctx).summary(ReportRepo.Filter.shift(shiftId));
            long expected = CashDrawer.expected(s.openingCash, sum.cashReceived, sum.refundsCash,
                    sum.expensesCash, sum.payIns, sum.payOuts);
            long now = System.currentTimeMillis();
            ContentValues cv = new ContentValues();
            cv.put("status", Shift.CLOSED);
            cv.put("closed_at", now);
            cv.put("closed_by", byUser);
            cv.put("counted_cash", countedCash);
            cv.put("expected_cash", expected);
            cv.put("difference", CashDrawer.difference(countedCash, expected));
            cv.put("close_note", note == null ? "" : note.trim());
            cv.put("summary_json", sum.toJson());
            db.update("shifts", cv, "id=? AND status='OPEN'", new String[]{String.valueOf(shiftId)});
            Audit.log(db, byUser, "إغلاق وردية", "#" + s.shiftNo + " متوقع " + Money.number(expected)
                    + " فعلي " + Money.number(countedCash));
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return get(shiftId);
    }

    /** إعادة فتح وردية مغلقة: مدير فقط، بسبب إلزامي، وتُحفظ نتائج الإغلاق السابقة في السجل. */
    public void reopen(long shiftId, String reason, User manager) {
        if (manager == null || !manager.isManager()) {
            throw new AppException("إعادة فتح الوردية تتطلب صلاحية مدير");
        }
        String r = reason == null ? "" : reason.trim();
        if (r.isEmpty()) {
            throw new AppException("أدخل سبب إعادة فتح الوردية");
        }
        SQLiteDatabase db = db();
        db.beginTransaction();
        try {
            Shift s = get(shiftId);
            if (s == null || s.isOpen()) {
                throw new AppException("الوردية ليست مغلقة");
            }
            if (openShift() != null) {
                throw new AppException("توجد وردية أخرى مفتوحة. أغلقها أولاً.");
            }
            String prev = null;
            try (Cursor c = db.rawQuery("SELECT summary_json FROM shifts WHERE id=?",
                    new String[]{String.valueOf(shiftId)})) {
                if (c.moveToFirst() && !c.isNull(0)) {
                    prev = c.getString(0);
                }
            }
            ContentValues log = new ContentValues();
            log.put("shift_id", shiftId);
            log.put("reopened_at", System.currentTimeMillis());
            log.put("user_name", manager.name);
            log.put("reason", r);
            log.put("previous_summary_json", "{\"expected\":" + s.expectedCash + ",\"counted\":" + s.countedCash
                    + ",\"difference\":" + s.difference + ",\"closed_at\":" + s.closedAt
                    + ",\"summary\":" + (prev == null ? "null" : prev) + "}");
            db.insertOrThrow("shift_reopens", null, log);

            ContentValues cv = new ContentValues();
            cv.put("status", Shift.OPEN);
            db.update("shifts", cv, "id=?", new String[]{String.valueOf(shiftId)});
            Audit.log(db, manager.name, "إعادة فتح وردية", "#" + s.shiftNo + " السبب: " + r);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public int reopenCount(long shiftId) {
        try (Cursor c = db().rawQuery("SELECT COUNT(*) FROM shift_reopens WHERE shift_id=?",
                new String[]{String.valueOf(shiftId)})) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }
}
