package iq.haji.alhjexx.model;

public class Shift {
    public static final String OPEN = "OPEN";
    public static final String CLOSED = "CLOSED";

    public long id;
    public long shiftNo;
    public long userId;
    public String cashierName;
    public long openedAt;
    public long openingCash;
    public String status;
    public long closedAt;
    public String closedBy;
    public long countedCash;
    public long expectedCash;
    public long difference;
    public String closeNote;

    public boolean isOpen() {
        return OPEN.equals(status);
    }
}
