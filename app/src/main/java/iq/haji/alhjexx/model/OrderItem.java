package iq.haji.alhjexx.model;

/** سطر طلب معتمد: الاسم والسعر منسوخان وقت الاعتماد ولا يتأثران بتعديل المنيو لاحقاً. */
public class OrderItem {
    public long id;
    public long orderId;
    public long productId;
    public long sizeId;
    public long categoryId;
    public String categoryName;
    public String name;
    public long unitPrice;
    public int qty;
    public long lineTotal;
}
