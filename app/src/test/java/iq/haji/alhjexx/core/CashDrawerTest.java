package iq.haji.alhjexx.core;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class CashDrawerTest {

    @Test
    public void expectedCashFormula() {
        // افتتاحي 50,000 + نقد 120,000 − استرجاع 5,000 − مصروفات 10,000 + إيداع 2,000 − سحب 20,000
        assertEquals(137_000, CashDrawer.expected(50_000, 120_000, 5_000, 10_000, 2_000, 20_000));
    }

    @Test
    public void difference() {
        assertEquals(-1500, CashDrawer.difference(98_500, 100_000));
        assertEquals(500, CashDrawer.difference(100_500, 100_000));
    }
}
