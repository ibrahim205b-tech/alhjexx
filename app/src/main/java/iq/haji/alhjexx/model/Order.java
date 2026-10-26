package iq.haji.alhjexx.model;

import java.util.ArrayList;
import java.util.List;

/** طلب معتمد كما هو محفوظ في قاعدة البيانات. */
public class Order {
    public long id;
    public String uuid;
    public String invoiceNo;
    public long shiftId;
    public long shiftNo;
    public long userId;
    public String cashierName;
    public String type;
    public String tableLabel;
    public String status;
    public long subtotal;
    public String discountType;
    public long discountValue;
    public long discountAmount;
    /** خدمة الصالة المضافة للطلب. */
    public long serviceFee;
    public long total;
    public long createdAt;
    public int printCount;

    public final List<OrderItem> items = new ArrayList<>();
    public final List<Payment> payments = new ArrayList<>();

    // بيانات الإلغاء إن وُجد
    public String cancelReason;
    public String cancelledBy;
    public long cancelledAt;
    public long refundedAmount;

    public boolean isCancelled() {
        return OrderStatus.CANCELLED.equals(status);
    }

    public long paidBy(String method) {
        long s = 0;
        for (Payment p : payments) {
            if (method.equals(p.method)) {
                s += p.amount;
            }
        }
        return s;
    }

    public String paymentLabel() {
        long cash = paidBy(PayMethod.CASH);
        long card = paidBy(PayMethod.CARD);
        if (cash > 0 && card > 0) {
            return "مختلط";
        }
        return card > 0 ? PayMethod.label(PayMethod.CARD) : PayMethod.label(PayMethod.CASH);
    }
}
