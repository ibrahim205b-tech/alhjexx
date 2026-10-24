package iq.haji.alhjexx.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

import iq.haji.alhjexx.core.AppException;
import iq.haji.alhjexx.core.Money;
import iq.haji.alhjexx.model.CashAdjustment;
import iq.haji.alhjexx.model.Expense;
import iq.haji.alhjexx.model.Shift;

/** المصروفات النقدية والتسويات (إيداع/سحب) المرتبطة بالوردية المفتوحة. */
public final class CashRepo {

    private final Context ctx;

    public CashRepo(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    private SQLiteDatabase db() {
        return Db.get(ctx).getWritableDatabase();
    }

    private Shift requireOpenShift() {
        Shift s = new ShiftRepo(ctx).openShift();
        if (s == null) {
            throw new AppException("لا توجد وردية مفتوحة");
        }
        return s;
    }

    public long addExpense(long amount, String category, String description, String user) {
        if (amount <= 0 || amount > Money.MAX) {
            throw new AppException("أدخل مبلغاً صحيحاً");
        }
        String cat = category == null ? "" : category.trim();
        if (cat.isEmpty()) {
            throw new AppException("اختر تصنيف المصروف");
        }
        Shift s = requireOpenShift();
        ContentValues cv = new ContentValues();
        cv.put("shift_id", s.id);
        cv.put("amount", amount);
        cv.put("category", cat);
        cv.put("description", description == null ? "" : description.trim());
        cv.put("user_name", user);
        cv.put("created_at", System.currentTimeMillis());
        long id = db().insertOrThrow("expenses", null, cv);
        Audit.log(db(), user, "مصروف", cat + " " + Money.number(amount));
        return id;
    }

    public List<Expense> expenses(long shiftId) {
        List<Expense> out = new ArrayList<>();
        try (Cursor c = db().rawQuery("SELECT id,shift_id,amount,category,description,user_name,created_at "
                + "FROM expenses WHERE shift_id=? ORDER BY id", new String[]{String.valueOf(shiftId)})) {
            while (c.moveToNext()) {
                Expense e = new Expense();
                e.id = c.getLong(0);
                e.shiftId = c.getLong(1);
                e.amount = c.getLong(2);
                e.category = c.getString(3);
                e.description = c.getString(4);
                e.userName = c.getString(5);
                e.createdAt = c.getLong(6);
                out.add(e);
            }
        }
        return out;
    }

    public long addAdjustment(String direction, long amount, String reason, String user) {
        if (!CashAdjustment.IN.equals(direction) && !CashAdjustment.OUT.equals(direction)) {
            throw new AppException("نوع تسوية غير صالح");
        }
        if (amount <= 0 || amount > Money.MAX) {
            throw new AppException("أدخل مبلغاً صحيحاً");
        }
        String r = reason == null ? "" : reason.trim();
        if (r.isEmpty()) {
            throw new AppException("أدخل سبب التسوية");
        }
        Shift s = requireOpenShift();
        ContentValues cv = new ContentValues();
        cv.put("shift_id", s.id);
        cv.put("direction", direction);
        cv.put("amount", amount);
        cv.put("reason", r);
        cv.put("user_name", user);
        cv.put("created_at", System.currentTimeMillis());
        long id = db().insertOrThrow("cash_adjustments", null, cv);
        Audit.log(db(), user, CashAdjustment.IN.equals(direction) ? "إيداع نقدي" : "سحب نقدي",
                Money.number(amount) + " - " + r);
        return id;
    }

    public List<CashAdjustment> adjustments(long shiftId) {
        List<CashAdjustment> out = new ArrayList<>();
        try (Cursor c = db().rawQuery("SELECT id,shift_id,direction,amount,reason,user_name,created_at "
                + "FROM cash_adjustments WHERE shift_id=? ORDER BY id", new String[]{String.valueOf(shiftId)})) {
            while (c.moveToNext()) {
                CashAdjustment a = new CashAdjustment();
                a.id = c.getLong(0);
                a.shiftId = c.getLong(1);
                a.direction = c.getString(2);
                a.amount = c.getLong(3);
                a.reason = c.getString(4);
                a.userName = c.getString(5);
                a.createdAt = c.getLong(6);
                out.add(a);
            }
        }
        return out;
    }
}
