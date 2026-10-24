package iq.haji.alhjexx.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Typeface;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import iq.haji.alhjexx.R;

/** أدوات واجهة مشتركة: تحويل الأبعاد، رسائل، حقول إدخال ونوافذ باتجاه RTL. */
public final class Ui {

    private Ui() {
    }

    public static int dp(Context c, float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics());
    }

    public static void toast(Context c, String msg) {
        Toast.makeText(c, msg, Toast.LENGTH_LONG).show();
    }

    public static AlertDialog.Builder dialog(Context c, String title) {
        return new AlertDialog.Builder(c).setTitle(title);
    }

    /** يعرض النافذة ويجعل اتجاهها من اليمين لليسار. */
    public static AlertDialog show(AlertDialog d) {
        d.show();
        if (d.getWindow() != null) {
            d.getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        }
        return d;
    }

    public static AlertDialog show(AlertDialog.Builder b) {
        return show(b.create());
    }

    public static void error(Context c, String message) {
        show(dialog(c, "تنبيه").setMessage(message).setPositiveButton(R.string.ok, null));
    }

    public static void confirm(Context c, String title, String message, String yes, Runnable onYes) {
        show(dialog(c, title).setMessage(message)
                .setPositiveButton(yes, (d, w) -> onYes.run())
                .setNegativeButton(R.string.back, null));
    }

    public static EditText input(Context c, String hint, int inputType, String value) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setInputType(inputType);
        e.setText(value == null ? "" : value);
        e.setSingleLine((inputType & InputType.TYPE_TEXT_FLAG_MULTI_LINE) == 0);
        e.setTextColor(c.getColor(R.color.text));
        e.setHintTextColor(c.getColor(R.color.text_3));
        e.setBackgroundResource(R.drawable.input);
        e.setPadding(dp(c, 12), dp(c, 10), dp(c, 12), dp(c, 10));
        e.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        e.setTextDirection(View.TEXT_DIRECTION_RTL);
        e.setTextSize(15);
        return e;
    }

    public static EditText money(Context c, String hint, String value) {
        return input(c, hint, InputType.TYPE_CLASS_NUMBER, value);
    }

    public static EditText pin(Context c, String hint) {
        EditText e = input(c, hint, InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD, "");
        e.setGravity(Gravity.CENTER);
        e.setTextSize(20);
        return e;
    }

    public static TextView label(Context c, String text) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextColor(c.getColor(R.color.text_2));
        t.setTextSize(13);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(c, 10), 0, dp(c, 4));
        return t;
    }

    public static TextView text(Context c, String text, float sp, int colorRes, boolean bold) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(sp);
        t.setTextColor(c.getColor(colorRes));
        if (bold) {
            t.setTypeface(Typeface.DEFAULT_BOLD);
        }
        return t;
    }

    /** عمود نموذج داخل نافذة مع حشوة وتمرير. */
    public static LinearLayout form(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        int p = dp(c, 20);
        l.setPadding(p, dp(c, 8), p, dp(c, 4));
        return l;
    }

    public static ScrollView scroll(Context c, View child) {
        ScrollView s = new ScrollView(c);
        s.addView(child, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return s;
    }

    public static TextView button(Context c, String text, boolean primary) {
        TextView b = new TextView(c);
        b.setText(text);
        b.setGravity(Gravity.CENTER);
        b.setTextSize(14);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(c.getColor(primary ? R.color.white : R.color.text));
        b.setBackgroundResource(primary ? R.drawable.btn_red : R.drawable.btn_dark);
        b.setMinHeight(dp(c, 44));
        b.setPadding(dp(c, 14), 0, dp(c, 14), 0);
        b.setClickable(true);
        b.setFocusable(true);
        return b;
    }

    public static LinearLayout.LayoutParams lp(Context c, int w, int h, int topMarginDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.topMargin = dp(c, topMarginDp);
        return p;
    }

    public static void hideKeyboard(Activity a) {
        View v = a.getCurrentFocus();
        if (v != null) {
            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager) a.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
            }
        }
    }

    /** يربط زر «حفظ» في نافذة بحيث لا تُغلق إلا إذا نجح التحقق (onSave يعيد true). */
    public interface Saver {
        boolean save();
    }

    public static AlertDialog showForm(Context c, String title, View content, String positive, Saver saver) {
        final AlertDialog d = dialog(c, title)
                .setView(content)
                .setPositiveButton(positive, null)
                .setNegativeButton(R.string.cancel, null)
                .create();
        d.setOnShowListener(x -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (saver.save()) {
                d.dismiss();
            }
        }));
        return show(d);
    }
}
