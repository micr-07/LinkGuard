package com.linkguard.browser;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

/**
 * 主界面上的设置行（M3 List 的一行）。
 *
 * <p>行高与内边距遵循 M3 List：单行 56dp / 双行 72dp、左右各 16dp、
 * 行间分隔线 1dp 且左右缩进 16dp。
 */
public final class Rows {

    /** 点击回调。用匿名内部类实现，避免依赖 lambda（构建环境没有 java.base.jmod）。 */
    public interface OnClick {
        void onClick();
    }

    private Rows() {
    }

    /**
     * 一行「跳转」条目。
     *
     * @param subtitle 副标题，可为 null
     * @param value    右侧取值，可为 null（不显示）
     */
    public static View add(LinearLayout parent, String title, String subtitle,
                           String value, final OnClick cb) {
        return add(parent, title, subtitle, value, 0, cb);
    }

    /** 带前导图标的一行（M3 列表行常见形态）。 */
    public static View add(LinearLayout parent, String title, String subtitle,
                           String value, int iconRes, final OnClick cb) {
        View row = inflate(parent);
        if (iconRes != 0) {
            ImageView ic = row.findViewById(R.id.rowIcon);
            ic.setImageResource(iconRes);
            ic.setVisibility(View.VISIBLE);
        }
        ((TextView) row.findViewById(R.id.rowTitle)).setText(title);

        TextView sub = row.findViewById(R.id.rowSub);
        if (subtitle == null || subtitle.length() == 0) {
            sub.setVisibility(View.GONE);
        } else {
            sub.setVisibility(View.VISIBLE);
            sub.setText(subtitle);
        }

        TextView val = row.findViewById(R.id.rowValue);
        if (value == null || value.length() == 0) {
            val.setVisibility(View.GONE);
        } else {
            val.setVisibility(View.VISIBLE);
            val.setText(value);
        }

        row.findViewById(R.id.rowChevron).setVisibility(View.VISIBLE);
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (cb != null) {
                    cb.onClick();
                }
            }
        });
        parent.addView(row);
        return row;
    }

    /**
     * 一行「开关」条目。先 setChecked 再挂监听，避免初始化时误写回配置。
     */
    public static Switch addSwitch(LinearLayout parent, String title, String subtitle,
                                   boolean checked, CompoundButton.OnCheckedChangeListener l) {
        return addSwitch(parent, title, subtitle, checked, 0, l);
    }

    public static Switch addSwitch(LinearLayout parent, String title, String subtitle,
                                   boolean checked, int iconRes,
                                   CompoundButton.OnCheckedChangeListener l) {
        View row = inflate(parent);
        if (iconRes != 0) {
            ImageView ic = row.findViewById(R.id.rowIcon);
            ic.setImageResource(iconRes);
            ic.setVisibility(View.VISIBLE);
        }
        ((TextView) row.findViewById(R.id.rowTitle)).setText(title);

        TextView sub = row.findViewById(R.id.rowSub);
        if (subtitle == null || subtitle.length() == 0) {
            sub.setVisibility(View.GONE);
        } else {
            sub.setVisibility(View.VISIBLE);
            sub.setText(subtitle);
        }

        // 布局里 Switch 没有 id，按类型找（见 item_setting.xml 的说明）
        final Switch sw = findSwitch(row);
        if (sw == null) {
            parent.addView(row);
            return null;
        }
        sw.setVisibility(View.VISIBLE);
        sw.setChecked(checked);
        if (l != null) {
            sw.setOnCheckedChangeListener(l);
        }
        // 点整行也能切换
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sw.setChecked(!sw.isChecked());
            }
        });
        parent.addView(row);
        return sw;
    }

    /** 行与行之间的分隔线（M3：1dp，左右缩进 16dp）。 */
    public static void divider(LinearLayout parent) {
        View line = new View(parent.getContext());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(parent, 1));
        lp.leftMargin = dp(parent, 16);
        lp.rightMargin = dp(parent, 16);
        line.setLayoutParams(lp);
        line.setBackgroundColor(Themes.outlineVariant(parent.getContext()));
        parent.addView(line);
    }

    /** 在一行里按类型找开关（布局故意不给它 id）。 */
    private static Switch findSwitch(View v) {
        if (v instanceof Switch) {
            return (Switch) v;
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                Switch s = findSwitch(g.getChildAt(i));
                if (s != null) {
                    return s;
                }
            }
        }
        return null;
    }

    private static View inflate(ViewGroup parent) {
        return LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_setting, parent, false);
    }

    private static int dp(View v, int value) {
        return (int) (value * v.getResources().getDisplayMetrics().density + 0.5f);
    }
}
