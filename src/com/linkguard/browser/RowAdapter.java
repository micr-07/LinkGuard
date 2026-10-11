package com.linkguard.browser;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 通用列表适配器：图标 + 两行文字 + 勾选框 + 删除按钮，按需显示。 */
public class RowAdapter extends BaseAdapter {

    /** 一行数据。{@code key} 用来标识这一行对应的网址或包名。 */
    public static final class Row {
        public final String key;
        public String title = "";
        public String subtitle = "";
        public boolean checked;
        public Drawable icon;

        public Row(String key) {
            this.key = key;
        }
    }

    /** 勾选框变化回调。 */
    public interface OnToggle {
        void onToggle(Row row, boolean checked);
    }

    /** 点删除回调。 */
    public interface OnDelete {
        void onDelete(Row row);
    }

    private final LayoutInflater inflater;
    private final List<Row> all = new ArrayList<Row>();
    private final List<Row> shown = new ArrayList<Row>();

    private boolean showCheck;
    private boolean showIcon;
    private boolean showDelete = true;
    private String query = "";
    private OnToggle onToggle;
    private OnDelete onDelete;

    public RowAdapter(Context c) {
        inflater = LayoutInflater.from(c);
    }

    public void setShowCheck(boolean v) {
        showCheck = v;
    }

    public void setShowIcon(boolean v) {
        showIcon = v;
    }

    public void setShowDelete(boolean v) {
        showDelete = v;
    }

    public void setOnToggle(OnToggle t) {
        onToggle = t;
    }

    public void setOnDelete(OnDelete d) {
        onDelete = d;
    }

    public void setRows(List<Row> rows) {
        all.clear();
        all.addAll(rows);
        applyFilter();
        notifyDataSetChanged();
    }

    public void setQuery(String q) {
        query = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        applyFilter();
        notifyDataSetChanged();
    }

    public int getTotalCount() {
        return all.size();
    }

    private void applyFilter() {
        shown.clear();
        if (query.isEmpty()) {
            shown.addAll(all);
            return;
        }
        for (Row r : all) {
            String title = r.title == null ? "" : r.title.toLowerCase(Locale.ROOT);
            String sub = r.subtitle == null ? "" : r.subtitle.toLowerCase(Locale.ROOT);
            if (title.contains(query) || sub.contains(query)
                    || (r.key != null && r.key.toLowerCase(Locale.ROOT).contains(query))) {
                shown.add(r);
            }
        }
    }

    @Override
    public int getCount() {
        return shown.size();
    }

    @Override
    public Row getItem(int position) {
        return shown.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View v = convertView;
        if (v == null) {
            v = inflater.inflate(R.layout.item_list_row, parent, false);
        }
        final Row row = getItem(position);

        ImageView iv = v.findViewById(R.id.ivIcon);
        if (showIcon) {
            iv.setVisibility(View.VISIBLE);
            if (row.icon != null) {
                iv.setImageDrawable(row.icon);
            } else {
                iv.setImageResource(android.R.drawable.sym_def_app_icon);
            }
        } else {
            iv.setVisibility(View.GONE);
        }

        TextView title = v.findViewById(R.id.tvTitle);
        title.setText(row.title);

        TextView sub = v.findViewById(R.id.tvSub);
        if (row.subtitle == null || row.subtitle.isEmpty()) {
            sub.setVisibility(View.GONE);
        } else {
            sub.setVisibility(View.VISIBLE);
            sub.setText(row.subtitle);
        }

        CheckBox cb = v.findViewById(R.id.cbCheck);
        if (showCheck) {
            cb.setVisibility(View.VISIBLE);
            cb.setOnCheckedChangeListener(null);
            cb.setChecked(row.checked);
            cb.setTag(row.key);
            cb.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(android.widget.CompoundButton buttonView, boolean isChecked) {
                    row.checked = isChecked;
                    if (onToggle != null) {
                        onToggle.onToggle(row, isChecked);
                    }
                }
            });
        } else {
            cb.setVisibility(View.GONE);
            cb.setOnCheckedChangeListener(null);
        }

        TextView del = v.findViewById(R.id.btnDel);
        if (showDelete) {
            del.setVisibility(View.VISIBLE);
            del.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View target) {
                    if (onDelete != null) {
                        onDelete.onDelete(row);
                    }
                }
            });
        } else {
            del.setVisibility(View.GONE);
            del.setOnClickListener(null);
        }

        return v;
    }
}
