package com.linkguard.browser;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 自绘的 Material 3 对话框。
 *
 * <p><b>为什么不用系统的 AlertDialog</b>：它的圆角、配色、按钮排布全部由设备主题决定，
 * Android 15 上恰好是圆角，到了 Android 10 及更早就是方角旧样式，跟应用里的 MD3 不是一套。
 * 这里按 M3 规格自绘：容器圆角 28dp（CornerExtraLarge）、海拔 Level3、遮罩 32%，
 * 按钮用文字按钮右对齐 —— 这样在 Android 8/10/15 上长得一致。
 */
public final class MdDialog {

    /** 点击回调（不用 lambda：构建环境缺 java.base.jmod）。 */
    public interface OnClick {
        void onClick();
    }

    /** 列表项点击回调，参数是下标。 */
    public interface OnItem {
        void onItem(int index);
    }

    private final Dialog dialog;
    private final TextView title;
    private final TextView message;
    private final LinearLayout list;
    private final FrameLayout custom;
    private final LinearLayout actions;

    private MdDialog(Activity act) {
        dialog = new Dialog(act);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.md_dialog);

        Window w = dialog.getWindow();
        if (w != null) {
            // 窗口背景透明，圆角交给卡片自己画；否则系统会在四角补上方的底色
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            WindowManager.LayoutParams lp = w.getAttributes();
            lp.dimAmount = 0.32f;
            w.setAttributes(lp);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }

        title = dialog.findViewById(R.id.mdTitle);
        message = dialog.findViewById(R.id.mdMessage);
        list = dialog.findViewById(R.id.mdList);
        custom = dialog.findViewById(R.id.mdCustom);
        actions = dialog.findViewById(R.id.mdActions);

        MdScrollView sv = dialog.findViewById(R.id.mdScroll);
        int screenH = act.getResources().getDisplayMetrics().heightPixels;
        sv.setMaxHeight((int) (screenH * 0.74f));
    }

    public static MdDialog create(Activity act) {
        return new MdDialog(act);
    }

    public MdDialog title(int res) {
        title.setText(res);
        return this;
    }

    public MdDialog title(CharSequence s) {
        title.setText(s);
        return this;
    }

    public MdDialog message(int res) {
        message.setVisibility(View.VISIBLE);
        message.setText(res);
        return this;
    }

    public MdDialog message(CharSequence s) {
        message.setVisibility(View.VISIBLE);
        message.setText(s);
        return this;
    }

    /** 自定义内容（输入框等）。 */
    public MdDialog content(View v) {
        custom.setVisibility(View.VISIBLE);
        custom.addView(v);
        return this;
    }

    /** 内容为空时不显示标题下方的空白。 */
    public MdDialog items(CharSequence[] labels, final OnItem cb) {
        list.setVisibility(View.VISIBLE);
        LayoutInflater inf = LayoutInflater.from(list.getContext());
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            TextView row = (TextView) inf.inflate(R.layout.item_dialog, list, false);
            row.setText(labels[i]);
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                    if (cb != null) {
                        cb.onItem(idx);
                    }
                }
            });
            list.addView(row);
        }
        return this;
    }

    /** 右侧文字按钮。{@code danger} 为 true 时用危险色（用于「屏蔽」这类不可逆操作）。 */
    public MdDialog action(CharSequence label, final OnClick cb, boolean danger) {
        Button b = (Button) LayoutInflater.from(actions.getContext())
                .inflate(R.layout.md_dialog_action, actions, false);
        b.setText(label);
        if (danger) {
            b.setTextColor(Themes.danger(actions.getContext()));
        }
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                if (cb != null) {
                    cb.onClick();
                }
            }
        });
        actions.addView(b);
        return this;
    }

    public MdDialog action(CharSequence label, OnClick cb) {
        return action(label, cb, false);
    }

    public MdDialog positive(CharSequence label, OnClick cb) {
        return action(label, cb, false);
    }

    public MdDialog negative(CharSequence label, OnClick cb) {
        return action(label, cb, false);
    }

    /** 危险操作的主按钮，红色。 */
    public MdDialog positiveDanger(CharSequence label, OnClick cb) {
        return action(label, cb, true);
    }

    /** 让对话框可以拿到标题右侧的空位，用于自定义排布（如主题预览的对勾）。 */
    public View actionsRow() {
        return actions;
    }

    public void dismiss() {
        dialog.dismiss();
    }

    public MdDialog show() {
        dialog.show();
        return this;
    }
}
