package iq.haji.alhjexx.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import iq.haji.alhjexx.R;

/** قائمة عامة: عنوان، سطر فرعي، شارة، وقيمة. */
public final class RowAdapter extends BaseAdapter {

    public static final class Row {
        public final String title;
        public final String sub;
        public final String value;
        public final Object tag;
        public String badge;
        public int badgeColor = R.color.text_2;

        public Row(String title, String sub, String value, Object tag) {
            this.title = title;
            this.sub = sub;
            this.value = value;
            this.tag = tag;
        }

        public Row badge(String text, int colorRes) {
            badge = text;
            badgeColor = colorRes;
            return this;
        }
    }

    private final Context ctx;
    private final List<Row> rows = new ArrayList<>();

    public RowAdapter(Context ctx) {
        this.ctx = ctx;
    }

    public void set(List<Row> list) {
        rows.clear();
        rows.addAll(list);
        notifyDataSetChanged();
    }

    public Row row(int i) {
        return rows.get(i);
    }

    @Override
    public int getCount() {
        return rows.size();
    }

    @Override
    public Object getItem(int i) {
        return rows.get(i);
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    @Override
    public View getView(int i, View v, ViewGroup parent) {
        View row = v != null ? v : LayoutInflater.from(ctx).inflate(R.layout.item_row, parent, false);
        Row r = rows.get(i);
        ((TextView) row.findViewById(R.id.tvTitle)).setText(r.title);
        TextView sub = row.findViewById(R.id.tvSub);
        sub.setText(r.sub == null ? "" : r.sub);
        sub.setVisibility(r.sub == null || r.sub.isEmpty() ? View.GONE : View.VISIBLE);
        ((TextView) row.findViewById(R.id.tvValue)).setText(r.value == null ? "" : r.value);
        TextView badge = row.findViewById(R.id.tvBadge);
        if (r.badge == null) {
            badge.setVisibility(View.GONE);
        } else {
            badge.setVisibility(View.VISIBLE);
            badge.setText(r.badge);
            badge.setTextColor(ctx.getColor(r.badgeColor));
        }
        return row;
    }
}
