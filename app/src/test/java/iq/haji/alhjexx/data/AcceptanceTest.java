package iq.haji.alhjexx.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.util.List;

import iq.haji.alhjexx.core.AppException;
import iq.haji.alhjexx.core.CashDrawer;
import iq.haji.alhjexx.core.OrderMath;
import iq.haji.alhjexx.model.Cart;
import iq.haji.alhjexx.model.Order;
import iq.haji.alhjexx.model.OrderType;
import iq.haji.alhjexx.model.PayMethod;
import iq.haji.alhjexx.model.Product;
import iq.haji.alhjexx.model.ProductSize;
import iq.haji.alhjexx.model.Shift;
import iq.haji.alhjexx.model.User;
import iq.haji.alhjexx.print.DocRenderer;
import iq.haji.alhjexx.print.EscPos;
import iq.haji.alhjexx.print.Templates;

/**
 * اختبارات القبول على قاعدة بيانات SQLite حقيقية (Robolectric):
 * الورديات، الطلبات، الخصم، الدفع، الإلغاء، التقارير، الصندوق، ثبات الأسعار التاريخية،
 * إعادة التشغيل، والنسخ الاحتياطي والاستعادة.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class AcceptanceTest {

    private Context ctx;
    private UserRepo users;
    private CatalogRepo catalog;
    private ShiftRepo shifts;
    private OrderRepo orders;
    private ReportRepo reports;
    private CashRepo cash;
    private User manager;
    private User cashier;
    private long kebab;
    private long pizza;
    private long drinks;

    @Before
    public void setUp() {
        ctx = ApplicationProvider.getApplicationContext();
        users = new UserRepo(ctx);
        catalog = new CatalogRepo(ctx);
        shifts = new ShiftRepo(ctx);
        orders = new OrderRepo(ctx);
        reports = new ReportRepo(ctx);
        cash = new CashRepo(ctx);
        manager = users.get(users.create("المدير", User.MANAGER, "9999", "test"));
        cashier = users.get(users.create("علي", User.CASHIER, "1111", "test"));

        long grills = catalog.addCategory("المشويات", "test");
        drinks = catalog.addCategory("المشروبات", "test");
        Product k = new Product();
        k.categoryId = grills;
        k.name = "كباب";
        k.price = 6000;
        kebab = catalog.saveProduct(k, "test");

        Product p = new Product();
        p.categoryId = grills;
        p.name = "بيتزا";
        ProductSize s1 = new ProductSize();
        s1.name = "صغير";
        s1.price = 5000;
        ProductSize s2 = new ProductSize();
        s2.name = "كبير";
        s2.price = 10000;
        p.sizes.add(s1);
        p.sizes.add(s2);
        pizza = catalog.saveProduct(p, "test");

        Product d = new Product();
        d.categoryId = drinks;
        d.name = "بيبسي";
        d.price = 500;
        catalog.saveProduct(d, "test");
    }

    @After
    public void tearDown() {
        Db.closeInstance();
        ctx.deleteDatabase(Db.NAME);
    }

    private Cart cart(String type, int kebabQty, int pepsiQty) {
        Cart c = new Cart();
        c.type = type;
        for (int i = 0; i < kebabQty; i++) {
            c.add(kebab, 0, 1, "المشويات", "كباب", 6000);
        }
        for (int i = 0; i < pepsiQty; i++) {
            c.add(99, 0, drinks, "المشروبات", "بيبسي", 500);
        }
        return c;
    }

    @Test
    public void fullShiftLifecycle() {
        // 1) فتح وردية برصيد افتتاحي، ومنع فتح وردية ثانية
        Shift s = shifts.open(cashier, 50_000);
        assertEquals(1, s.shiftNo);
        assertTrue(s.isOpen());
        assertThrows(AppException.class, () -> shifts.open(cashier, 0));

        // 2-5) طلب داخلي وطلب سفري بعدة أصناف وتعديل الكمية
        Cart dine = cart(OrderType.DINE_IN, 2, 2);   // 12,000 + 1,000
        dine.tableLabel = "5";
        dine.setQty(dine.lines.get(0), 3);           // كباب ×3 = 18,000
        assertEquals(19_000, dine.subtotal());

        // 6) خصم صحيح 10%
        dine.discountType = OrderMath.DISCOUNT_PERCENT;
        dine.discountValue = 10;
        assertEquals(1_900, dine.discountAmount());
        assertEquals(17_100, dine.total());

        // 7) الاعتماد مرة واحدة فقط حتى مع الضغط المكرر
        long id1 = orders.confirm(dine, 17_100, 0, cashier);
        long again = orders.confirm(dine, 17_100, 0, cashier);
        assertEquals(id1, again);

        Cart take = cart(OrderType.TAKEAWAY, 1, 0);  // 6,000 مختلط
        long id2 = orders.confirm(take, 2_000, 4_000, cashier);

        Cart third = cart(OrderType.TAKEAWAY, 1, 1); // 6,500 سيُلغى
        long id3 = orders.confirm(third, 6_500, 0, cashier);

        // 8-9) حفظ تفاصيل الطلب وأسعاره ومدفوعاته
        Order o1 = orders.get(id1);
        assertEquals(3, o1.items.get(0).qty);
        assertEquals(6_000, o1.items.get(0).unitPrice);
        assertEquals(18_000, o1.items.get(0).lineTotal);
        assertEquals(1_900, o1.discountAmount);
        assertEquals(17_100, o1.total);
        assertEquals("5", o1.tableLabel);
        assertEquals(17_100, o1.paidBy(PayMethod.CASH));
        Order o2 = orders.get(id2);
        assertEquals(2_000, o2.paidBy(PayMethod.CASH));
        assertEquals(4_000, o2.paidBy(PayMethod.CARD));
        assertFalse(o1.invoiceNo.equals(o2.invoiceNo));

        // دفع لا يساوي الإجمالي يُرفض
        assertThrows(AppException.class, () -> orders.confirm(cart(OrderType.TAKEAWAY, 1, 0), 5_000, 0, cashier));

        // 10) إلغاء يتطلب سبباً وموافقة مدير، مع استرجاع نقدي جزئي فعلي
        assertThrows(AppException.class, () -> orders.cancel(id3, "", cashier, manager, 0, 0));
        assertThrows(AppException.class, () -> orders.cancel(id3, "خطأ", cashier, null, 0, 0));
        orders.cancel(id3, "طلب الزبون الإلغاء", cashier, manager, 6_500, 0);
        assertThrows(AppException.class, () -> orders.cancel(id3, "مرة ثانية", manager, manager, 0, 0));
        Order o3 = orders.get(id3);
        assertTrue(o3.isCancelled());
        assertEquals(6_500, o3.total);           // القيمة الأصلية محفوظة
        assertEquals(6_500, o3.refundedAmount);
        assertEquals("طلب الزبون الإلغاء", o3.cancelReason);

        // مصروف وتسوية
        cash.addExpense(3_000, "مشتريات", "خبز", cashier.name);
        cash.addAdjustment("OUT", 10_000, "إيداع بنكي", manager.name);

        // 11-12) الملغى في تقريره، ومستبعد من صافي المبيعات
        ReportRepo.Summary sum = reports.summary(ReportRepo.Filter.shift(s.id));
        assertEquals(2, sum.orderCount);
        assertEquals(1, sum.dineInCount);
        assertEquals(1, sum.takeawayCount);
        assertEquals(19_000 + 6_000, sum.gross);
        assertEquals(1_900, sum.discounts);
        assertEquals(17_100 + 6_000, sum.net);
        assertEquals(1, sum.cancelledCount);
        assertEquals(6_500, sum.cancelledTotal);
        assertEquals(17_100 + 2_000, sum.salesCash);
        assertEquals(4_000, sum.salesCard);
        List<Order> cancelled = reports.cancelledOrders(0, Long.MAX_VALUE);
        assertEquals(1, cancelled.size());
        assertEquals(id3, cancelled.get(0).id);

        // 15) تقرير قسم: الأصناف بالسعر المعتمد وإجمالي القسم
        List<ReportRepo.CategorySale> drinksReport = reports.categorySales(ReportRepo.Filter.shift(s.id), drinks);
        assertEquals(1, drinksReport.size());
        assertEquals(2, drinksReport.get(0).qty);
        assertEquals(1_000, drinksReport.get(0).total);

        // 13) إغلاق الوردية وحساب النقدية المتوقعة
        long expected = CashDrawer.expected(50_000, 17_100 + 2_000 + 6_500, 6_500, 3_000, 0, 10_000);
        assertEquals(56_100, expected);
        Shift closed = shifts.close(s.id, 55_600, "عد مساءً", manager.name);
        assertFalse(closed.isOpen());
        assertEquals(expected, closed.expectedCash);
        assertEquals(55_600, closed.countedCash);
        assertEquals(-500, closed.difference);

        // بعد الإغلاق: لا طلبات ولا إلغاء
        assertThrows(AppException.class, () -> orders.confirm(cart(OrderType.TAKEAWAY, 1, 0), 6_000, 0, cashier));
        assertThrows(AppException.class, () -> orders.cancel(id2, "متأخر", manager, manager, 0, 0));

        // 14) تقرير الوردية يُبنى ويُحوَّل لأوامر طباعة
        byte[] escpos = EscPos.encode(DocRenderer.default58().render(Templates.shiftReport("مطعم الحجي", closed,
                reports.summary(ReportRepo.Filter.shift(s.id)), cash.expenses(s.id), cash.adjustments(s.id),
                reports.cancelledOrdersOfShift(s.id), shifts.reopenCount(s.id))), 3, true);
        assertTrue(escpos.length > 1000);

        // إعادة الفتح تتطلب مديراً وسبباً، وتُسجَّل
        assertThrows(AppException.class, () -> shifts.reopen(s.id, "سبب", cashier));
        assertThrows(AppException.class, () -> shifts.reopen(s.id, "", manager));
        shifts.reopen(s.id, "تصحيح عد الصندوق", manager);
        assertEquals(1, shifts.reopenCount(s.id));
        assertTrue(shifts.get(s.id).isOpen());
    }

    @Test
    public void priceChangeDoesNotAlterOldInvoices() {
        // 16
        shifts.open(cashier, 0);
        long id = orders.confirm(cart(OrderType.TAKEAWAY, 2, 0), 12_000, 0, cashier);
        Product k = catalog.product(kebab);
        k.price = 9_000;
        catalog.saveProduct(k, "test");
        Order old = orders.get(id);
        assertEquals(6_000, old.items.get(0).unitPrice);
        assertEquals(12_000, old.total);
        assertEquals(9_000, catalog.product(kebab).price);

        // الصنف المستخدم في طلب يُؤرشف ولا يُحذف
        catalog.deleteProduct(kebab, "test");
        assertNotNull(catalog.product(kebab));
        assertTrue(catalog.product(kebab).archived);
        assertEquals("كباب", orders.get(id).items.get(0).name);
    }

    @Test
    public void sizesHaveOwnPrices() {
        Product p = catalog.product(pizza);
        assertTrue(p.hasSizes());
        assertEquals(2, p.activeSizes().size());
        assertEquals(5_000, p.minPrice());
    }

    @Test
    public void dataSurvivesRestart() {
        // 17
        shifts.open(cashier, 1_000);
        long id = orders.confirm(cart(OrderType.TAKEAWAY, 1, 0), 6_000, 0, cashier);
        Db.closeInstance();
        OrderRepo reopened = new OrderRepo(ctx);
        assertNotNull(reopened.get(id));
        assertNotNull(new ShiftRepo(ctx).openShift());
    }

    @Test
    public void backupAndRestore() throws Exception {
        // 18
        shifts.open(cashier, 0);
        long id = orders.confirm(cart(OrderType.TAKEAWAY, 1, 0), 6_000, 0, cashier);
        BackupManager bm = new BackupManager(ctx);
        File out = new File(ctx.getCacheDir(), "backup-test.db");
        bm.backupTo(Uri.fromFile(out), "test");
        assertTrue(out.length() > 0);

        // بيانات بعد النسخة ستختفي بعد الاستعادة
        long later = new OrderRepo(ctx).confirm(cart(OrderType.TAKEAWAY, 1, 1), 6_500, 0, cashier);
        assertNotNull(new OrderRepo(ctx).get(later));

        File staged = bm.stage(Uri.fromFile(out));
        BackupManager.Check check = BackupManager.verify(staged);
        assertTrue(check.message, check.valid);
        assertEquals(1, check.orders);
        bm.restore(staged);

        OrderRepo after = new OrderRepo(ctx);
        assertNotNull(after.get(id));
        assertNull(after.get(later));

        // ملف غير صالح يُرفض
        File junk = new File(ctx.getCacheDir(), "junk.db");
        java.nio.file.Files.write(junk.toPath(), "not a database".getBytes());
        assertFalse(BackupManager.verify(junk).valid);
    }

    @Test
    public void noDeliveryFeatures() {
        // 19: لا يوجد نوع توصيل ولا أعمدة دلفري في قاعدة البيانات
        assertTrue(OrderType.isValid(OrderType.DINE_IN));
        assertTrue(OrderType.isValid(OrderType.TAKEAWAY));
        assertFalse(OrderType.isValid("DELIVERY"));
        try (Cursor c = Db.get(ctx).getReadableDatabase().rawQuery("PRAGMA table_info(orders)", null)) {
            while (c.moveToNext()) {
                String col = c.getString(1).toLowerCase();
                assertFalse(col, col.contains("driver") || col.contains("delivery") || col.contains("address"));
            }
        }
    }

    @Test
    public void dineInServiceFeeIsAddedAndPrinted() {
        shifts.open(cashier, 0);
        Cart c = cart(OrderType.DINE_IN, 1, 0);      // 6,000
        c.serviceFee = 2_000;
        assertEquals(8_000, c.total());
        long id = orders.confirm(c, 8_000, 0, cashier);
        Order o = orders.get(id);
        assertEquals(2_000, o.serviceFee);
        assertEquals(8_000, o.total);
        assertEquals(6_000, o.subtotal);

        // السفري لا تُحسب عليه خدمة صالة
        Cart t = cart(OrderType.TAKEAWAY, 1, 0);
        t.serviceFee = 2_000;
        long id2 = orders.confirm(t, 6_000, 0, cashier);
        assertEquals(0, orders.get(id2).serviceFee);

        ReportRepo.Summary sum = reports.summary(ReportRepo.Filter.range(0, Long.MAX_VALUE));
        assertEquals(2_000, sum.serviceFees);
        assertEquals(12_000, sum.gross);
        assertEquals(14_000, sum.net);
    }

    @Test
    public void officialMenuImportsOnceWithPizzaSizes() {
        OfficialMenu.Result r = OfficialMenu.importMenu(ctx, true, "test");
        assertEquals(16, r.categories);
        // «بيبسي 500» موجود مسبقاً في قسم المشروبات فيُعاد تفعيله بدل تكراره.
        assertEquals(OfficialMenu.itemCount(), r.added + r.alreadyPresent);
        OfficialMenu.Result again = OfficialMenu.importMenu(ctx, false, "test");
        assertEquals(0, again.added);
        int sized = 0;
        for (Product p : catalog.products(0, true)) {
            if (p.hasSizes()) {
                sized++;
                assertEquals(3, p.activeSizes().size());
            }
        }
        assertEquals(5, sized);
        assertEquals(OfficialMenu.itemCount(), catalog.products(0, true).size());
    }

    @Test
    public void pinsAreHashedAndVerified() {
        assertTrue(users.verify(manager.id, "9999"));
        assertFalse(users.verify(manager.id, "0000"));
        assertNotNull(users.findManagerByPin("9999"));
        assertNull(users.findManagerByPin("1111"));
        try (Cursor c = Db.get(ctx).getReadableDatabase().rawQuery("SELECT pin_hash FROM users", null)) {
            while (c.moveToNext()) {
                assertFalse(c.getString(0).contains("9999"));
            }
        }
        assertThrows(AppException.class, () -> users.setActive(manager.id, false, "test"));
    }
}
