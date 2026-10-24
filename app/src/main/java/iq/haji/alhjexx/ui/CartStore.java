package iq.haji.alhjexx.ui;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import iq.haji.alhjexx.data.Settings;
import iq.haji.alhjexx.model.Cart;
import iq.haji.alhjexx.model.CartLine;
import iq.haji.alhjexx.model.OrderType;

/** يحفظ الطلب الجاري غير المعتمد حتى لا يضيع إذا أُغلق التطبيق فجأة. */
final class CartStore {

    private static final String KEY = "draft_cart";

    private CartStore() {
    }

    static void save(Context c, Cart cart) {
        try {
            JSONObject j = new JSONObject();
            j.put("uuid", cart.clientUuid);
            j.put("type", cart.type);
            j.put("table", cart.tableLabel);
            j.put("dtype", cart.discountType);
            j.put("dvalue", cart.discountValue);
            JSONArray arr = new JSONArray();
            for (CartLine l : cart.lines) {
                JSONObject o = new JSONObject();
                o.put("p", l.productId);
                o.put("s", l.sizeId);
                o.put("c", l.categoryId);
                o.put("cn", l.categoryName);
                o.put("n", l.name);
                o.put("u", l.unitPrice);
                o.put("q", l.qty);
                arr.put(o);
            }
            j.put("lines", arr);
            new Settings(c).set(KEY, j.toString());
        } catch (JSONException ignored) {
            // لا شيء: المسودة اختيارية
        }
    }

    static Cart load(Context c) {
        Cart cart = new Cart();
        String raw = new Settings(c).get(KEY, null);
        if (raw == null || raw.isEmpty()) {
            return cart;
        }
        try {
            JSONObject j = new JSONObject(raw);
            cart.clientUuid = j.optString("uuid", cart.clientUuid);
            String type = j.optString("type", OrderType.TAKEAWAY);
            cart.type = OrderType.isValid(type) ? type : OrderType.TAKEAWAY;
            cart.tableLabel = j.optString("table", "");
            cart.discountType = j.optString("dtype", cart.discountType);
            cart.discountValue = j.optLong("dvalue", 0);
            JSONArray arr = j.optJSONArray("lines");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    CartLine l = new CartLine();
                    l.productId = o.getLong("p");
                    l.sizeId = o.optLong("s", 0);
                    l.categoryId = o.optLong("c", 0);
                    l.categoryName = o.optString("cn", "");
                    l.name = o.getString("n");
                    l.unitPrice = o.getLong("u");
                    l.qty = Math.max(1, o.optInt("q", 1));
                    cart.lines.add(l);
                }
            }
            cart.clampDiscount();
        } catch (JSONException | IllegalArgumentException e) {
            return new Cart();
        }
        return cart;
    }

    static void clear(Context c) {
        new Settings(c).set(KEY, "");
    }
}
