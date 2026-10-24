package iq.haji.alhjexx.model;

public final class OrderStatus {
    /** معتمد ومدفوع. */
    public static final String PAID = "PAID";
    /** ملغى بعد الاعتماد؛ يبقى في قاعدة البيانات للتدقيق. */
    public static final String CANCELLED = "CANCELLED";

    private OrderStatus() {
    }

    public static String label(String s) {
        return CANCELLED.equals(s) ? "ملغى" : "مدفوع";
    }
}
