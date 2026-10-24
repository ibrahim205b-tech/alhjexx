package iq.haji.alhjexx.print;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextDirectionHeuristics;
import android.text.TextPaint;

import iq.haji.alhjexx.core.AppException;

/** يرسم PrintDoc كصورة أبيض/أسود بعرض الطابعة. يُستخدم للطباعة وللمعاينة على الشاشة. */
public final class DocRenderer {

    /** عرض الطباعة لورق 58mm: 384 نقطة (48 بايت). */
    public static final int WIDTH_58MM = 384;
    private static final int MAX_HEIGHT = 30000;

    private final int width;
    private final int margin;
    private final float base;

    public DocRenderer(int widthDots, int marginDots, float baseTextSize) {
        this.width = (widthDots / 8) * 8;
        this.margin = marginDots;
        this.base = baseTextSize;
    }

    public static DocRenderer default58() {
        return new DocRenderer(WIDTH_58MM, 6, 22f);
    }

    public Bitmap render(PrintDoc doc) {
        Pass measure = new Pass(null);
        measure.run(doc);
        int h = (int) Math.ceil(measure.y) + 8;
        if (h > MAX_HEIGHT) {
            throw new AppException("المستند طويل جداً للطباعة دفعة واحدة. اختر فترة أقصر.");
        }
        Bitmap bmp;
        try {
            bmp = Bitmap.createBitmap(width, h, Bitmap.Config.ARGB_8888);
        } catch (OutOfMemoryError e) {
            throw new AppException("الذاكرة غير كافية لتجهيز الطباعة");
        }
        Canvas canvas = new Canvas(bmp);
        canvas.drawColor(Color.WHITE);
        new Pass(canvas).run(doc);
        return bmp;
    }

    private final class Pass {
        private final Canvas canvas;
        float y = 4;
        float boxTop = -1;

        Pass(Canvas canvas) {
            this.canvas = canvas;
        }

        int cw() {
            return width - 2 * margin;
        }

        TextPaint paint(float size, boolean bold, int color) {
            TextPaint p = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(color);
            p.setTextSize(base * size);
            p.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            return p;
        }

        float text(String value, float x, float top, int w, float size, boolean bold, int color,
                   Layout.Alignment alignment) {
            if (value == null || value.isEmpty() || w <= 0) {
                return 0;
            }
            StaticLayout layout = StaticLayout.Builder.obtain(value, 0, value.length(),
                            paint(size, bold, color), w)
                    .setAlignment(alignment)
                    .setTextDirection(TextDirectionHeuristics.RTL)
                    .setIncludePad(false)
                    .build();
            if (canvas != null) {
                canvas.save();
                canvas.translate(x, top);
                layout.draw(canvas);
                canvas.restore();
            }
            return layout.getHeight();
        }

        Layout.Alignment align(PrintDoc.Align a) {
            // مع اتجاه RTL: NORMAL = يمين، OPPOSITE = يسار.
            if (a == PrintDoc.Align.CENTER) {
                return Layout.Alignment.ALIGN_CENTER;
            }
            return a == PrintDoc.Align.LEFT ? Layout.Alignment.ALIGN_OPPOSITE : Layout.Alignment.ALIGN_NORMAL;
        }

        void line(float yy, float stroke, boolean dashed) {
            if (canvas == null) {
                return;
            }
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(Color.BLACK);
            p.setStrokeWidth(stroke);
            if (dashed) {
                p.setStyle(Paint.Style.STROKE);
                p.setPathEffect(new DashPathEffect(new float[]{6f, 5f}, 0f));
            }
            canvas.drawLine(margin, yy, width - margin, yy, p);
        }

        void run(PrintDoc doc) {
            for (PrintDoc.Line l : doc.lines) {
                switch (l.kind) {
                    case TEXT:
                        y += text(l.cells[0], margin, y, cw(), l.size, l.bold, Color.BLACK, align(l.align)) + 3;
                        break;
                    case PAIR:
                        pair(l);
                        break;
                    case ROW:
                        row(l);
                        break;
                    case RULE:
                        line(y + 3, 2f, false);
                        y += 9;
                        break;
                    case DOUBLE_RULE:
                        line(y + 2, 2f, false);
                        line(y + 7, 2f, false);
                        y += 13;
                        break;
                    case DASHED:
                        line(y + 3, 1f, true);
                        y += 7;
                        break;
                    case BANNER:
                        banner(l);
                        break;
                    case SPACE:
                        y += l.size;
                        break;
                    case BOX_START:
                        boxTop = y;
                        y += 8;
                        break;
                    case BOX_END:
                        y += 4;
                        if (canvas != null && boxTop >= 0) {
                            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                            p.setColor(Color.BLACK);
                            p.setStrokeWidth(3f);
                            p.setStyle(Paint.Style.STROKE);
                            canvas.drawRect(margin + 1, boxTop, width - margin - 1, y, p);
                        }
                        boxTop = -1;
                        y += 6;
                        break;
                    default:
                        break;
                }
            }
        }

        void pair(PrintDoc.Line l) {
            String label = l.cells[0];
            String value = l.cells[1] == null ? "" : l.cells[1];
            TextPaint p = paint(l.size, l.bold, Color.BLACK);
            int valueW = Math.min((int) Math.ceil(p.measureText(value)) + 4, (int) (cw() * 0.6f));
            int gap = 8;
            int labelW = cw() - valueW - gap;
            float h1 = text(label, margin + valueW + gap, y, labelW, l.size, l.bold, Color.BLACK,
                    Layout.Alignment.ALIGN_NORMAL);
            float h2 = text(value, margin, y, valueW, l.size, l.bold, Color.BLACK,
                    Layout.Alignment.ALIGN_OPPOSITE);
            y += Math.max(h1, h2) + 4;
        }

        void row(PrintDoc.Line l) {
            int n = l.cells.length;
            float sum = 0;
            for (int i = 0; i < n; i++) {
                sum += l.weights == null ? 1 : l.weights[i];
            }
            int gap = 4;
            int usable = cw() - gap * (n - 1);
            float x = width - margin;
            float h = 0;
            for (int i = 0; i < n; i++) {
                float wgt = l.weights == null ? 1 : l.weights[i];
                int w = (int) (usable * wgt / sum);
                x -= w;
                Layout.Alignment a = i == 0 ? Layout.Alignment.ALIGN_NORMAL
                        : (i == n - 1 ? Layout.Alignment.ALIGN_OPPOSITE : Layout.Alignment.ALIGN_CENTER);
                h = Math.max(h, text(l.cells[i], x, y, w, l.size, l.bold, Color.BLACK, a));
                x -= gap;
            }
            y += h + 3;
        }

        void banner(PrintDoc.Line l) {
            float pad = 5;
            float top = y;
            float h = text(l.cells[0], margin, top + pad, cw(), l.size, true, Color.WHITE,
                    Layout.Alignment.ALIGN_CENTER);
            if (canvas != null) {
                Paint bg = new Paint();
                bg.setColor(Color.BLACK);
                canvas.drawRect(margin, top, width - margin, top + h + pad * 2, bg);
                text(l.cells[0], margin, top + pad, cw(), l.size, true, Color.WHITE,
                        Layout.Alignment.ALIGN_CENTER);
            }
            y = top + h + pad * 2 + 3;
        }
    }
}
