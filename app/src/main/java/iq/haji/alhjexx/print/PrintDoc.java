package iq.haji.alhjexx.print;

import java.util.ArrayList;
import java.util.List;

/**
 * مستند طباعة منطقي (عناوين، أزواج قيمة، جداول، خطوط) مستقل عن الطابعة.
 * يُرسم كصورة بعرض الورق ثم يُرسل بأوامر ESC/POS، فيظهر النص العربي متصلاً
 * وصحيح الاتجاه حتى على الطابعات التي لا تدعم العربية في خطوطها الداخلية.
 */
public final class PrintDoc {

    public enum Kind { TEXT, PAIR, ROW, RULE, DOUBLE_RULE, DASHED, BANNER, SPACE, BOX_START, BOX_END }

    public enum Align { RIGHT, CENTER, LEFT }

    public static final class Line {
        public final Kind kind;
        public final String[] cells;
        public final float[] weights;
        public final float size;
        public final boolean bold;
        public final Align align;

        Line(Kind kind, String[] cells, float[] weights, float size, boolean bold, Align align) {
            this.kind = kind;
            this.cells = cells;
            this.weights = weights;
            this.size = size;
            this.bold = bold;
            this.align = align;
        }
    }

    /** أحجام نسبية لحجم الخط الأساسي. */
    public static final float SMALL = 0.8f;
    public static final float NORMAL = 1f;
    public static final float LARGE = 1.3f;
    public static final float HUGE = 1.6f;

    public final List<Line> lines = new ArrayList<>();

    public PrintDoc text(String s, float size, boolean bold, Align align) {
        lines.add(new Line(Kind.TEXT, new String[]{s == null ? "" : s}, null, size, bold, align));
        return this;
    }

    public PrintDoc center(String s, float size, boolean bold) {
        return text(s, size, bold, Align.CENTER);
    }

    public PrintDoc right(String s, float size, boolean bold) {
        return text(s, size, bold, Align.RIGHT);
    }

    /** عنوان على اليمين وقيمة على اليسار. */
    public PrintDoc pair(String label, String value, float size, boolean bold) {
        lines.add(new Line(Kind.PAIR, new String[]{label, value}, null, size, bold, Align.RIGHT));
        return this;
    }

    /**
     * صف جدول من اليمين إلى اليسار. الخلية الأولى (الاسم) يمين ويلتف نصها،
     * والبقية أعمدة أرقام. weights تحدد نسب العرض.
     */
    public PrintDoc row(String[] cells, float[] weights, float size, boolean bold) {
        lines.add(new Line(Kind.ROW, cells, weights, size, bold, Align.RIGHT));
        return this;
    }

    public PrintDoc rule() {
        lines.add(new Line(Kind.RULE, null, null, 1, false, Align.CENTER));
        return this;
    }

    public PrintDoc doubleRule() {
        lines.add(new Line(Kind.DOUBLE_RULE, null, null, 1, false, Align.CENTER));
        return this;
    }

    public PrintDoc dashed() {
        lines.add(new Line(Kind.DASHED, null, null, 1, false, Align.CENTER));
        return this;
    }

    /** شريط أسود بنص أبيض لعناوين الأقسام. */
    public PrintDoc banner(String s) {
        lines.add(new Line(Kind.BANNER, new String[]{s}, null, NORMAL, true, Align.CENTER));
        return this;
    }

    public PrintDoc space(float dots) {
        lines.add(new Line(Kind.SPACE, null, null, dots, false, Align.CENTER));
        return this;
    }

    public PrintDoc boxStart() {
        lines.add(new Line(Kind.BOX_START, null, null, 1, false, Align.CENTER));
        return this;
    }

    public PrintDoc boxEnd() {
        lines.add(new Line(Kind.BOX_END, null, null, 1, false, Align.CENTER));
        return this;
    }
}
