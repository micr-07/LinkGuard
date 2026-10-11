package com.linkguard.browser;

import android.app.Activity;


import android.view.inputmethod.InputMethodManager;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;

import java.util.ArrayList;
import java.util.List;

/** 几个界面共用的小对话框。 */
public final class Dialogs {

    /** 选择浏览器。{@code useDefault} 为 true 表示“不指定 / 清空”。 */
    public interface OnPick {
        void onPick(Target target, boolean useDefault);
    }

    public interface OnText {
        void onText(String text);
    }

    private Dialogs() {
    }

    /**
     * 缩放输入框：只让输数字，回车即确认。
     * 输入框自己记住引用，值在 {@link #zoomValue} 里取。
     */
    private static EditText lastZoomField;

    public static View zoomField(Activity act, int current) {
        final EditText et = new EditText(act);
        et.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        et.setSingleLine(true);
        et.setText(String.valueOf(current));
        et.setSelection(et.getText().length());
        et.setBackgroundResource(R.drawable.md_field);
        int pad = (int) (16 * act.getResources().getDisplayMetrics().density);
        et.setPadding(pad, pad / 2, pad, pad / 2);
        lastZoomField = et;
        return et;
    }

    /** 读缩放输入框的值；非法就返回原值。 */
    public static int zoomValue(Activity act, int fallback) {
        if (lastZoomField == null) {
            return fallback;
        }
        try {
            int v = Integer.parseInt(lastZoomField.getText().toString().trim());
            return v;
        } catch (Exception e) {
            return fallback;
        }
    }
    /** 输入一个网址（会自动归一化成根网址）。 */
    public static void inputDomain(final Activity act, String title, String hint, final OnText cb) {
        final EditText et = new EditText(act);
        et.setHint(hint);
        et.setSingleLine(true);
        et.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_URI);

        et.setBackgroundResource(R.drawable.md_field);
        int pad = (int) (16 * act.getResources().getDisplayMetrics().density);
        et.setPadding(pad, pad / 2, pad, pad / 2);

        MdDialog.create(act)
                .title(title)
                .content(et)
                .negative(Lang.s(act, "cancel"), null)
                .positive(Lang.s(act, "ok"), new MdDialog.OnClick() {
                    @Override
                    public void onClick() {
                        String host = HostUtil.normalizeInput(et.getText().toString());
                        if (!host.isEmpty()) {
                            cb.onText(host);
                        }
                    }
                })
                .show();

        try {
            et.requestFocus();
            InputMethodManager imm = (InputMethodManager) act.getSystemService(Activity.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(et, InputMethodManager.SHOW_IMPLICIT);
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * 弹出浏览器选择列表。
     *
     * @param allowDefault true 时列表第一项是“不指定 / 使用默认”。
     */
    public static void pickBrowser(final Activity act, String title, final boolean allowDefault,
                                   final OnPick cb) {
        pickBrowser(act, title, allowDefault ? Lang.s(act, "use_default_browser") : null, cb);
    }

    /**
     * 同上，但可以自定义第一项（“不使用默认”项）的文案；{@code defaultLabelRes} 传 0 表示没有这一项。
     */
    public static void pickBrowser(final Activity act, String title, final String defaultLabel,
                                   final OnPick cb) {
        final boolean allowDefault = defaultLabel != null;
        final List<Target> list = BrowserCatalog.browsers(act);
        if (list.isEmpty()) {
            MdDialog.create(act)
                    .title(Lang.s(act, "browser_list_empty_title"))
                    .message(Lang.s(act, "browser_list_empty_msg_short"))
                    .positive(Lang.s(act, "ok"), null)
                    .show();
            return;
        }
        final List<String> labels = new ArrayList<String>();
        if (allowDefault) {
            labels.add(defaultLabel);
        }
        for (Target t : list) {
            labels.add(t.preferred ? t.label + "（当前默认）" : t.label);
        }
        MdDialog.create(act)
                .title(title)
                .items(labels.toArray(new CharSequence[0]), new MdDialog.OnItem() {
                    @Override
                    public void onItem(int which) {
                        int idx = allowDefault ? which - 1 : which;
                        if (idx < 0) {
                            cb.onPick(null, true);
                        } else {
                            cb.onPick(list.get(idx), false);
                        }
                    }
                })
                .negative(Lang.s(act, "cancel"), null)
                .show();
    }
}
