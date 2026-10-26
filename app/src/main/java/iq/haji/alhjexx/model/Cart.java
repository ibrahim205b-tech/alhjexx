package iq.haji.alhjexx.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import iq.haji.alhjexx.core.OrderMath;

/**
 * الطلب الجاري في الذاكرة. المعرّف clientUuid يُنشأ مرة واحدة لكل طلب
 * ويُحفظ كقيد UNIQUE في قاعدة البيانات لمنع تسجيل الطلب نفسه مرتين.
 */
public class Cart {
    public String clientUuid = UUID.randomUUID().toString();
    public String type = OrderType.TAKEAWAY;
    public String tableLabel = "";
    public String discountType = OrderMath.DISCOUNT_NONE;
    public long discountValue;
    /** خدمة الصالة المضافة لهذا الطلب (0 = بدون). */
    public long serviceFee;
    public final List<CartLine> lines = new ArrayList<>();

    public long subtotal() {
        long s = 0;
        for (CartLine l : lines) {
            s += l.total();
        }
        return s;
    }

    public long discountAmount() {
        return OrderMath.discountAmount(subtotal(), discountType, discountValue);
    }

    public long total() {
        return OrderMath.total(subtotal(), discountAmount(), serviceFee);
    }

    public int itemCount() {
        int n = 0;
        for (CartLine l : lines) {
            n += l.qty;
        }
        return n;
    }

    /** يضيف صنفاً، أو يزيد كميته إذا كان موجوداً بنفس الحجم. */
    public CartLine add(long productId, long sizeId, long categoryId, String categoryName,
                        String name, long unitPrice) {
        for (CartLine l : lines) {
            if (l.sameItem(productId, sizeId)) {
                if (l.qty < OrderMath.MAX_QTY) {
                    l.qty++;
                }
                return l;
            }
        }
        CartLine l = new CartLine();
        l.productId = productId;
        l.sizeId = sizeId;
        l.categoryId = categoryId;
        l.categoryName = categoryName;
        l.name = name;
        l.unitPrice = unitPrice;
        l.qty = 1;
        lines.add(l);
        return l;
    }

    public void setQty(CartLine line, int qty) {
        if (qty <= 0) {
            lines.remove(line);
        } else {
            line.qty = Math.min(qty, OrderMath.MAX_QTY);
        }
    }

    /** يُبقي الخصم صالحاً بعد تغيّر الأسطر (مثلاً إذا أصبح الخصم الثابت أكبر من الإجمالي). */
    public void clampDiscount() {
        if (OrderMath.DISCOUNT_FIXED.equals(discountType) && discountValue > subtotal()) {
            discountValue = subtotal();
        }
        if (lines.isEmpty()) {
            discountType = OrderMath.DISCOUNT_NONE;
            discountValue = 0;
            serviceFee = 0;
        }
        if (!OrderType.DINE_IN.equals(type)) {
            serviceFee = 0;
        }
    }
}
