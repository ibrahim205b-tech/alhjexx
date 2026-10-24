package iq.haji.alhjexx.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ScrollView;

import iq.haji.alhjexx.R;
import iq.haji.alhjexx.core.AppException;
import iq.haji.alhjexx.print.DocRenderer;
import iq.haji.alhjexx.print.PrintDoc;

/** معاينة مطابقة تماماً لما سيُطبع: نفس الصورة التي تُرسل للطابعة. */
public final class DocPreview extends ScrollView {

    private final ImageView image;
    private Bitmap current;

    public DocPreview(Context c) {
        super(c);
        setBackgroundResource(R.drawable.card);
        int p = Ui.dp(c, 12);
        setPadding(p, p, p, p);
        FrameLayout frame = new FrameLayout(c);
        image = new ImageView(c);
        image.setAdjustViewBounds(true);
        image.setScaleType(ImageView.ScaleType.FIT_START);
        image.setBackgroundColor(0xFFFFFFFF);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Ui.dp(c, 420), FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_HORIZONTAL);
        frame.addView(image, lp);
        addView(frame, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    public void show(PrintDoc doc) {
        try {
            Bitmap b = DocRenderer.default58().render(doc);
            image.setImageBitmap(b);
            if (current != null && current != b) {
                current.recycle();
            }
            current = b;
            scrollTo(0, 0);
        } catch (AppException e) {
            Ui.toast(getContext(), e.getMessage());
        }
    }
}
