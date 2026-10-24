package iq.haji.alhjexx.model;

/** إيداع نقدي في الصندوق (IN) أو سحب منه (OUT) بإجراء إداري. */
public class CashAdjustment {
    public static final String IN = "IN";
    public static final String OUT = "OUT";

    public long id;
    public long shiftId;
    public String direction;
    public long amount;
    public String reason;
    public String userName;
    public long createdAt;
}
