package iq.haji.alhjexx.ui;

import android.os.Bundle;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import iq.haji.alhjexx.R;
import iq.haji.alhjexx.security.Session;

/** قالب الشاشات الإدارية: شريط علوي (رجوع، عنوان، أزرار) ومنطقة محتوى. */
public abstract class PageActivity extends BaseActivity {

    protected FrameLayout content;
    private LinearLayout actions;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!Session.isLoggedIn()) {
            return;
        }
        setContentView(R.layout.activity_page);
        content = findViewById(R.id.content);
        actions = findViewById(R.id.actions);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
    }

    protected void setPageTitle(String title) {
        ((TextView) findViewById(R.id.tvPageTitle)).setText(title);
    }

    protected TextView addAction(String text, boolean primary, Runnable r) {
        TextView b = Ui.button(this, text, primary);
        b.setOnClickListener(v -> r.run());
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, Ui.dp(this, 40));
        p.setMarginStart(Ui.dp(this, 8));
        actions.addView(b, p);
        return b;
    }

    /** يُستدعى من onCreate الفرعي: يعيد false إذا يجب إيقاف بناء الشاشة. */
    protected boolean ready() {
        return content != null && sessionValid();
    }
}
