package iq.haji.alhjexx.data;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Set;

import iq.haji.alhjexx.core.AppException;

/**
 * النسخ الاحتياطي والاستعادة لملف قاعدة البيانات كاملاً (المبيعات، الورديات، المنيو، المستخدمون، الإعدادات).
 * الملف يُحفظ حيث يختار المستخدم عبر نافذة النظام، ولا يُرفع إلى الإنترنت.
 */
public final class BackupManager {

    private final Context ctx;

    public BackupManager(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    private File dbFile() {
        return ctx.getDatabasePath(Db.NAME);
    }

    /** يكتب نسخة من قاعدة البيانات إلى الملف الذي اختاره المستخدم. */
    public synchronized void backupTo(Uri target, String byUser) throws IOException {
        Audit.log(ctx, byUser, "نسخة احتياطية", "إنشاء نسخة احتياطية");
        // الإغلاق يضمن دمج سجل WAL في الملف الرئيسي قبل النسخ.
        Db.closeInstance();
        File src = dbFile();
        try (InputStream in = new FileInputStream(src);
             OutputStream out = ctx.getContentResolver().openOutputStream(target, "wt")) {
            if (out == null) {
                throw new IOException("تعذر فتح ملف الوجهة");
            }
            copy(in, out);
        }
    }

    /** نتيجة فحص ملف نسخة احتياطية. */
    public static final class Check {
        public boolean valid;
        public String message;
        public int orders;
        public int shifts;
        public int products;
    }

    /** ينسخ الملف المختار إلى مكان مؤقت ويفحصه دون المساس بالبيانات الحالية. */
    public File stage(Uri source) throws IOException {
        File tmp = new File(ctx.getCacheDir(), "restore_candidate.db");
        if (tmp.exists() && !tmp.delete()) {
            throw new IOException("تعذر تجهيز الملف المؤقت");
        }
        try (InputStream in = ctx.getContentResolver().openInputStream(source);
             OutputStream out = new FileOutputStream(tmp)) {
            if (in == null) {
                throw new IOException("تعذر قراءة الملف المختار");
            }
            copy(in, out);
        }
        return tmp;
    }

    public static Check verify(File file) {
        Check r = new Check();
        SQLiteDatabase db = null;
        try {
            db = SQLiteDatabase.openDatabase(file.getPath(), null, SQLiteDatabase.OPEN_READONLY);
            try (Cursor c = db.rawQuery("PRAGMA integrity_check", null)) {
                if (!c.moveToFirst() || !"ok".equalsIgnoreCase(c.getString(0))) {
                    r.message = "الملف تالف ولا يمكن استعادته";
                    return r;
                }
            }
            Set<String> tables = new HashSet<>();
            try (Cursor c = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null)) {
                while (c.moveToNext()) {
                    tables.add(c.getString(0));
                }
            }
            for (String t : Db.REQUIRED_TABLES) {
                if (!tables.contains(t)) {
                    r.message = "الملف ليس نسخة احتياطية من كاشير الحجي";
                    return r;
                }
            }
            try (Cursor c = db.rawQuery("SELECT value FROM settings WHERE key=?", new String[]{Db.SIGNATURE_KEY})) {
                if (!c.moveToFirst() || !Db.SIGNATURE.equals(c.getString(0))) {
                    r.message = "الملف ليس نسخة احتياطية من كاشير الحجي";
                    return r;
                }
            }
            if (db.getVersion() > Db.VERSION) {
                r.message = "النسخة الاحتياطية من إصدار أحدث من التطبيق. حدّث التطبيق أولاً.";
                return r;
            }
            r.orders = count(db, "orders");
            r.shifts = count(db, "shifts");
            r.products = count(db, "products");
            r.valid = true;
            r.message = "نسخة صالحة";
            return r;
        } catch (Exception e) {
            r.message = "تعذر فتح الملف كقاعدة بيانات";
            return r;
        } finally {
            if (db != null) {
                db.close();
            }
        }
    }

    private static int count(SQLiteDatabase db, String table) {
        try (Cursor c = db.rawQuery("SELECT COUNT(*) FROM " + table, null)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    /**
     * يستبدل البيانات الحالية بالنسخة المفحوصة. يجب استدعاء verify قبلها وعرض تحذير للمستخدم.
     * قبل الاستبدال تُحفظ نسخة من البيانات الحالية داخل مجلد التطبيق للطوارئ.
     */
    public synchronized void restore(File staged) throws IOException {
        Check check = verify(staged);
        if (!check.valid) {
            throw new AppException(check.message);
        }
        Db.closeInstance();
        File db = dbFile();
        File safety = new File(ctx.getFilesDir(), "before_restore.db");
        if (db.exists()) {
            try (InputStream in = new FileInputStream(db); OutputStream out = new FileOutputStream(safety)) {
                copy(in, out);
            }
        }
        deleteIfExists(new File(db.getPath() + "-wal"));
        deleteIfExists(new File(db.getPath() + "-shm"));
        deleteIfExists(new File(db.getPath() + "-journal"));
        try (InputStream in = new FileInputStream(staged); OutputStream out = new FileOutputStream(db, false)) {
            copy(in, out);
        }
        deleteIfExists(staged);
        Audit.log(ctx, null, "استعادة نسخة احتياطية",
                "طلبات " + check.orders + "، ورديات " + check.shifts + "، أصناف " + check.products);
    }

    private static void deleteIfExists(File f) {
        if (f.exists()) {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        }
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[64 * 1024];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
        out.flush();
    }
}
