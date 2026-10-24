package iq.haji.alhjexx.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class OrderMathTest {

    @Test
    public void lineTotal() {
        assertEquals(15000, OrderMath.lineTotal(5000, 3));
    }

    @Test
    public void lineTotalRejectsBadQty() {
        assertThrows(IllegalArgumentException.class, () -> OrderMath.lineTotal(5000, 0));
        assertThrows(IllegalArgumentException.class, () -> OrderMath.lineTotal(5000, 1000));
    }

    @Test
    public void fixedDiscount() {
        assertEquals(2000, OrderMath.discountAmount(10000, OrderMath.DISCOUNT_FIXED, 2000));
        assertEquals(8000, OrderMath.total(10000, 2000));
    }

    @Test
    public void percentDiscountRoundsDown() {
        assertEquals(1000, OrderMath.discountAmount(10000, OrderMath.DISCOUNT_PERCENT, 10));
        assertEquals(1233, OrderMath.discountAmount(12333, OrderMath.DISCOUNT_PERCENT, 10));
    }

    @Test
    public void discountValidation() {
        assertThrows(IllegalArgumentException.class,
                () -> OrderMath.discountAmount(10000, OrderMath.DISCOUNT_FIXED, -1));
        assertThrows(IllegalArgumentException.class,
                () -> OrderMath.discountAmount(10000, OrderMath.DISCOUNT_FIXED, 10001));
        assertThrows(IllegalArgumentException.class,
                () -> OrderMath.discountAmount(10000, OrderMath.DISCOUNT_PERCENT, 101));
        assertEquals(0, OrderMath.discountAmount(10000, OrderMath.DISCOUNT_NONE, 500));
    }

    @Test
    public void paymentsMustMatchDue() {
        OrderMath.validatePayments(10000, 6000, 4000);
        assertThrows(IllegalArgumentException.class, () -> OrderMath.validatePayments(10000, 6000, 3000));
        assertThrows(IllegalArgumentException.class, () -> OrderMath.validatePayments(10000, -1, 10001));
    }
}
