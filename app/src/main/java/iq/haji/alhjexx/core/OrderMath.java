package iq.haji.alhjexx.core;

/**
 * قواعد الحساب الموحّدة للطلب. كل الشاشات والتقارير والاختبارات تستخدم هذه الدوال.
 *
 * <pre>
 * إجمالي السطر   = سعر الوحدة × الكمية
 * المجموع الفرعي = مجموع إجماليات الأسطر
 * الخصم الثابت   = القيمة المدخلة
 * الخصم بالنسبة  = floor(المجموع الفرعي × النسبة ÷ 100)   (تقريب للأسفل لصالح الزبون لا يتجاوز الإجمالي)
 * الإجمالي النهائي = المجموع الفرعي − مبلغ الخصم + خدمة الصالة
 * </pre>
 * الخصم يُحسب على الأصناف فقط، وخدمة الصالة مبلغ ثابت يُضاف بعده.
 */
public final class OrderMath {

    public static final String DISCOUNT_NONE = "NONE";
    public static final String DISCOUNT_FIXED = "FIXED";
    public static final String DISCOUNT_PERCENT = "PERCENT";

    public static final int MAX_QTY = 999;

    private OrderMath() {
    }

    public static long lineTotal(long unitPrice, int qty) {
        if (unitPrice < 0) {
            throw new IllegalArgumentException("سعر الوحدة لا يمكن أن يكون سالباً");
        }
        if (qty <= 0 || qty > MAX_QTY) {
            throw new IllegalArgumentException("الكمية يجب أن تكون بين 1 و " + MAX_QTY);
        }
        return Math.multiplyExact(unitPrice, (long) qty);
    }

    /**
     * يحسب مبلغ الخصم ويتحقق من صحته.
     *
     * @throws IllegalArgumentException برسالة عربية إذا كان الخصم غير صالح.
     */
    public static long discountAmount(long subtotal, String type, long value) {
        if (type == null || DISCOUNT_NONE.equals(type) || value == 0) {
            return 0;
        }
        if (value < 0) {
            throw new IllegalArgumentException("قيمة الخصم لا يمكن أن تكون سالبة");
        }
        if (DISCOUNT_FIXED.equals(type)) {
            if (value > subtotal) {
                throw new IllegalArgumentException("الخصم أكبر من إجمالي الطلب");
            }
            return value;
        }
        if (DISCOUNT_PERCENT.equals(type)) {
            if (value > 100) {
                throw new IllegalArgumentException("نسبة الخصم يجب ألا تتجاوز 100%");
            }
            return subtotal * value / 100;
        }
        throw new IllegalArgumentException("نوع خصم غير معروف");
    }

    public static long total(long subtotal, long discountAmount) {
        return total(subtotal, discountAmount, 0);
    }

    public static long total(long subtotal, long discountAmount, long serviceFee) {
        if (serviceFee < 0) {
            throw new IllegalArgumentException("خدمة الصالة لا يمكن أن تكون سالبة");
        }
        long t = subtotal - discountAmount + serviceFee;
        if (t < 0) {
            throw new IllegalArgumentException("الإجمالي لا يمكن أن يكون سالباً");
        }
        return t;
    }

    /**
     * يتحقق من أجزاء الدفع: لا قيم سالبة، ومجموعها يساوي المستحق تماماً.
     */
    public static void validatePayments(long due, long cash, long card) {
        if (cash < 0 || card < 0) {
            throw new IllegalArgumentException("مبالغ الدفع لا يمكن أن تكون سالبة");
        }
        if (cash + card != due) {
            throw new IllegalArgumentException("مجموع الدفع (" + Money.format(cash + card)
                    + ") لا يساوي المبلغ المستحق (" + Money.format(due) + ")");
        }
    }
}
