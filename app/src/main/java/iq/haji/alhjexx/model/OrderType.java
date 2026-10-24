package iq.haji.alhjexx.model;

/** نوعا الطلب الوحيدان في النظام. لا يوجد نوع توصيل. */
public final class OrderType {
    public static final String DINE_IN = "DINE_IN";
    public static final String TAKEAWAY = "TAKEAWAY";

    private OrderType() {
    }

    public static String label(String type) {
        return DINE_IN.equals(type) ? "داخل المطعم" : "سفري";
    }

    public static boolean isValid(String type) {
        return DINE_IN.equals(type) || TAKEAWAY.equals(type);
    }
}
