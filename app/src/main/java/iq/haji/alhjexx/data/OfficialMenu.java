package iq.haji.alhjexx.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/**
 * القائمة الرسمية لمطعم الحجي (16 قسماً). تُستورد من شاشة المنيو بإجراء إداري.
 * أصناف البيتزا متعددة الأحجام تُحفظ صنفاً واحداً بأحجامه وأسعاره، ويُختار الحجم عند الطلب.
 * الاستيراد لا يكرر صنفاً موجوداً ولا يحذف شيئاً؛ في وضع الاستبدال تُخفى الأصناف والأقسام الحالية فقط.
 */
public final class OfficialMenu {

    private OfficialMenu() {
    }

    public static final class Result {
        public int categories;
        public int added;
        public int alreadyPresent;
    }

    private static final class Size {
        final String name;
        final long price;

        Size(String name, long price) {
            this.name = name;
            this.price = price;
        }
    }

    private static final class Item {
        final String name;
        final long price;
        final Size[] sizes;

        Item(String name, long price, Size[] sizes) {
            this.name = name;
            this.price = price;
            this.sizes = sizes;
        }
    }

    private static final class Section {
        final String name;
        final Item[] items;

        Section(String name, Item... items) {
            this.name = name;
            this.items = items;
        }
    }

    private static Item i(String name, long price) {
        return new Item(name, price, new Size[0]);
    }

    private static Item sized(String name, Size... sizes) {
        return new Item(name, sizes[0].price, sizes);
    }

    private static Size sz(String name, long price) {
        return new Size(name, price);
    }

    private static final Section[] MENU = {
            new Section("الكنتاكي",
                    i("كنتاكي 3 قطع", 5000),
                    i("كنتاكي 5 قطع", 8000),
                    i("كنتاكي 10 قطع عائلي", 16000),
                    i("ستربس 10 قطع عائلي", 15000),
                    i("ستربس 7 قطع", 10000),
                    i("ستربس 4 قطع", 6000)),

            new Section("الدجاج",
                    i("دجاجة ذبح عراقي", 14000),
                    i("نص دجاجة ذبح عراقي", 8000),
                    i("دجاجة مشوية", 12000),
                    i("نص دجاجة مشوية", 7000),
                    i("ربع دجاجة ذبح عراقي", 5000),
                    i("ربع دجاجة عالتمَن", 4000)),

            new Section("البركر",
                    i("بركَر لحم كلاسك", 1000),
                    i("بركَر لحم بالجبن", 2000),
                    i("بركَر دجاج بالجبن", 2000),
                    i("بركَر لحم دبل", 3000)),

            new Section("الفلافل",
                    i("لفة فلافل عادي", 500),
                    i("لفة فلافل مشكل", 750),
                    i("صاج فلافل", 1000),
                    i("صينية فلافل كبيرة", 5000),
                    i("صينية فلافل وسط", 3000),
                    i("لفة قوزي دجاج", 1000),
                    i("لفة جلفراي", 1000),
                    i("لفة مغربية", 1000),
                    i("لفة كبدة دجاج", 1000)),

            new Section("الريزو",
                    i("ريزو أعشاب", 5000),
                    i("ريزو بالجبن", 5000),
                    i("ريزو مدخن", 5000),
                    i("ريزو هبلينو", 5000),
                    i("ريزو كلاسك", 5000),
                    i("ريزو شاورما دجاج", 5000),
                    i("ريزو شاورما لحم", 5000),
                    i("ريزو سبايسي", 5000),
                    i("ريزو", 3000)),

            new Section("المقبلات",
                    i("ماعون ثنائي", 1000),
                    i("خماسي", 2000),
                    i("صينية مشكل", 5000)),

            new Section("المشروبات",
                    i("بيبسي", 500),
                    i("سفن", 500),
                    i("ميريندا", 500),
                    i("لبن", 500),
                    i("بطل ببسي وسط", 750)),

            new Section("السندويشات",
                    i("سندويش شاورما لحم", 2000),
                    i("سندويش دجاج", 1500),
                    i("سندويش زنكر", 3000)),

            new Section("اللغَم",
                    i("لغَم لحم", 5000),
                    i("لغَم دجاج", 5000),
                    i("زنكر", 3000)),

            new Section("الشرقي",
                    i("منسف دجاج + لحم", 50000),
                    i("منسف سمك + دجاج", 40000),
                    i("منسف سمك + لحم", 60000),
                    i("منسف دجاج", 25000),
                    i("صينية تمن ومرق كبيرة", 10000),
                    i("صينية تمن ومرق وسط", 7000),
                    i("صينية تمن ومرق صغيرة", 5000),
                    i("نفر تمن ومرق", 3000)),

            new Section("الفنكر",
                    i("فنكر عادي", 1000),
                    i("فنكر بالجبن", 1500),
                    i("فنكر عائلي", 3000)),

            new Section("البيتزا",
                    sized("بيتزا لحم", sz("صغير", 5000), sz("وسط", 7000), sz("كبير", 10000)),
                    sized("بيتزا دجاج", sz("صغير", 5000), sz("وسط", 7000), sz("كبير", 10000)),
                    sized("بيتزا الفصول الأربعة", sz("صغير", 5000), sz("وسط", 7000), sz("كبير", 10000)),
                    sized("بيتزا مارجريتا", sz("صغير", 4000), sz("وسط", 6000), sz("كبير", 8000)),
                    sized("بيتزا ببروني", sz("صغير", 5000), sz("وسط", 7000), sz("كبير", 10000)),
                    i("بيتزا لحم", 3000),
                    i("بيتزا دجاج", 3000)),

            new Section("المشويات (وزن)",
                    i("كيلو كباب لحم غنم (20 شيش) سفري", 20000),
                    i("نص كيلو كباب لحم غنم (10 شيش) سفري", 10000),
                    i("كيلو كباب دجاج (20 شيش) سفري", 16000),
                    i("نص كيلو كباب دجاج (10 شيش) سفري", 8000)),

            new Section("المشويات",
                    i("نفر كباب لحم غنم", 12000),
                    i("نصف نفر كباب لحم غنم", 6000),
                    i("نفر تكه لحم غنم", 14000),
                    i("نصف نفر تكه لحم غنم", 7000),
                    i("نفر كباب دجاج", 10000),
                    i("نصف نفر كباب دجاج", 5000),
                    i("نفر تكه دجاج متبله", 10000),
                    i("نصف نفر تكه دجاج متبله", 5000),
                    i("نفر معلاك غنم", 14000),
                    i("نصف نفر معلاك غنم", 7000),
                    i("نفر قلب غنم", 12000),
                    i("نصف نفر قلب غنم", 6000),
                    i("نفر جلاوي غنم", 12000),
                    i("نصف نفر جلاوي غنم", 6000),
                    i("نفر مسحب دجاج ناشف (نصف دجاجة)", 8000),
                    i("نفر مسحب دجاج عالتمَن (نصف دجاجة)", 10000),
                    i("دجاجة عالصجمع صينيه عالتمَن", 15000),
                    i("نصف دجاجة عالصجمع صينيه عالتمَن", 8000),
                    i("دجاجة عالصجمع ناشف صينيه بدون تمن", 13000),
                    i("نصف دجاجة عالصجمع ناشف صينيه بدون تمن", 7000)),

            new Section("المشويات (لفات)",
                    i("لفه كباب لحم غنم", 1500),
                    i("لفه كباب دجاج", 1500),
                    i("لفه تكه دجاج", 1500),
                    i("لفه تكه لحم", 2500),
                    i("لفه معلاك", 2500),
                    i("لفه قلب", 2500),
                    i("لفه جلاوي", 2500),
                    i("شيش ليه", 2500)),

            new Section("الشاورما",
                    i("صاج شاورما لحم", 2500),
                    i("صاج شاورما دجاج", 2000),
                    i("صاج ايطالي لحم", 5000),
                    i("صاج ايطالي دجاج", 5000))
    };

    public static int sectionCount() {
        return MENU.length;
    }

    /** عدد الأصناف (كل صنف بأحجامه يُعد صنفاً واحداً). */
    public static int itemCount() {
        int n = 0;
        for (Section s : MENU) {
            n += s.items.length;
        }
        return n;
    }

    public static Result importMenu(Context ctx, boolean replace, String byUser) {
        SQLiteDatabase db = Db.get(ctx).getWritableDatabase();
        Result r = new Result();
        long now = System.currentTimeMillis();
        db.beginTransaction();
        try {
            if (replace) {
                ContentValues hide = new ContentValues();
                hide.put("active", 0);
                db.update("products", hide, null, null);
                db.update("categories", hide, null, null);
            }
            for (int si = 0; si < MENU.length; si++) {
                Section sec = MENU[si];
                long catId = category(db, sec.name, si + 1, now);
                r.categories++;
                for (int ii = 0; ii < sec.items.length; ii++) {
                    Item it = sec.items[ii];
                    long existing = findProduct(db, catId, it);
                    if (existing > 0) {
                        ContentValues on = new ContentValues();
                        on.put("active", 1);
                        on.put("sort_order", ii);
                        db.update("products", on, "id=?", new String[]{String.valueOf(existing)});
                        r.alreadyPresent++;
                        continue;
                    }
                    ContentValues p = new ContentValues();
                    p.put("category_id", catId);
                    p.put("name", it.name);
                    p.put("price", it.price);
                    p.put("active", 1);
                    p.put("archived", 0);
                    p.put("sort_order", ii);
                    p.put("created_at", now);
                    long pid = db.insertOrThrow("products", null, p);
                    for (int k = 0; k < it.sizes.length; k++) {
                        ContentValues s = new ContentValues();
                        s.put("product_id", pid);
                        s.put("name", it.sizes[k].name);
                        s.put("price", it.sizes[k].price);
                        s.put("sort_order", k);
                        s.put("active", 1);
                        db.insertOrThrow("product_sizes", null, s);
                    }
                    r.added++;
                }
            }
            Audit.log(db, byUser, "استيراد القائمة الرسمية",
                    (replace ? "استبدال" : "إضافة") + ": أُضيف " + r.added + "، موجود " + r.alreadyPresent);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return r;
    }

    private static long category(SQLiteDatabase db, String name, int order, long now) {
        long id = 0;
        try (Cursor c = db.rawQuery("SELECT id FROM categories WHERE name=? AND archived=0 ORDER BY id LIMIT 1",
                new String[]{name})) {
            if (c.moveToFirst()) {
                id = c.getLong(0);
            }
        }
        ContentValues cv = new ContentValues();
        cv.put("sort_order", order);
        cv.put("active", 1);
        if (id > 0) {
            db.update("categories", cv, "id=?", new String[]{String.valueOf(id)});
            return id;
        }
        cv.put("name", name);
        cv.put("archived", 0);
        cv.put("created_at", now);
        return db.insertOrThrow("categories", null, cv);
    }

    /** صنف مطابق: نفس القسم والاسم، وبالأحجام نفسها أو بالسعر نفسه إن لم تكن له أحجام. */
    private static long findProduct(SQLiteDatabase db, long catId, Item it) {
        try (Cursor c = db.rawQuery("SELECT p.id, p.price, (SELECT COUNT(*) FROM product_sizes s "
                        + "WHERE s.product_id=p.id AND s.active=1) FROM products p "
                        + "WHERE p.category_id=? AND p.name=? AND p.archived=0 ORDER BY p.id",
                new String[]{String.valueOf(catId), it.name})) {
            while (c.moveToNext()) {
                int sizes = c.getInt(2);
                if (it.sizes.length > 0 && sizes > 0) {
                    return c.getLong(0);
                }
                if (it.sizes.length == 0 && sizes == 0 && c.getLong(1) == it.price) {
                    return c.getLong(0);
                }
            }
        }
        return 0;
    }
}
