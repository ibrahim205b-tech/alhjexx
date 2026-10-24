package iq.haji.alhjexx.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

import iq.haji.alhjexx.core.AppException;
import iq.haji.alhjexx.model.User;
import iq.haji.alhjexx.security.PinHasher;

public final class UserRepo {

    private final Context ctx;

    public UserRepo(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    private SQLiteDatabase db() {
        return Db.get(ctx).getWritableDatabase();
    }

    private static User read(Cursor c) {
        User u = new User();
        u.id = c.getLong(0);
        u.name = c.getString(1);
        u.role = c.getString(2);
        u.active = c.getInt(3) == 1;
        return u;
    }

    public boolean hasManager() {
        try (Cursor c = db().rawQuery(
                "SELECT COUNT(*) FROM users WHERE role='MANAGER' AND active=1", null)) {
            return c.moveToFirst() && c.getInt(0) > 0;
        }
    }

    public List<User> list(boolean onlyActive) {
        List<User> out = new ArrayList<>();
        try (Cursor c = db().rawQuery("SELECT id,name,role,active FROM users"
                + (onlyActive ? " WHERE active=1" : "") + " ORDER BY role DESC, name", null)) {
            while (c.moveToNext()) {
                out.add(read(c));
            }
        }
        return out;
    }

    public User get(long id) {
        try (Cursor c = db().rawQuery("SELECT id,name,role,active FROM users WHERE id=?",
                new String[]{String.valueOf(id)})) {
            return c.moveToFirst() ? read(c) : null;
        }
    }

    private static String cleanName(String name) {
        String n = name == null ? "" : name.trim();
        if (n.isEmpty()) {
            throw new AppException("أدخل اسم المستخدم");
        }
        if (n.length() > 40) {
            throw new AppException("الاسم طويل جداً");
        }
        return n;
    }

    public long create(String name, String role, String pin, String byUser) {
        String n = cleanName(name);
        if (!User.MANAGER.equals(role) && !User.CASHIER.equals(role)) {
            throw new AppException("دور غير صالح");
        }
        if (!PinHasher.isValidPin(pin)) {
            throw new AppException("الرمز يجب أن يكون من 4 إلى 8 أرقام");
        }
        String salt = PinHasher.newSalt();
        ContentValues cv = new ContentValues();
        cv.put("name", n);
        cv.put("role", role);
        cv.put("pin_salt", salt);
        cv.put("pin_hash", PinHasher.hash(pin, salt));
        cv.put("active", 1);
        cv.put("created_at", System.currentTimeMillis());
        long id = db().insert("users", null, cv);
        if (id < 0) {
            throw new AppException("تعذر حفظ المستخدم");
        }
        Audit.log(db(), byUser, "إضافة مستخدم", n + " - " + User.roleLabel(role));
        return id;
    }

    /** يتحقق من رمز مستخدم محدد. */
    public boolean verify(long userId, String pin) {
        try (Cursor c = db().rawQuery(
                "SELECT pin_salt,pin_hash FROM users WHERE id=? AND active=1",
                new String[]{String.valueOf(userId)})) {
            return c.moveToFirst() && PinHasher.verify(pin, c.getString(0), c.getString(1));
        }
    }

    /** يبحث عن مدير فعّال يطابق الرمز. يعيد null إذا لم يطابق أي مدير. */
    public User findManagerByPin(String pin) {
        for (User u : list(true)) {
            if (u.isManager() && verify(u.id, pin)) {
                return u;
            }
        }
        return null;
    }

    public void changePin(long userId, String newPin, String byUser) {
        if (!PinHasher.isValidPin(newPin)) {
            throw new AppException("الرمز يجب أن يكون من 4 إلى 8 أرقام");
        }
        String salt = PinHasher.newSalt();
        ContentValues cv = new ContentValues();
        cv.put("pin_salt", salt);
        cv.put("pin_hash", PinHasher.hash(newPin, salt));
        db().update("users", cv, "id=?", new String[]{String.valueOf(userId)});
        User u = get(userId);
        Audit.log(db(), byUser, "تغيير رمز", u == null ? "#" + userId : u.name);
    }

    public void rename(long userId, String name, String byUser) {
        String n = cleanName(name);
        ContentValues cv = new ContentValues();
        cv.put("name", n);
        db().update("users", cv, "id=?", new String[]{String.valueOf(userId)});
        Audit.log(db(), byUser, "تعديل اسم مستخدم", n);
    }

    /** تعطيل/تفعيل. لا يُسمح بتعطيل آخر مدير فعّال. المستخدمون لا يُحذفون لأن الطلبات مرتبطة بهم. */
    public void setActive(long userId, boolean active, String byUser) {
        User u = get(userId);
        if (u == null) {
            throw new AppException("المستخدم غير موجود");
        }
        if (!active && u.isManager()) {
            int managers = 0;
            for (User x : list(true)) {
                if (x.isManager()) {
                    managers++;
                }
            }
            if (managers <= 1) {
                throw new AppException("لا يمكن تعطيل آخر مدير في النظام");
            }
        }
        ContentValues cv = new ContentValues();
        cv.put("active", active ? 1 : 0);
        db().update("users", cv, "id=?", new String[]{String.valueOf(userId)});
        Audit.log(db(), byUser, active ? "تفعيل مستخدم" : "تعطيل مستخدم", u.name);
    }
}
