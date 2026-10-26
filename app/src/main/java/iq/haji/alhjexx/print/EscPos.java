package iq.haji.alhjexx.print;

import android.graphics.Bitmap;
import android.graphics.Color;

import java.io.ByteArrayOutputStream;

/**
 * تحويل الصورة إلى أوامر ESC/POS نقطية (GS v 0) مع تهيئة وتغذية وقطع اختياري.
 * هذا الأمر مدعوم في أغلب الطابعات الحرارية الشائعة، لكن يجب تجربة كل طابعة فعلياً
 * باستخدام «اختبار الطباعة» قبل الاعتماد عليها.
 */
public final class EscPos {

    private static final int BAND_HEIGHT = 128;

    private EscPos() {
    }

    public static byte[] encode(Bitmap bmp, int feedLines, boolean cut) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0x1B);
        out.write(0x40); // ESC @ تهيئة
        int w = bmp.getWidth();
        int h = bmp.getHeight();
        int bytesPerRow = (w + 7) / 8;
        int[] pixels = new int[w];
        for (int top = 0; top < h; top += BAND_HEIGHT) {
            int bandH = Math.min(BAND_HEIGHT, h - top);
            out.write(0x1D);
            out.write(0x76);
            out.write(0x30);
            out.write(0x00);
            out.write(bytesPerRow & 0xFF);
            out.write((bytesPerRow >> 8) & 0xFF);
            out.write(bandH & 0xFF);
            out.write((bandH >> 8) & 0xFF);
            for (int y = top; y < top + bandH; y++) {
                bmp.getPixels(pixels, 0, w, 0, y, w, 1);
                for (int bx = 0; bx < bytesPerRow; bx++) {
                    int b = 0;
                    for (int bit = 0; bit < 8; bit++) {
                        int x = bx * 8 + bit;
                        if (x < w && isDark(pixels[x])) {
                            b |= 0x80 >> bit;
                        }
                    }
                    out.write(b);
                }
            }
        }
        out.write(0x1B);
        out.write(0x64);
        out.write(Math.max(0, Math.min(feedLines, 255))); // ESC d n تغذية
        if (cut) {
            out.write(0x1D);
            out.write(0x56);
            out.write(66);
            out.write(3); // GS V 66 n تغذية ثم قص جزئي؛ تتجاهله الطابعات بلا قاطع
        }
        return out.toByteArray();
    }

    private static boolean isDark(int argb) {
        if (Color.alpha(argb) < 128) {
            return false;
        }
        int lum = (Color.red(argb) * 299 + Color.green(argb) * 587 + Color.blue(argb) * 114) / 1000;
        return lum < 150;
    }
}
