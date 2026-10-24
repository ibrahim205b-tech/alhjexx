package iq.haji.alhjexx.core;

/**
 * معادلة النقدية المتوقعة في الصندوق لوردية واحدة.
 *
 * <pre>
 * المتوقع = النقدية الافتتاحية
 *         + النقد المقبوض فعلياً من الطلبات (كل أجزاء الدفع النقدي المسجلة في الوردية)
 *         − النقد المُعاد للزبائن (الاسترجاعات النقدية المسجلة في الوردية)
 *         − المصروفات المدفوعة نقداً من الصندوق
 *         + الإيداعات النقدية في الصندوق (تسويات إدخال)
 *         − السحوبات النقدية من الصندوق (تسويات إخراج)
 * </pre>
 *
 * ملاحظات:
 * <ul>
 *   <li>الطلب الملغى بدون استرجاع يبقى نقده في الصندوق، لذلك لا يُطرح إلا عند تسجيل استرجاع فعلي.</li>
 *   <li>المدفوعات الإلكترونية لا تدخل الصندوق.</li>
 *   <li>المصروفات تُعرض منفصلة ولا تُخصم من المبيعات.</li>
 * </ul>
 */
public final class CashDrawer {

    private CashDrawer() {
    }

    public static long expected(long openingCash,
                                long cashReceived,
                                long cashRefunds,
                                long cashExpenses,
                                long payIns,
                                long payOuts) {
        return openingCash + cashReceived - cashRefunds - cashExpenses + payIns - payOuts;
    }

    /** الفرق = الفعلي − المتوقع. موجب = زيادة في الصندوق، سالب = عجز. */
    public static long difference(long counted, long expected) {
        return counted - expected;
    }
}
