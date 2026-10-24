package iq.haji.alhjexx.core;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MoneyTest {

    @Test
    public void format() {
        assertEquals("12,500 د.ع", Money.format(12500));
    }

    @Test
    public void parseArabicDigitsAndSeparators() {
        assertEquals(12500, Money.parse("١٢٬٥٠٠"));
        assertEquals(12500, Money.parse("12,500"));
        assertEquals(-1, Money.parse("abc"));
        assertEquals(-1, Money.parse(""));
        assertEquals(-1, Money.parse("-5"));
    }
}
