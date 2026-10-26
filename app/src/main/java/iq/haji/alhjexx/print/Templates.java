package iq.haji.alhjexx.print;

import java.util.List;

import iq.haji.alhjexx.core.CashDrawer;
import iq.haji.alhjexx.core.Dates;
import iq.haji.alhjexx.core.Money;
import iq.haji.alhjexx.core.OrderMath;
import iq.haji.alhjexx.data.ReportRepo;
import iq.haji.alhjexx.model.CashAdjustment;
import iq.haji.alhjexx.model.Expense;
import iq.haji.alhjexx.model.Order;
import iq.haji.alhjexx.model.OrderItem;
import iq.haji.alhjexx.model.OrderType;
import iq.haji.alhjexx.model.PayMethod;
import iq.haji.alhjexx.model.Payment;
import iq.haji.alhjexx.model.Shift;

import static iq.haji.alhjexx.print.PrintDoc.HUGE;
import static iq.haji.alhjexx.print.PrintDoc.LARGE;
import static iq.haji.alhjexx.print.PrintDoc.NORMAL;
import static iq.haji.alhjexx.print.PrintDoc.SMALL;

/** قوالب الطباعة المصممة لورق 58mm. كل قالب يبني مستنداً منطقياً من بيانات قاعدة البيانات. */
public final class Templates {

    /** أعمدة جدول الأصناف: الاسم | العدد | السعر | المجموع */
    private static final float[] ITEM_COLS = {3.0f, 0.9f, 1.6f, 1.9f};

    private Templates() {
    }

    private static void header(PrintDoc d, String restaurant, String phone, String title) {
        d.space(2);
        d.center(restaurant, HUGE, true);
        if (phone != null && !phone.trim().isEmpty()) {
            d.center("هاتف: " + phone.trim(), SMALL, false);
        }
        d.space(4);
        if (title != null) {
            d.banner(title);
            d.space(4);
        }
    }

    private static String n(long v) {
        return Money.number(v);
    }

    // ===================== الفاتورة =====================

    public static PrintDoc invoice(Order o, String restaurant, String phone, String footer,
                                   boolean showTable, boolean reprint) {
        PrintDoc d = new PrintDoc();
        header(d, restaurant, phone, null);
        d.center("فاتورة رقم", SMALL, false);
        d.center(o.invoiceNo, LARGE, true);
        d.center(Dates.dateTime(o.createdAt), SMALL, false);
        d.rule();
        d.pair("نوع الطلب:", OrderType.label(o.type), NORMAL, true);
        if (showTable && OrderType.DINE_IN.equals(o.type) && o.tableLabel != null && !o.tableLabel.isEmpty()) {
            d.pair("الطاولة:", o.tableLabel, NORMAL, true);
        }
        d.rule();
        d.row(new String[]{"الصنف", "العدد", "السعر", "المجموع"}, ITEM_COLS, SMALL, true);
        d.rule();
        for (OrderItem i : o.items) {
            d.row(new String[]{i.name, String.valueOf(i.qty), n(i.unitPrice), n(i.lineTotal)}, ITEM_COLS, 0.9f, false);
        }
        d.rule();
        d.pair("المجموع:", Money.format(o.subtotal), NORMAL, false);
        if (o.discountAmount > 0) {
            String label = OrderMath.DISCOUNT_PERCENT.equals(o.discountType)
                    ? "الخصم (" + o.discountValue + "%):" : "الخصم:";
            d.pair(label, "- " + Money.format(o.discountAmount), NORMAL, false);
        }
        if (o.serviceFee > 0) {
            d.pair("خدمة الصالة:", Money.format(o.serviceFee), NORMAL, false);
        }
        d.boxStart();
        d.center("المبلغ النهائي", NORMAL, true);
        d.center(Money.format(o.total), HUGE, true);
        d.boxEnd();
        for (Payment p : o.payments) {
            d.pair(PayMethod.label(p.method) + ":", Money.format(p.amount), SMALL, false);
        }
        if (o.isCancelled()) {
            d.space(4);
            d.banner("ملغاة");
        }
        if (reprint) {
            d.center("(نسخة معاد طباعتها)", SMALL, false);
        }
        if (footer != null && !footer.trim().isEmpty()) {
            d.space(6);
            d.center(footer.trim(), NORMAL, true);
        }
        d.space(10);
        return d;
    }

    // ===================== ملخص المبيعات (مشترك) =====================

    private static void salesBlock(PrintDoc d, ReportRepo.Summary s) {
        d.banner("المبيعات");
        d.space(3);
        d.pair("عدد الطلبات:", n(s.orderCount), NORMAL, true);
        d.pair("داخل المطعم:", n(s.dineInCount) + " / " + n(s.dineInTotal), SMALL, false);
        d.pair("سفري:", n(s.takeawayCount) + " / " + n(s.takeawayTotal), SMALL, false);
        d.dashed();
        d.pair("إجمالي المبيعات:", Money.format(s.gross), NORMAL, false);
        d.pair("الخصومات (" + s.discountedOrders + "):", "- " + Money.format(s.discounts), NORMAL, false);
        if (s.serviceFees > 0) {
            d.pair("خدمة الصالة (" + s.serviceOrders + "):", "+ " + Money.format(s.serviceFees), NORMAL, false);
        }
        d.pair("صافي المبيعات:", Money.format(s.net), NORMAL, true);
        d.dashed();
        d.pair("نقداً:", Money.format(s.salesCash), SMALL, false);
        d.pair("بطاقة / إلكتروني:", Money.format(s.salesCard), SMALL, false);
        d.dashed();
        d.pair("الملغاة (" + s.cancelledCount + "):", Money.format(s.cancelledTotal), NORMAL, false);
        if (s.refundsCash + s.refundsCard > 0) {
            d.pair("استرجاع نقدي:", Money.format(s.refundsCash), SMALL, false);
            d.pair("استرجاع إلكتروني:", Money.format(s.refundsCard), SMALL, false);
        }
        d.space(4);
    }

    private static void categoriesBlock(PrintDoc d, List<ReportRepo.CategorySale> cats) {
        if (cats.isEmpty()) {
            return;
        }
        for (ReportRepo.CategorySale c : cats) {
            d.space(4);
            d.banner("القسم: " + c.name);
            d.row(new String[]{"الصنف", "العدد", "السعر", "المجموع"}, ITEM_COLS, SMALL, true);
            d.rule();
            for (ReportRepo.ItemSale i : c.items) {
                d.row(new String[]{i.name, n(i.qty), n(i.unitPrice), n(i.total)}, ITEM_COLS, 0.85f, false);
            }
            d.rule();
            d.pair("إجمالي القسم (" + n(c.qty) + " قطعة):", Money.format(c.total), NORMAL, true);
        }
        d.space(4);
    }

    // ===================== تقرير الوردية =====================

    public static PrintDoc shiftReport(String restaurant, Shift sh, ReportRepo.Summary s,
                                       List<Expense> expenses, List<CashAdjustment> adjustments,
                                       List<Order> cancelled, int reopenCount) {
        PrintDoc d = new PrintDoc();
        header(d, restaurant, null, "تقرير الوردية #" + sh.shiftNo);
        d.pair("الكاشير:", sh.cashierName, NORMAL, false);
        d.pair("البداية:", Dates.dateTime(sh.openedAt), SMALL, false);
        d.pair("النهاية:", sh.isOpen() ? "مفتوحة" : Dates.dateTime(sh.closedAt), SMALL, false);
        if (!sh.isOpen() && sh.closedBy != null) {
            d.pair("أغلقها:", sh.closedBy, SMALL, false);
        }
        if (reopenCount > 0) {
            d.pair("أعيد فتحها:", reopenCount + " مرة", SMALL, true);
        }
        d.doubleRule();
        salesBlock(d, s);
        categoriesBlock(d, s.categories);

        if (!cancelled.isEmpty()) {
            d.banner("الطلبات الملغاة");
            for (Order o : cancelled) {
                d.pair(o.invoiceNo, Money.format(o.total), SMALL, true);
                d.right("السبب: " + o.cancelReason, SMALL, false);
                d.dashed();
            }
            d.space(4);
        }

        if (!expenses.isEmpty()) {
            d.banner("المصروفات");
            for (Expense e : expenses) {
                d.pair(e.category + (e.description == null || e.description.isEmpty() ? "" : " - " + e.description),
                        n(e.amount), SMALL, false);
            }
            d.pair("إجمالي المصروفات:", Money.format(s.expensesCash), NORMAL, true);
            d.space(4);
        }
        if (!adjustments.isEmpty()) {
            d.banner("تسويات الصندوق");
            for (CashAdjustment a : adjustments) {
                d.pair((CashAdjustment.IN.equals(a.direction) ? "إيداع: " : "سحب: ") + a.reason,
                        n(a.amount), SMALL, false);
            }
            d.space(4);
        }

        long expected = CashDrawer.expected(sh.openingCash, s.cashReceived, s.refundsCash,
                s.expensesCash, s.payIns, s.payOuts);
        d.banner("الصندوق");
        d.space(3);
        d.pair("النقدية الافتتاحية:", n(sh.openingCash), SMALL, false);
        d.pair("+ النقد المقبوض:", n(s.cashReceived), SMALL, false);
        d.pair("- الاسترجاع النقدي:", n(s.refundsCash), SMALL, false);
        d.pair("- المصروفات النقدية:", n(s.expensesCash), SMALL, false);
        d.pair("+ الإيداعات:", n(s.payIns), SMALL, false);
        d.pair("- السحوبات:", n(s.payOuts), SMALL, false);
        d.rule();
        d.pair("المتوقع في الصندوق:", Money.format(sh.isOpen() ? expected : sh.expectedCash), NORMAL, true);
        if (!sh.isOpen()) {
            d.pair("الفعلي عند العد:", Money.format(sh.countedCash), NORMAL, true);
            String sign = sh.difference > 0 ? "زيادة " : (sh.difference < 0 ? "عجز " : "");
            d.boxStart();
            d.center("الفرق", NORMAL, true);
            d.center(sign + Money.format(Math.abs(sh.difference)), LARGE, true);
            d.boxEnd();
            if (sh.closeNote != null && !sh.closeNote.isEmpty()) {
                d.right("ملاحظة: " + sh.closeNote, SMALL, false);
            }
        } else {
            d.center("(الوردية مفتوحة — الأرقام غير نهائية)", SMALL, false);
        }
        d.space(6);
        d.center("طُبع: " + Dates.dateTime(System.currentTimeMillis()), SMALL, false);
        d.space(10);
        return d;
    }

    // ===================== تقارير المبيعات =====================

    public static PrintDoc salesReport(String restaurant, String title, String period, ReportRepo.Summary s) {
        PrintDoc d = new PrintDoc();
        header(d, restaurant, null, title);
        d.center(period, SMALL, true);
        d.doubleRule();
        salesBlock(d, s);
        categoriesBlock(d, s.categories);
        d.banner("حركة الصندوق في الفترة");
        d.pair("النقد المقبوض:", Money.format(s.cashReceived), SMALL, false);
        d.pair("المصروفات (" + s.expenseCount + "):", Money.format(s.expensesCash), SMALL, false);
        d.pair("الإيداعات / السحوبات:", n(s.payIns) + " / " + n(s.payOuts), SMALL, false);
        d.space(6);
        d.center("طُبع: " + Dates.dateTime(System.currentTimeMillis()), SMALL, false);
        d.space(10);
        return d;
    }

    public static PrintDoc categoryReport(String restaurant, String period, List<ReportRepo.CategorySale> cats) {
        PrintDoc d = new PrintDoc();
        header(d, restaurant, null, "تقرير الأقسام");
        d.center(period, SMALL, true);
        d.doubleRule();
        if (cats.isEmpty()) {
            d.center("لا توجد مبيعات في هذه الفترة", NORMAL, false);
        }
        long total = 0;
        for (ReportRepo.CategorySale c : cats) {
            total += c.total;
        }
        categoriesBlock(d, cats);
        if (cats.size() > 1) {
            d.doubleRule();
            d.pair("الإجمالي:", Money.format(total), NORMAL, true);
        }
        d.space(10);
        return d;
    }

    public static PrintDoc productReport(String restaurant, String productName, String period,
                                         List<ReportRepo.ItemSale> items) {
        PrintDoc d = new PrintDoc();
        header(d, restaurant, null, "تقرير صنف");
        d.center(productName, LARGE, true);
        d.center(period, SMALL, true);
        d.doubleRule();
        long qty = 0;
        long total = 0;
        d.row(new String[]{"الصنف", "العدد", "السعر", "المجموع"}, ITEM_COLS, SMALL, true);
        d.rule();
        for (ReportRepo.ItemSale i : items) {
            d.row(new String[]{i.name, n(i.qty), n(i.unitPrice), n(i.total)}, ITEM_COLS, 0.85f, false);
            qty += i.qty;
            total += i.total;
        }
        if (items.isEmpty()) {
            d.center("لم يُبع هذا الصنف في الفترة", NORMAL, false);
        }
        d.rule();
        d.pair("عدد الوحدات:", n(qty), NORMAL, true);
        d.pair("إجمالي المبيعات:", Money.format(total), NORMAL, true);
        d.space(10);
        return d;
    }

    public static PrintDoc cancelledReport(String restaurant, String period, List<Order> orders) {
        PrintDoc d = new PrintDoc();
        header(d, restaurant, null, "الطلبات الملغاة");
        d.center(period, SMALL, true);
        d.doubleRule();
        long total = 0;
        long refunded = 0;
        for (Order o : orders) {
            d.pair("فاتورة " + o.invoiceNo, Money.format(o.total), NORMAL, true);
            d.pair("وقت الطلب:", Dates.dateTime(o.createdAt), SMALL, false);
            d.pair("وقت الإلغاء:", Dates.dateTime(o.cancelledAt), SMALL, false);
            d.pair("بواسطة:", o.cancelledBy, SMALL, false);
            d.right("السبب: " + o.cancelReason, SMALL, false);
            for (OrderItem i : o.items) {
                d.pair("  " + i.name + " × " + i.qty, n(i.lineTotal), SMALL, false);
            }
            if (o.refundedAmount > 0) {
                d.pair("المُسترجع:", Money.format(o.refundedAmount), SMALL, true);
            }
            d.dashed();
            total += o.total;
            refunded += o.refundedAmount;
        }
        if (orders.isEmpty()) {
            d.center("لا توجد طلبات ملغاة", NORMAL, false);
        }
        d.pair("العدد:", n(orders.size()), NORMAL, true);
        d.pair("القيمة الأصلية:", Money.format(total), NORMAL, true);
        d.pair("إجمالي المُسترجع:", Money.format(refunded), NORMAL, false);
        d.space(10);
        return d;
    }

    public static PrintDoc comparison(String restaurant, String title, String labelA, ReportRepo.Summary a,
                                      String labelB, ReportRepo.Summary b) {
        PrintDoc d = new PrintDoc();
        header(d, restaurant, null, title);
        float[] cols = {2.2f, 1.6f, 1.6f};
        d.row(new String[]{"البند", labelA, labelB}, cols, SMALL, true);
        d.rule();
        cmp(d, cols, "عدد الطلبات", a.orderCount, b.orderCount);
        cmp(d, cols, "داخل المطعم", a.dineInTotal, b.dineInTotal);
        cmp(d, cols, "سفري", a.takeawayTotal, b.takeawayTotal);
        cmp(d, cols, "إجمالي المبيعات", a.gross, b.gross);
        cmp(d, cols, "الخصومات", a.discounts, b.discounts);
        cmp(d, cols, "خدمة الصالة", a.serviceFees, b.serviceFees);
        cmp(d, cols, "صافي المبيعات", a.net, b.net);
        cmp(d, cols, "نقداً", a.salesCash, b.salesCash);
        cmp(d, cols, "إلكتروني", a.salesCard, b.salesCard);
        cmp(d, cols, "الملغاة", a.cancelledTotal, b.cancelledTotal);
        cmp(d, cols, "المصروفات", a.expensesCash, b.expensesCash);
        d.rule();
        long diff = b.net - a.net;
        d.pair("فرق صافي المبيعات:", (diff >= 0 ? "+" : "-") + Money.format(Math.abs(diff)), NORMAL, true);
        d.space(10);
        return d;
    }

    private static void cmp(PrintDoc d, float[] cols, String label, long a, long b) {
        d.row(new String[]{label, n(a), n(b)}, cols, 0.85f, false);
    }

    public static PrintDoc testPage(String restaurant, String printerName) {
        PrintDoc d = new PrintDoc();
        header(d, restaurant, null, "اختبار الطباعة");
        d.pair("الطابعة:", printerName, SMALL, false);
        d.pair("الوقت:", Dates.dateTime(System.currentTimeMillis()), SMALL, false);
        d.rule();
        d.right("نص عربي: مطعم الحجي يرحب بكم", NORMAL, false);
        d.right("أرقام: 0123456789", NORMAL, false);
        d.row(new String[]{"صنف طويل الاسم للتجربة", "12", "12,500", "150,000"}, ITEM_COLS, 0.9f, false);
        d.pair("مبلغ كبير:", Money.format(1_250_000), NORMAL, true);
        d.rule();
        d.center("إذا ظهرت هذه السطور كاملة وواضحة فالطابعة متوافقة", SMALL, false);
        d.space(10);
        return d;
    }
}
