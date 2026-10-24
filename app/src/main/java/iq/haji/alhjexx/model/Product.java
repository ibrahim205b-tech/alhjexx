package iq.haji.alhjexx.model;

import java.util.ArrayList;
import java.util.List;

/** صنف في المنيو. إذا كانت له أحجام فالسعر يؤخذ من الحجم المختار. */
public class Product {
    public long id;
    public long categoryId;
    public String name;
    public long price;
    public boolean active = true;
    public boolean archived;
    public final List<ProductSize> sizes = new ArrayList<>();

    public boolean hasSizes() {
        for (ProductSize s : sizes) {
            if (s.active) {
                return true;
            }
        }
        return false;
    }

    public List<ProductSize> activeSizes() {
        List<ProductSize> out = new ArrayList<>();
        for (ProductSize s : sizes) {
            if (s.active) {
                out.add(s);
            }
        }
        return out;
    }

    public long minPrice() {
        long min = Long.MAX_VALUE;
        for (ProductSize s : activeSizes()) {
            min = Math.min(min, s.price);
        }
        return min == Long.MAX_VALUE ? price : min;
    }
}
