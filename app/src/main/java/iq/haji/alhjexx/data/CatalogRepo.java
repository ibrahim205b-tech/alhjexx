package iq.haji.alhjexx.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import iq.haji.alhjexx.core.AppException;
import iq.haji.alhjexx.core.Money;
import iq.haji.alhjexx.model.Category;
import iq.haji.alhjexx.model.Product;
import iq.haji.alhjexx.model.ProductSize;

/**
 * إدارة الأقسام والأصناف والأحجام.
 * الحذف النهائي مسموح فقط لما لم يُستخدم في أي طلب؛ غير ذلك يُؤرشف ويُخفى.
 */
public final class CatalogRepo {

    private final Context ctx;

    public CatalogRepo(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    private SQLiteDatabase db() {
        return Db.get(ctx).getWritableDatabase();
    }

    private static String cleanName(String name, String what) {
        String n = name == null ? "" : name.trim();
        if (n.isEmpty()) {
            throw new AppException("أدخل " + what);
        }
        if (n.length() > 60) {
            throw new AppException(what + " طويل جداً (الحد 60 حرفاً)");
        }
        return n;
    }

    private static void checkPrice(long price) {
        if (price < 0 || price > Money.MAX) {
            throw new AppException("سعر غير صالح");
        }
    }

    // ---------------- الأقسام ----------------

    public List<Category> categories(boolean onlyActive) {
        List<Category> out = new ArrayList<>();
        String sql = "SELECT id,name,sort_order,active,archived FROM categories WHERE archived=0"
                + (onlyActive ? " AND active=1" : "") + " ORDER BY sort_order, id";
        try (Cursor c = db().rawQuery(sql, null)) {
            while (c.moveToNext()) {
                Category k = new Category();
                k.id = c.getLong(0);
                k.name = c.getString(1);
                k.sortOrder = c.getInt(2);
                k.active = c.getInt(3) == 1;
                k.archived = c.getInt(4) == 1;
                out.add(k);
            }
        }
        return out;
    }

    public Category category(long id) {
        for (Category c : categories(false)) {
            if (c.id == id) {
                return c;
            }
        }
        return null;
    }

    public long addCategory(String name, String by) {
        String n = cleanName(name, "اسم القسم");
        int next = 1;
        try (Cursor c = db().rawQuery("SELECT COALESCE(MAX(sort_order),0)+1 FROM categories", null)) {
            if (c.moveToFirst()) {
                next = c.getInt(0);
            }
        }
        ContentValues cv = new ContentValues();
        cv.put("name", n);
        cv.put("sort_order", next);
        cv.put("active", 1);
        cv.put("created_at", System.currentTimeMillis());
        long id = db().insert("categories", null, cv);
        if (id < 0) {
            throw new AppException("تعذر حفظ القسم");
        }
        Audit.log(db(), by, "إضافة قسم", n);
        return id;
    }

    public void renameCategory(long id, String name, String by) {
        String n = cleanName(name, "اسم القسم");
        ContentValues cv = new ContentValues();
        cv.put("name", n);
        db().update("categories", cv, "id=?", new String[]{String.valueOf(id)});
        Audit.log(db(), by, "تعديل اسم قسم", n);
    }

    public void setCategoryActive(long id, boolean active, String by) {
        ContentValues cv = new ContentValues();
        cv.put("active", active ? 1 : 0);
        db().update("categories", cv, "id=?", new String[]{String.valueOf(id)});
        Category c = category(id);
        Audit.log(db(), by, active ? "إظهار قسم" : "إخفاء قسم", c == null ? "#" + id : c.name);
    }

    /** يحرك القسم خطوة للأعلى (delta=-1) أو للأسفل (delta=+1). */
    public void moveCategory(long id, int delta) {
        List<Category> list = categories(false);
        int idx = -1;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id == id) {
                idx = i;
            }
        }
        int target = idx + delta;
        if (idx < 0 || target < 0 || target >= list.size()) {
            return;
        }
        Category a = list.remove(idx);
        list.add(target, a);
        SQLiteDatabase db = db();
        db.beginTransaction();
        try {
            for (int i = 0; i < list.size(); i++) {
                ContentValues cv = new ContentValues();
                cv.put("sort_order", i + 1);
                db.update("categories", cv, "id=?", new String[]{String.valueOf(list.get(i).id)});
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /** يحذف القسم إن كان فارغاً وغير مستخدم، وإلا يرفض. */
    public void deleteCategory(long id, String by) {
        try (Cursor c = db().rawQuery("SELECT COUNT(*) FROM products WHERE category_id=? AND archived=0",
                new String[]{String.valueOf(id)})) {
            if (c.moveToFirst() && c.getInt(0) > 0) {
                throw new AppException("لا يمكن حذف قسم يحتوي أصنافاً. انقل أصنافه أو أخفِ القسم.");
            }
        }
        boolean used;
        try (Cursor c = db().rawQuery("SELECT COUNT(*) FROM order_items WHERE category_id=?",
                new String[]{String.valueOf(id)})) {
            used = c.moveToFirst() && c.getInt(0) > 0;
        }
        try (Cursor c = db().rawQuery("SELECT COUNT(*) FROM products WHERE category_id=?",
                new String[]{String.valueOf(id)})) {
            used = used || (c.moveToFirst() && c.getInt(0) > 0);
        }
        Category k = category(id);
        if (used) {
            ContentValues cv = new ContentValues();
            cv.put("archived", 1);
            cv.put("active", 0);
            db().update("categories", cv, "id=?", new String[]{String.valueOf(id)});
            Audit.log(db(), by, "أرشفة قسم", k == null ? "#" + id : k.name);
        } else {
            db().delete("categories", "id=?", new String[]{String.valueOf(id)});
            Audit.log(db(), by, "حذف قسم", k == null ? "#" + id : k.name);
        }
    }

    // ---------------- الأصناف ----------------

    private Map<Long, List<ProductSize>> loadSizes() {
        Map<Long, List<ProductSize>> map = new HashMap<>();
        try (Cursor c = db().rawQuery(
                "SELECT id,product_id,name,price,sort_order,active FROM product_sizes ORDER BY sort_order, id",
                null)) {
            while (c.moveToNext()) {
                ProductSize s = new ProductSize();
                s.id = c.getLong(0);
                s.productId = c.getLong(1);
                s.name = c.getString(2);
                s.price = c.getLong(3);
                s.sortOrder = c.getInt(4);
                s.active = c.getInt(5) == 1;
                List<ProductSize> l = map.get(s.productId);
                if (l == null) {
                    l = new ArrayList<>();
                    map.put(s.productId, l);
                }
                l.add(s);
            }
        }
        return map;
    }

    /**
     * @param categoryId 0 = كل الأقسام
     * @param onlyActive true لشاشة الكاشير: الصنف وقسمه مفعّلان
     */
    public List<Product> products(long categoryId, boolean onlyActive) {
        Map<Long, List<ProductSize>> sizes = loadSizes();
        List<Product> out = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT p.id,p.category_id,p.name,p.price,p.active,p.archived FROM products p "
                        + "JOIN categories c ON c.id=p.category_id WHERE p.archived=0 AND c.archived=0");
        List<String> args = new ArrayList<>();
        if (onlyActive) {
            sql.append(" AND p.active=1 AND c.active=1");
        }
        if (categoryId > 0) {
            sql.append(" AND p.category_id=?");
            args.add(String.valueOf(categoryId));
        }
        sql.append(" ORDER BY c.sort_order, p.sort_order, p.id");
        try (Cursor c = db().rawQuery(sql.toString(), args.toArray(new String[0]))) {
            while (c.moveToNext()) {
                Product p = new Product();
                p.id = c.getLong(0);
                p.categoryId = c.getLong(1);
                p.name = c.getString(2);
                p.price = c.getLong(3);
                p.active = c.getInt(4) == 1;
                p.archived = c.getInt(5) == 1;
                List<ProductSize> l = sizes.get(p.id);
                if (l != null) {
                    p.sizes.addAll(l);
                }
                out.add(p);
            }
        }
        return out;
    }

    public Product product(long id) {
        Map<Long, List<ProductSize>> sizes = loadSizes();
        try (Cursor c = db().rawQuery(
                "SELECT id,category_id,name,price,active,archived FROM products WHERE id=?",
                new String[]{String.valueOf(id)})) {
            if (!c.moveToFirst()) {
                return null;
            }
            Product p = new Product();
            p.id = c.getLong(0);
            p.categoryId = c.getLong(1);
            p.name = c.getString(2);
            p.price = c.getLong(3);
            p.active = c.getInt(4) == 1;
            p.archived = c.getInt(5) == 1;
            List<ProductSize> l = sizes.get(p.id);
            if (l != null) {
                p.sizes.addAll(l);
            }
            return p;
        }
    }

    /**
     * يحفظ الصنف وأحجامه. id=0 للإضافة.
     * تعديل السعر لا يؤثر على الطلبات السابقة لأن أسطر الطلب تحفظ نسخة من الاسم والسعر.
     */
    public long saveProduct(Product p, String by) {
        String name = cleanName(p.name, "اسم الصنف");
        if (category(p.categoryId) == null) {
            throw new AppException("اختر قسماً صحيحاً");
        }
        List<ProductSize> sizes = new ArrayList<>();
        for (ProductSize s : p.sizes) {
            if (s.name != null && !s.name.trim().isEmpty()) {
                s.name = cleanName(s.name, "اسم الحجم");
                checkPrice(s.price);
                if (s.price <= 0) {
                    throw new AppException("أدخل سعراً صحيحاً للحجم «" + s.name + "»");
                }
                sizes.add(s);
            }
        }
        if (sizes.isEmpty() && p.price <= 0) {
            throw new AppException("أدخل سعراً صحيحاً أكبر من صفر");
        }
        checkPrice(p.price);

        SQLiteDatabase db = db();
        Product old = p.id == 0 ? null : product(p.id);
        db.beginTransaction();
        try {
            ContentValues cv = new ContentValues();
            cv.put("category_id", p.categoryId);
            cv.put("name", name);
            cv.put("price", sizes.isEmpty() ? p.price : sizes.get(0).price);
            cv.put("active", p.active ? 1 : 0);
            long id = p.id;
            if (id == 0) {
                cv.put("created_at", System.currentTimeMillis());
                cv.put("sort_order", 0);
                id = db.insert("products", null, cv);
                if (id < 0) {
                    throw new AppException("تعذر حفظ الصنف");
                }
            } else {
                db.update("products", cv, "id=?", new String[]{String.valueOf(id)});
            }

            // الأحجام: تحديث الموجود، إضافة الجديد، وتعطيل المحذوف (لا يُحذف نهائياً للحفاظ على المراجع).
            List<Long> keep = new ArrayList<>();
            for (int i = 0; i < sizes.size(); i++) {
                ProductSize s = sizes.get(i);
                ContentValues sv = new ContentValues();
                sv.put("product_id", id);
                sv.put("name", s.name);
                sv.put("price", s.price);
                sv.put("sort_order", i);
                sv.put("active", 1);
                if (s.id > 0) {
                    db.update("product_sizes", sv, "id=? AND product_id=?",
                            new String[]{String.valueOf(s.id), String.valueOf(id)});
                    keep.add(s.id);
                } else {
                    keep.add(db.insert("product_sizes", null, sv));
                }
            }
            StringBuilder where = new StringBuilder("product_id=?");
            List<String> args = new ArrayList<>();
            args.add(String.valueOf(id));
            for (Long k : keep) {
                where.append(" AND id<>?");
                args.add(String.valueOf(k));
            }
            ContentValues off = new ContentValues();
            off.put("active", 0);
            db.update("product_sizes", off, where.toString(), args.toArray(new String[0]));

            if (old == null) {
                Audit.log(db, by, "إضافة صنف", name + " = " + Money.number(sizes.isEmpty() ? p.price : sizes.get(0).price));
            } else {
                StringBuilder d = new StringBuilder(name);
                if (old.price != p.price && sizes.isEmpty()) {
                    d.append(" | السعر: ").append(Money.number(old.price)).append(" ← ").append(Money.number(p.price));
                }
                if (old.categoryId != p.categoryId) {
                    d.append(" | نقل إلى قسم آخر");
                }
                Audit.log(db, by, "تعديل صنف", d.toString());
            }
            db.setTransactionSuccessful();
            return id;
        } finally {
            db.endTransaction();
        }
    }

    public void setProductActive(long id, boolean active, String by) {
        ContentValues cv = new ContentValues();
        cv.put("active", active ? 1 : 0);
        db().update("products", cv, "id=?", new String[]{String.valueOf(id)});
        Product p = product(id);
        Audit.log(db(), by, active ? "تفعيل صنف" : "تعطيل صنف", p == null ? "#" + id : p.name);
    }

    public boolean isProductUsed(long id) {
        try (Cursor c = db().rawQuery("SELECT COUNT(*) FROM order_items WHERE product_id=?",
                new String[]{String.valueOf(id)})) {
            return c.moveToFirst() && c.getInt(0) > 0;
        }
    }

    /** الصنف المرتبط بطلبات سابقة يُؤرشف فقط، وغير المستخدم يُحذف مع أحجامه. */
    public void deleteProduct(long id, String by) {
        Product p = product(id);
        SQLiteDatabase db = db();
        if (isProductUsed(id)) {
            ContentValues cv = new ContentValues();
            cv.put("archived", 1);
            cv.put("active", 0);
            db.update("products", cv, "id=?", new String[]{String.valueOf(id)});
            Audit.log(db, by, "أرشفة صنف", p == null ? "#" + id : p.name);
            return;
        }
        db.beginTransaction();
        try {
            db.delete("product_sizes", "product_id=?", new String[]{String.valueOf(id)});
            db.delete("products", "id=?", new String[]{String.valueOf(id)});
            Audit.log(db, by, "حذف صنف", p == null ? "#" + id : p.name);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public int productCount() {
        try (Cursor c = db().rawQuery("SELECT COUNT(*) FROM products WHERE archived=0", null)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }
}
