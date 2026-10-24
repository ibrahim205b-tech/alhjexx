package iq.haji.alhjexx.model;

public final class PayMethod {
    public static final String CASH = "CASH";
    /** بطاقة/دفع إلكتروني: تسجيل محاسبي فقط، لا يوجد تكامل مع جهاز دفع. */
    public static final String CARD = "CARD";

    private PayMethod() {
    }

    public static String label(String m) {
        return CARD.equals(m) ? "بطاقة / إلكتروني" : "نقداً";
    }
}
