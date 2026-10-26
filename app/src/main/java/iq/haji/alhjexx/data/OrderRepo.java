package iq.haji.alhjexx.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

import iq.haji.alhjexx.core.AppException;
import iq.haji.alhjexx.core.Dates;
import iq.haji.alhjexx.core.Money;
import iq.haji.alhjexx.core.OrderMath;
import iq.haji.alhjexx.model.Cart;
import iq.haji.alhjexx.model.CartLine;
import iq.haji.alhjexx.model.Order;
import iq.haji.alhjexx.model.OrderItem;
import iq.haji.alhjexx.model.OrderStatus;
import iq.haji.alhjexx.model.OrderType;
import iq.haji.alhjexx.model.PayMethod;
import iq.haji.alhjexx.model.Payment;
import iq.haji.alhjexx.model.Shift;
import iq.haji.alhjexx.model.User;

/**
 * اعتماد الطلبات وإلغاؤها. كل عملية كتابة تتم داخل معاملة واحدة:
 * إما أن يُحفظ الطلب بكامل أسطره ومدفوعاته، أو لا يُحفظ شيء.
 */
public final class OrderRepo {

    private static final String COLS = "o.id,o.uuid,o.invoice_no,o.shift_id,s.shift_no,o.user_id,o.cashier_name,"
            + "o.order_type,o.table_label,o.status,o.subtotal,o.discount_type,o.discount_value,"
            + "o.discount_amount,o.total,o.created_at,o.print_count,o.service_fee";

    private final Context ctx;

    public OrderRepo(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    private SQLiteDatabase db() {
        return Db.get(ctx).getWritableDatabase();
    }

    /** يتحقق من الطلب الجاري دون حفظه. */
    public static void validate(Cart cart) {
        if (cart.lines.isEmpty()) {
            throw new AppException("الطلب فارغ. أضف صنفاً واحداً على الأقل.");
        }
        if (!OrderType.isValid(cart.type)) {
            throw new AppException("اختر نوع الطلب");
        }
        for (CartLine l : cart.lines) {
            if (l.qty <= 0 || l.qty > OrderMath.MAX_QTY) {
                throw new AppException("كمية غير صحيحة للصنف: " + l.name);
            }
            if (l.unitPrice < 0) {
                throw new AppException("سعر غير صحيح للصنف: " + l.name);
            }
        }
        try {
            cart.total();
        } catch (IllegalArgumentException e) {
            throw new AppException(e.getMessage());
        }
    }

    /** رقم فاتورة فريد بصيغة yyMMdd-NNNN، يُحسب داخل معاملة الاعتماد. */
    private static String nextInvoiceNo(SQLiteDatabase db, long now) {
        String prefix = Dates.invoicePrefix(now);
        long seq = 1;
        try (Cursor c = db.rawQuery(
                "SELECT COALESCE(MAX(CAST(substr(invoice_no,8) AS INTEGER)),0)+1 FROM orders WHERE invoice_no LIKE ?",
                new String[]{prefix + "-%"})) {
            if (c.moveToFirst()) {
                seq = c.getLong(0);
            }
        }
        return prefix + "-" + String.format(java.util.Locale.US, "%04d", seq);
    }

    /**
     * يعتمد الطلب ويسجل مدفوعاته.
     * إذا وُجد طلب بنفس clientUuid (ضغطة مكررة) يُعاد رقمه دون إنشاء طلب جديد.
     *
     * @return معرّف الطلب المحفوظ
     */
    public long confirm(Cart cart, long cashPaid, long cardPaid, User user) {
        if (user == null) {
            throw new AppException("سجّل الدخول أولاً");
        }
        validate(cart);
        long subtotal = cart.subtotal();
        long service = OrderType.DINE_IN.equals(cart.type) ? Math.max(0, cart.serviceFee) : 0;
        cart.serviceFee = service;
        long discount;
        long total;
        try {
            discount = cart.discountAmount();
            total = cart.total();
            OrderMath.validatePayments(total, cashPaid, cardPaid);
        } catch (IllegalArgumentException e) {
            throw new AppException(e.getMessage());
        }

        SQLiteDatabase db = db();
        db.beginTransaction();
        try {
            try (Cursor c = db.rawQuery("SELECT id FROM orders WHERE uuid=?", new String[]{cart.clientUuid})) {
                if (c.moveToFirst()) {
                    long existing = c.getLong(0);
                    db.setTransactionSuccessful();
                    return existing;
                }
            }
            Shift shift = new ShiftRepo(ctx).openShift();
            if (shift == null) {
                throw new AppException("لا توجد وردية مفتوحة. افتح وردية قبل اعتماد الطلبات.");
            }
            long now = System.currentTimeMillis();
            ContentValues o = new ContentValues();
            o.put("uuid", cart.clientUuid);
            o.put("invoice_no", nextInvoiceNo(db, now));
            o.put("shift_id", shift.id);
            o.put("user_id", user.id);
            o.put("cashier_name", user.name);
            o.put("order_type", cart.type);
            o.put("table_label", OrderType.DINE_IN.equals(cart.type) && cart.tableLabel != null
                    ? cart.tableLabel.trim() : "");
            o.put("status", OrderStatus.PAID);
            o.put("subtotal", subtotal);
            o.put("discount_type", discount > 0 ? cart.discountType : OrderMath.DISCOUNT_NONE);
            o.put("discount_value", discount > 0 ? cart.discountValue : 0);
            o.put("discount_amount", discount);
            o.put("service_fee", service);
            o.put("total", total);
            o.put("created_at", now);
            long orderId = db.insertOrThrow("orders", null, o);

            for (CartLine l : cart.lines) {
                ContentValues it = new ContentValues();
                it.put("order_id", orderId);
                it.put("product_id", l.productId);
                it.put("size_id", l.sizeId);
                it.put("category_id", l.categoryId);
                it.put("category_name", l.categoryName == null ? "" : l.categoryName);
                it.put("name", l.name);
                it.put("unit_price", l.unitPrice);
                it.put("qty", l.qty);
                it.put("line_total", l.total());
                db.insertOrThrow("order_items", null, it);
            }
            insertPayment(db, orderId, shift.id, PayMethod.CASH, cashPaid, now);
            insertPayment(db, orderId, shift.id, PayMethod.CARD, cardPaid, now);
            if (discount > 0) {
                Audit.log(db, user.name, "خصم", "طلب " + orderId + ": " + Money.number(discount));
            }
            db.setTransactionSuccessful();
            return orderId;
        } finally {
            db.endTransaction();
        }
    }

    private static void insertPayment(SQLiteDatabase db, long orderId, long shiftId, String method,
                                      long amount, long now) {
        if (amount <= 0) {
            return;
        }
        ContentValues p = new ContentValues();
        p.put("order_id", orderId);
        p.put("shift_id", shiftId);
        p.put("method", method);
        p.put("amount", amount);
        p.put("created_at", now);
        db.insertOrThrow("payments", null, p);
    }

    private static Order read(Cursor c) {
        Order o = new Order();
        o.id = c.getLong(0);
        o.uuid = c.getString(1);
        o.invoiceNo = c.getString(2);
        o.shiftId = c.getLong(3);
        o.shiftNo = c.getLong(4);
        o.userId = c.getLong(5);
        o.cashierName = c.getString(6);
        o.type = c.getString(7);
        o.tableLabel = c.isNull(8) ? "" : c.getString(8);
        o.status = c.getString(9);
        o.subtotal = c.getLong(10);
        o.discountType = c.getString(11);
        o.discountValue = c.getLong(12);
        o.discountAmount = c.getLong(13);
        o.total = c.getLong(14);
        o.createdAt = c.getLong(15);
        o.printCount = c.getInt(16);
        o.serviceFee = c.getLong(17);
        return o;
    }

    public Order get(long id) {
        SQLiteDatabase db = db();
        Order o;
        try (Cursor c = db.rawQuery("SELECT " + COLS + " FROM orders o JOIN shifts s ON s.id=o.shift_id WHERE o.id=?",
                new String[]{String.valueOf(id)})) {
            if (!c.moveToFirst()) {
                return null;
            }
            o = read(c);
        }
        loadDetails(db, o);
        return o;
    }

    private static void loadDetails(SQLiteDatabase db, Order o) {
        String[] arg = {String.valueOf(o.id)};
        try (Cursor c = db.rawQuery("SELECT id,product_id,size_id,category_id,category_name,name,unit_price,qty,line_total "
                + "FROM order_items WHERE order_id=? ORDER BY id", arg)) {
            while (c.moveToNext()) {
                OrderItem i = new OrderItem();
                i.id = c.getLong(0);
                i.orderId = o.id;
                i.productId = c.getLong(1);
                i.sizeId = c.getLong(2);
                i.categoryId = c.getLong(3);
                i.categoryName = c.getString(4);
                i.name = c.getString(5);
                i.unitPrice = c.getLong(6);
                i.qty = c.getInt(7);
                i.lineTotal = c.getLong(8);
                o.items.add(i);
            }
        }
        try (Cursor c = db.rawQuery("SELECT id,method,amount FROM payments WHERE order_id=? ORDER BY id", arg)) {
            while (c.moveToNext()) {
                Payment p = new Payment();
                p.id = c.getLong(0);
                p.orderId = o.id;
                p.method = c.getString(1);
                p.amount = c.getLong(2);
                o.payments.add(p);
            }
        }
        try (Cursor c = db.rawQuery("SELECT reason,user_name,approved_by,cancelled_at FROM cancellations WHERE order_id=?", arg)) {
            if (c.moveToFirst()) {
                o.cancelReason = c.getString(0);
                String by = c.getString(1);
                String approved = c.isNull(2) ? null : c.getString(2);
                o.cancelledBy = approved != null && !approved.equals(by) ? by + " (بموافقة " + approved + ")" : by;
                o.cancelledAt = c.getLong(3);
            }
        }
        try (Cursor c = db.rawQuery("SELECT COALESCE(SUM(amount),0) FROM refunds WHERE order_id=?", arg)) {
            if (c.moveToFirst()) {
                o.refundedAmount = c.getLong(0);
            }
        }
    }

    /** معايير البحث في الطلبات. القيم الفارغة/الصفرية تعني «الكل». */
    public static final class Query {
        public String invoiceNo;
        public long from;
        public long to;
        public String type;
        public long shiftId;
        public String status;
        public int limit = 300;
    }

    public List<Order> search(Query q) {
        StringBuilder sql = new StringBuilder("SELECT " + COLS + " FROM orders o JOIN shifts s ON s.id=o.shift_id WHERE 1=1");
        List<String> args = new ArrayList<>();
        if (q.invoiceNo != null && !q.invoiceNo.trim().isEmpty()) {
            sql.append(" AND o.invoice_no LIKE ?");
            args.add("%" + Money.normalizeDigits(q.invoiceNo) + "%");
        }
        if (q.from > 0) {
            sql.append(" AND o.created_at>=?");
            args.add(String.valueOf(q.from));
        }
        if (q.to > 0) {
            sql.append(" AND o.created_at<?");
            args.add(String.valueOf(q.to));
        }
        if (q.type != null && !q.type.isEmpty()) {
            sql.append(" AND o.order_type=?");
            args.add(q.type);
        }
        if (q.shiftId > 0) {
            sql.append(" AND o.shift_id=?");
            args.add(String.valueOf(q.shiftId));
        }
        if (q.status != null && !q.status.isEmpty()) {
            sql.append(" AND o.status=?");
            args.add(q.status);
        }
        sql.append(" ORDER BY o.id DESC LIMIT ").append(Math.max(1, q.limit));
        SQLiteDatabase db = db();
        List<Order> out = new ArrayList<>();
        try (Cursor c = db.rawQuery(sql.toString(), args.toArray(new String[0]))) {
            while (c.moveToNext()) {
                out.add(read(c));
            }
        }
        for (Order o : out) {
            loadDetails(db, o);
        }
        return out;
    }

    public void markPrinted(long orderId) {
        db().execSQL("UPDATE orders SET print_count=print_count+1 WHERE id=?", new Object[]{orderId});
    }

    /**
     * يلغي طلباً معتمداً دون حذفه.
     * السياسة: الإلغاء مسموح فقط لطلبات الوردية المفتوحة حالياً، حتى لا تتغير أرقام الورديات المغلقة.
     * الإلغاء لا يعني استرجاع المال؛ الاسترجاع يُسجَّل فقط إذا أُدخل مبلغه فعلياً.
     *
     * @param refundCash النقد المُعاد للزبون (0 إن لم يُعَد شيء)
     * @param refundCard المبلغ المُعاد إلكترونياً (0 إن لم يُعَد شيء)
     */
    public void cancel(long orderId, String reason, User user, User approver,
                       long refundCash, long refundCard) {
        if (user == null) {
            throw new AppException("سجّل الدخول أولاً");
        }
        String r = reason == null ? "" : reason.trim();
        if (r.isEmpty()) {
            throw new AppException("سبب الإلغاء إلزامي");
        }
        if (!user.isManager() && (approver == null || !approver.isManager())) {
            throw new AppException("إلغاء الطلب المعتمد يتطلب موافقة مدير");
        }
        if (refundCash < 0 || refundCard < 0) {
            throw new AppException("مبلغ الاسترجاع لا يمكن أن يكون سالباً");
        }
        SQLiteDatabase db = db();
        db.beginTransaction();
        try {
            Order o = get(orderId);
            if (o == null) {
                throw new AppException("الطلب غير موجود");
            }
            if (o.isCancelled()) {
                throw new AppException("الطلب ملغى مسبقاً");
            }
            Shift open = new ShiftRepo(ctx).openShift();
            if (open == null || open.id != o.shiftId) {
                throw new AppException("لا يمكن إلغاء طلب من وردية مغلقة. الإلغاء مسموح لطلبات الوردية المفتوحة فقط.");
            }
            if (refundCash > o.paidBy(PayMethod.CASH)) {
                throw new AppException("الاسترجاع النقدي أكبر من المبلغ المدفوع نقداً");
            }
            if (refundCard > o.paidBy(PayMethod.CARD)) {
                throw new AppException("الاسترجاع الإلكتروني أكبر من المبلغ المدفوع إلكترونياً");
            }
            long now = System.currentTimeMillis();
            ContentValues st = new ContentValues();
            st.put("status", OrderStatus.CANCELLED);
            db.update("orders", st, "id=? AND status='PAID'", new String[]{String.valueOf(orderId)});

            ContentValues cv = new ContentValues();
            cv.put("order_id", orderId);
            cv.put("shift_id", open.id);
            cv.put("reason", r);
            cv.put("user_name", user.name);
            cv.put("approved_by", user.isManager() ? user.name : approver.name);
            cv.put("cancelled_at", now);
            db.insertOrThrow("cancellations", null, cv);

            insertRefund(db, orderId, open.id, PayMethod.CASH, refundCash, user.name, now);
            insertRefund(db, orderId, open.id, PayMethod.CARD, refundCard, user.name, now);

            Audit.log(db, user.name, "إلغاء طلب", o.invoiceNo + " (" + Money.number(o.total) + ") السبب: " + r
                    + (refundCash + refundCard > 0 ? " | استرجاع " + Money.number(refundCash + refundCard) : ""));
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private static void insertRefund(SQLiteDatabase db, long orderId, long shiftId, String method,
                                     long amount, String user, long now) {
        if (amount <= 0) {
            return;
        }
        ContentValues cv = new ContentValues();
        cv.put("order_id", orderId);
        cv.put("shift_id", shiftId);
        cv.put("method", method);
        cv.put("amount", amount);
        cv.put("user_name", user);
        cv.put("created_at", now);
        db.insertOrThrow("refunds", null, cv);
    }

    /** آخر طلب معتمد في الوردية (لإعادة الطباعة السريعة). */
    public Order lastOrder() {
        try (Cursor c = db().rawQuery("SELECT id FROM orders ORDER BY id DESC LIMIT 1", null)) {
            return c.moveToFirst() ? get(c.getLong(0)) : null;
        }
    }
}
