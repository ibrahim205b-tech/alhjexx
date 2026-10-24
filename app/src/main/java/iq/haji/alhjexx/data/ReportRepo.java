package iq.haji.alhjexx.data;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import iq.haji.alhjexx.model.Order;
import iq.haji.alhjexx.model.OrderStatus;

/**
 * محرك التقارير. كل الأرقام تُحسب من قاعدة البيانات مباشرة.
 *
 * التعريفات المحاسبية الموحّدة:
 * <ul>
 *   <li>إجمالي المبيعات = مجموع أسطر الطلبات غير الملغاة قبل الخصم.</li>
 *   <li>الخصومات = مجموع خصومات الطلبات غير الملغاة.</li>
 *   <li>صافي المبيعات = إجمالي المبيعات − الخصومات = مجموع إجماليات الطلبات غير الملغاة.</li>
 *   <li>الملغاة تُعرض منفصلة بقيمتها الأصلية ولا تدخل في المبيعات.</li>
 *   <li>الاسترجاعات تُعرض منفصلة وتؤثر في الصندوق فقط إذا كانت نقدية.</li>
 *   <li>المصروفات تُعرض منفصلة ولا تُخصم من المبيعات.</li>
 * </ul>
 */
public final class ReportRepo {

    /** نطاق التقرير: وردية محددة، أو فترة زمنية [from, to). */
    public static final class Filter {
        public long shiftId;
        public long from;
        public long to;

        public static Filter shift(long shiftId) {
            Filter f = new Filter();
            f.shiftId = shiftId;
            return f;
        }

        public static Filter range(long from, long to) {
            Filter f = new Filter();
            f.from = from;
            f.to = to;
            return f;
        }

        /** شرط SQL على جدول له عمود shift_id وعمود زمني. */
        String where(String alias, String timeCol, List<String> args) {
            String p = alias == null ? "" : alias + ".";
            if (shiftId > 0) {
                args.add(String.valueOf(shiftId));
                return p + "shift_id=?";
            }
            args.add(String.valueOf(from));
            args.add(String.valueOf(to));
            return p + timeCol + ">=? AND " + p + timeCol + "<?";
        }
    }

    public static final class ItemSale {
        public long productId;
        public String name;
        public long unitPrice;
        public long qty;
        public long total;
    }

    public static final class CategorySale {
        public long categoryId;
        public String name;
        public long qty;
        public long total;
        public final List<ItemSale> items = new ArrayList<>();
    }

    public static final class Summary {
        public int orderCount;
        public int dineInCount;
        public long dineInTotal;
        public int takeawayCount;
        public long takeawayTotal;
        public long gross;
        public long discounts;
        public int discountedOrders;
        public long net;
        public int cancelledCount;
        public long cancelledTotal;
        /** مدفوعات الطلبات غير الملغاة حسب الوسيلة. */
        public long salesCash;
        public long salesCard;
        /** كل النقد المقبوض في النطاق (يشمل طلبات أُلغيت لاحقاً) — يدخل في معادلة الصندوق. */
        public long cashReceived;
        public long cardReceived;
        public long refundsCash;
        public long refundsCard;
        public long expensesCash;
        public int expenseCount;
        public long payIns;
        public long payOuts;
        public final List<CategorySale> categories = new ArrayList<>();

        public String toJson() {
            try {
                JSONObject j = new JSONObject();
                j.put("orders", orderCount);
                j.put("dine_in_count", dineInCount);
                j.put("dine_in_total", dineInTotal);
                j.put("takeaway_count", takeawayCount);
                j.put("takeaway_total", takeawayTotal);
                j.put("gross", gross);
                j.put("discounts", discounts);
                j.put("net", net);
                j.put("cancelled_count", cancelledCount);
                j.put("cancelled_total", cancelledTotal);
                j.put("sales_cash", salesCash);
                j.put("sales_card", salesCard);
                j.put("cash_received", cashReceived);
                j.put("refunds_cash", refundsCash);
                j.put("refunds_card", refundsCard);
                j.put("expenses_cash", expensesCash);
                j.put("pay_ins", payIns);
                j.put("pay_outs", payOuts);
                return j.toString();
            } catch (JSONException e) {
                return "{}";
            }
        }
    }

    private final Context ctx;

    public ReportRepo(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    private SQLiteDatabase db() {
        return Db.get(ctx).getReadableDatabase();
    }

    private long scalar(String sql, List<String> args) {
        try (Cursor c = db().rawQuery(sql, args.toArray(new String[0]))) {
            return c.moveToFirst() ? c.getLong(0) : 0;
        }
    }

    public Summary summary(Filter f) {
        Summary s = new Summary();
        SQLiteDatabase db = db();

        List<String> a = new ArrayList<>();
        String w = f.where("o", "created_at", a);
        try (Cursor c = db.rawQuery("SELECT o.order_type, o.status, COUNT(*), COALESCE(SUM(o.subtotal),0), "
                + "COALESCE(SUM(o.discount_amount),0), COALESCE(SUM(o.total),0), "
                + "SUM(CASE WHEN o.discount_amount>0 THEN 1 ELSE 0 END) "
                + "FROM orders o WHERE " + w + " GROUP BY o.order_type, o.status", a.toArray(new String[0]))) {
            while (c.moveToNext()) {
                String type = c.getString(0);
                String status = c.getString(1);
                int count = c.getInt(2);
                long total = c.getLong(5);
                if (OrderStatus.CANCELLED.equals(status)) {
                    s.cancelledCount += count;
                    s.cancelledTotal += total;
                    continue;
                }
                s.orderCount += count;
                s.gross += c.getLong(3);
                s.discounts += c.getLong(4);
                s.net += total;
                s.discountedOrders += c.getInt(6);
                if ("DINE_IN".equals(type)) {
                    s.dineInCount += count;
                    s.dineInTotal += total;
                } else {
                    s.takeawayCount += count;
                    s.takeawayTotal += total;
                }
            }
        }

        a = new ArrayList<>();
        w = f.where("p", "created_at", a);
        try (Cursor c = db.rawQuery("SELECT p.method, o.status, COALESCE(SUM(p.amount),0) FROM payments p "
                + "JOIN orders o ON o.id=p.order_id WHERE " + w + " GROUP BY p.method, o.status",
                a.toArray(new String[0]))) {
            while (c.moveToNext()) {
                boolean cash = "CASH".equals(c.getString(0));
                boolean cancelled = OrderStatus.CANCELLED.equals(c.getString(1));
                long amount = c.getLong(2);
                if (cash) {
                    s.cashReceived += amount;
                    if (!cancelled) {
                        s.salesCash += amount;
                    }
                } else {
                    s.cardReceived += amount;
                    if (!cancelled) {
                        s.salesCard += amount;
                    }
                }
            }
        }

        a = new ArrayList<>();
        w = f.where(null, "created_at", a);
        try (Cursor c = db.rawQuery("SELECT method, COALESCE(SUM(amount),0) FROM refunds WHERE " + w
                + " GROUP BY method", a.toArray(new String[0]))) {
            while (c.moveToNext()) {
                if ("CASH".equals(c.getString(0))) {
                    s.refundsCash += c.getLong(1);
                } else {
                    s.refundsCard += c.getLong(1);
                }
            }
        }

        a = new ArrayList<>();
        w = f.where(null, "created_at", a);
        try (Cursor c = db.rawQuery("SELECT COUNT(*), COALESCE(SUM(amount),0) FROM expenses WHERE " + w,
                a.toArray(new String[0]))) {
            if (c.moveToFirst()) {
                s.expenseCount = c.getInt(0);
                s.expensesCash = c.getLong(1);
            }
        }

        a = new ArrayList<>();
        w = f.where(null, "created_at", a);
        try (Cursor c = db.rawQuery("SELECT direction, COALESCE(SUM(amount),0) FROM cash_adjustments WHERE " + w
                + " GROUP BY direction", a.toArray(new String[0]))) {
            while (c.moveToNext()) {
                if ("IN".equals(c.getString(0))) {
                    s.payIns += c.getLong(1);
                } else {
                    s.payOuts += c.getLong(1);
                }
            }
        }

        s.categories.addAll(categorySales(f, 0));
        return s;
    }

    /**
     * المبيعات حسب القسم ثم الصنف، بالسعر المعتمد في الطلب نفسه (وليس السعر الحالي في المنيو).
     * @param categoryId 0 = كل الأقسام
     */
    public List<CategorySale> categorySales(Filter f, long categoryId) {
        List<String> a = new ArrayList<>();
        String w = f.where("o", "created_at", a);
        StringBuilder sql = new StringBuilder("SELECT i.category_id, i.category_name, i.product_id, i.name, i.unit_price, "
                + "SUM(i.qty), SUM(i.line_total) FROM order_items i JOIN orders o ON o.id=i.order_id "
                + "WHERE o.status='PAID' AND " + w);
        if (categoryId > 0) {
            sql.append(" AND i.category_id=?");
            a.add(String.valueOf(categoryId));
        }
        sql.append(" GROUP BY i.category_id, i.category_name, i.product_id, i.name, i.unit_price "
                + "ORDER BY i.category_name, SUM(i.line_total) DESC");
        Map<String, CategorySale> map = new LinkedHashMap<>();
        try (Cursor c = db().rawQuery(sql.toString(), a.toArray(new String[0]))) {
            while (c.moveToNext()) {
                String key = c.getLong(0) + "|" + c.getString(1);
                CategorySale cs = map.get(key);
                if (cs == null) {
                    cs = new CategorySale();
                    cs.categoryId = c.getLong(0);
                    cs.name = c.getString(1);
                    map.put(key, cs);
                }
                ItemSale it = new ItemSale();
                it.productId = c.getLong(2);
                it.name = c.getString(3);
                it.unitPrice = c.getLong(4);
                it.qty = c.getLong(5);
                it.total = c.getLong(6);
                cs.items.add(it);
                cs.qty += it.qty;
                cs.total += it.total;
            }
        }
        return new ArrayList<>(map.values());
    }

    /** أداء صنف محدد خلال فترة: يُجمع حسب الاسم والسعر المعتمد (يشمل أحجامه). */
    public List<ItemSale> productSales(long productId, long from, long to) {
        List<ItemSale> out = new ArrayList<>();
        try (Cursor c = db().rawQuery("SELECT i.name, i.unit_price, SUM(i.qty), SUM(i.line_total) "
                        + "FROM order_items i JOIN orders o ON o.id=i.order_id "
                        + "WHERE o.status='PAID' AND i.product_id=? AND o.created_at>=? AND o.created_at<? "
                        + "GROUP BY i.name, i.unit_price ORDER BY i.name, i.unit_price",
                new String[]{String.valueOf(productId), String.valueOf(from), String.valueOf(to)})) {
            while (c.moveToNext()) {
                ItemSale it = new ItemSale();
                it.productId = productId;
                it.name = c.getString(0);
                it.unitPrice = c.getLong(1);
                it.qty = c.getLong(2);
                it.total = c.getLong(3);
                out.add(it);
            }
        }
        return out;
    }

    /** الطلبات الملغاة في الفترة (حسب وقت الإلغاء) مع كامل تفاصيلها. */
    public List<Order> cancelledOrders(long from, long to) {
        List<Long> ids = new ArrayList<>();
        try (Cursor c = db().rawQuery("SELECT order_id FROM cancellations WHERE cancelled_at>=? AND cancelled_at<? "
                + "ORDER BY cancelled_at", new String[]{String.valueOf(from), String.valueOf(to)})) {
            while (c.moveToNext()) {
                ids.add(c.getLong(0));
            }
        }
        OrderRepo orders = new OrderRepo(ctx);
        List<Order> out = new ArrayList<>();
        for (Long id : ids) {
            Order o = orders.get(id);
            if (o != null) {
                out.add(o);
            }
        }
        return out;
    }

    public List<Order> cancelledOrdersOfShift(long shiftId) {
        List<Long> ids = new ArrayList<>();
        try (Cursor c = db().rawQuery("SELECT o.id FROM orders o WHERE o.shift_id=? AND o.status='CANCELLED' ORDER BY o.id",
                new String[]{String.valueOf(shiftId)})) {
            while (c.moveToNext()) {
                ids.add(c.getLong(0));
            }
        }
        OrderRepo orders = new OrderRepo(ctx);
        List<Order> out = new ArrayList<>();
        for (Long id : ids) {
            Order o = orders.get(id);
            if (o != null) {
                out.add(o);
            }
        }
        return out;
    }
}
