package iq.haji.alhjexx.model;

import iq.haji.alhjexx.core.OrderMath;

/** سطر في الطلب الجاري قبل الاعتماد. */
public class CartLine {
    public long productId;
    public long sizeId;
    public long categoryId;
    public String categoryName;
    public String name;
    public long unitPrice;
    public int qty = 1;

    public long total() {
        return OrderMath.lineTotal(unitPrice, qty);
    }

    public boolean sameItem(long productId, long sizeId) {
        return this.productId == productId && this.sizeId == sizeId;
    }
}
