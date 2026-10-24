package iq.haji.alhjexx.print;

import android.content.Context;
import android.graphics.Bitmap;

import iq.haji.alhjexx.core.AppException;
import iq.haji.alhjexx.data.Settings;

/** نقطة الدخول الموحدة للطباعة: مستند → صورة → ESC/POS → Bluetooth. */
public final class Printing {

    private Printing() {
    }

    public static void print(Context ctx, PrintDoc doc, int copies, BtPrinter.Callback cb) {
        Settings s = new Settings(ctx);
        byte[] data;
        Bitmap bmp = null;
        try {
            bmp = DocRenderer.default58().render(doc);
            byte[] one = EscPos.encode(bmp, s.getInt(Settings.PRINT_FEED_LINES, 4),
                    s.getBool(Settings.PRINT_CUT, true));
            int n = Math.max(1, Math.min(copies, 3));
            data = new byte[one.length * n];
            for (int i = 0; i < n; i++) {
                System.arraycopy(one, 0, data, i * one.length, one.length);
            }
        } catch (AppException e) {
            cb.onDone(false, e.getMessage());
            return;
        } finally {
            if (bmp != null) {
                bmp.recycle();
            }
        }
        BtPrinter.get(ctx).send(data, cb);
    }
}
