package iq.haji.alhjexx.data;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * قاعدة البيانات المحلية (SQLite). كل البيانات تبقى على الجهاز ولا تُرسل إلى أي خادم.
 * المبالغ أعداد صحيحة بالدينار. الطلبات والورديات لا تُحذف أبداً.
 */
public final class Db extends SQLiteOpenHelper {

    public static final String NAME = "alhjexx.db";
    public static final int VERSION = 2;

    /** علامة تُكتب في الإعدادات للتحقق من أن ملف النسخة الاحتياطية يخص هذا التطبيق. */
    public static final String SIGNATURE_KEY = "db_signature";
    public static final String SIGNATURE = "iq.haji.alhjexx";

    /** الجداول التي يجب وجودها في أي نسخة احتياطية صالحة. */
    public static final String[] REQUIRED_TABLES = {
            "categories", "products", "product_sizes", "users", "shifts", "shift_reopens",
            "orders", "order_items", "payments", "cancellations", "refunds",
            "expenses", "cash_adjustments", "audit_log", "settings"
    };

    private static volatile Db instance;

    public static Db get(Context context) {
        if (instance == null) {
            synchronized (Db.class) {
                if (instance == null) {
                    instance = new Db(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    /** يغلق الاتصال الحالي (يُستخدم قبل استعادة نسخة احتياطية). */
    public static synchronized void closeInstance() {
        if (instance != null) {
            instance.close();
            instance = null;
        }
    }

    private Db(Context context) {
        super(context, NAME, null, VERSION);
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE settings ("
                + "key TEXT PRIMARY KEY,"
                + "value TEXT)");

        db.execSQL("CREATE TABLE categories ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "name TEXT NOT NULL,"
                + "sort_order INTEGER NOT NULL DEFAULT 0,"
                + "active INTEGER NOT NULL DEFAULT 1,"
                + "archived INTEGER NOT NULL DEFAULT 0,"
                + "created_at INTEGER NOT NULL)");

        db.execSQL("CREATE TABLE products ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "category_id INTEGER NOT NULL REFERENCES categories(id),"
                + "name TEXT NOT NULL,"
                + "price INTEGER NOT NULL CHECK(price >= 0),"
                + "active INTEGER NOT NULL DEFAULT 1,"
                + "archived INTEGER NOT NULL DEFAULT 0,"
                + "sort_order INTEGER NOT NULL DEFAULT 0,"
                + "created_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX idx_products_cat ON products(category_id)");

        db.execSQL("CREATE TABLE product_sizes ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "product_id INTEGER NOT NULL REFERENCES products(id),"
                + "name TEXT NOT NULL,"
                + "price INTEGER NOT NULL CHECK(price >= 0),"
                + "sort_order INTEGER NOT NULL DEFAULT 0,"
                + "active INTEGER NOT NULL DEFAULT 1)");
        db.execSQL("CREATE INDEX idx_sizes_product ON product_sizes(product_id)");

        db.execSQL("CREATE TABLE users ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "name TEXT NOT NULL,"
                + "role TEXT NOT NULL CHECK(role IN ('MANAGER','CASHIER')),"
                + "pin_hash TEXT NOT NULL,"
                + "pin_salt TEXT NOT NULL,"
                + "active INTEGER NOT NULL DEFAULT 1,"
                + "created_at INTEGER NOT NULL)");

        db.execSQL("CREATE TABLE shifts ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "uuid TEXT NOT NULL UNIQUE,"
                + "shift_no INTEGER NOT NULL UNIQUE,"
                + "user_id INTEGER NOT NULL REFERENCES users(id),"
                + "cashier_name TEXT NOT NULL,"
                + "opened_at INTEGER NOT NULL,"
                + "opening_cash INTEGER NOT NULL CHECK(opening_cash >= 0),"
                + "status TEXT NOT NULL CHECK(status IN ('OPEN','CLOSED')),"
                + "closed_at INTEGER NOT NULL DEFAULT 0,"
                + "closed_by TEXT,"
                + "counted_cash INTEGER NOT NULL DEFAULT 0,"
                + "expected_cash INTEGER NOT NULL DEFAULT 0,"
                + "difference INTEGER NOT NULL DEFAULT 0,"
                + "close_note TEXT,"
                + "summary_json TEXT)");
        // وردية مفتوحة واحدة فقط على الجهاز في أي وقت.
        db.execSQL("CREATE UNIQUE INDEX idx_one_open_shift ON shifts(status) WHERE status='OPEN'");

        db.execSQL("CREATE TABLE shift_reopens ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "shift_id INTEGER NOT NULL REFERENCES shifts(id),"
                + "reopened_at INTEGER NOT NULL,"
                + "user_name TEXT NOT NULL,"
                + "reason TEXT NOT NULL,"
                + "previous_summary_json TEXT)");

        db.execSQL("CREATE TABLE orders ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "uuid TEXT NOT NULL UNIQUE,"
                + "invoice_no TEXT NOT NULL UNIQUE,"
                + "shift_id INTEGER NOT NULL REFERENCES shifts(id),"
                + "user_id INTEGER NOT NULL REFERENCES users(id),"
                + "cashier_name TEXT NOT NULL,"
                + "order_type TEXT NOT NULL CHECK(order_type IN ('DINE_IN','TAKEAWAY')),"
                + "table_label TEXT,"
                + "status TEXT NOT NULL CHECK(status IN ('PAID','CANCELLED')),"
                + "subtotal INTEGER NOT NULL CHECK(subtotal >= 0),"
                + "discount_type TEXT NOT NULL DEFAULT 'NONE',"
                + "discount_value INTEGER NOT NULL DEFAULT 0,"
                + "discount_amount INTEGER NOT NULL DEFAULT 0 CHECK(discount_amount >= 0),"
                + "service_fee INTEGER NOT NULL DEFAULT 0 CHECK(service_fee >= 0),"
                + "total INTEGER NOT NULL CHECK(total >= 0),"
                + "created_at INTEGER NOT NULL,"
                + "print_count INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX idx_orders_shift ON orders(shift_id)");
        db.execSQL("CREATE INDEX idx_orders_created ON orders(created_at)");

        db.execSQL("CREATE TABLE order_items ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "order_id INTEGER NOT NULL REFERENCES orders(id),"
                + "product_id INTEGER NOT NULL,"
                + "size_id INTEGER NOT NULL DEFAULT 0,"
                + "category_id INTEGER NOT NULL,"
                + "category_name TEXT NOT NULL,"
                + "name TEXT NOT NULL,"
                + "unit_price INTEGER NOT NULL CHECK(unit_price >= 0),"
                + "qty INTEGER NOT NULL CHECK(qty > 0),"
                + "line_total INTEGER NOT NULL CHECK(line_total >= 0))");
        db.execSQL("CREATE INDEX idx_items_order ON order_items(order_id)");
        db.execSQL("CREATE INDEX idx_items_product ON order_items(product_id)");

        db.execSQL("CREATE TABLE payments ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "order_id INTEGER NOT NULL REFERENCES orders(id),"
                + "shift_id INTEGER NOT NULL REFERENCES shifts(id),"
                + "method TEXT NOT NULL CHECK(method IN ('CASH','CARD')),"
                + "amount INTEGER NOT NULL CHECK(amount > 0),"
                + "created_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX idx_payments_order ON payments(order_id)");

        db.execSQL("CREATE TABLE cancellations ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "order_id INTEGER NOT NULL UNIQUE REFERENCES orders(id),"
                + "shift_id INTEGER NOT NULL REFERENCES shifts(id),"
                + "reason TEXT NOT NULL,"
                + "user_name TEXT NOT NULL,"
                + "approved_by TEXT,"
                + "cancelled_at INTEGER NOT NULL)");

        db.execSQL("CREATE TABLE refunds ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "order_id INTEGER NOT NULL REFERENCES orders(id),"
                + "shift_id INTEGER NOT NULL REFERENCES shifts(id),"
                + "method TEXT NOT NULL CHECK(method IN ('CASH','CARD')),"
                + "amount INTEGER NOT NULL CHECK(amount > 0),"
                + "user_name TEXT NOT NULL,"
                + "created_at INTEGER NOT NULL)");

        db.execSQL("CREATE TABLE expenses ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "shift_id INTEGER NOT NULL REFERENCES shifts(id),"
                + "amount INTEGER NOT NULL CHECK(amount > 0),"
                + "category TEXT NOT NULL,"
                + "description TEXT,"
                + "user_name TEXT NOT NULL,"
                + "created_at INTEGER NOT NULL)");

        db.execSQL("CREATE TABLE cash_adjustments ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "shift_id INTEGER NOT NULL REFERENCES shifts(id),"
                + "direction TEXT NOT NULL CHECK(direction IN ('IN','OUT')),"
                + "amount INTEGER NOT NULL CHECK(amount > 0),"
                + "reason TEXT NOT NULL,"
                + "user_name TEXT NOT NULL,"
                + "created_at INTEGER NOT NULL)");

        db.execSQL("CREATE TABLE audit_log ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "at INTEGER NOT NULL,"
                + "user_name TEXT,"
                + "action TEXT NOT NULL,"
                + "details TEXT)");

        db.execSQL("INSERT INTO settings(key,value) VALUES('" + SIGNATURE_KEY + "','" + SIGNATURE + "')");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // كل ترقية تضيف ما ينقص خطوة بخطوة دون حذف بيانات.
        if (oldVersion < 2) {
            // الإصدار 2: خدمة الصالة لكل طلب.
            db.execSQL("ALTER TABLE orders ADD COLUMN service_fee INTEGER NOT NULL DEFAULT 0");
        }
    }
}
