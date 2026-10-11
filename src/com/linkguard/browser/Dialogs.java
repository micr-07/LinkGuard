package com.linkguard.browser;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.view.inputmethod.InputMethodManager;
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

    /** 输入一个网址（会自动归一化成根网址）。 */
    public static void inputDomain(final Activity act, int titleRes, int hintRes, final OnText cb) {
        final EditText et = new EditText(act);
        et.setHint(hintRes);
        et.setSingleLine(true);
        et.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_URI);

        FrameLayout wrap = new FrameLayout(act);
        int pad = (int) (20 * act.getResources().getDisplayMetrics().density);
        wrap.setPadding(pad, pad / 2, pad, 0);
        wrap.addView(et, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));

        AlertDialog dlg = new AlertDialog.Builder(act)
                .setTitle(titleRes)
                .setView(wrap)
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        String host = HostUtil.normalizeInput(et.getText().toString());
                        if (!host.isEmpty()) {
                            cb.onText(host);
                        }
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .create();
        dlg.show();

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
        pickBrowser(act, title, allowDefault ? R.string.use_default_browser : 0, cb);
    }

    /**
     * 同上，但可以自定义第一项（“不使用默认”项）的文案；{@code defaultLabelRes} 传 0 表示没有这一项。
     */
    public static void pickBrowser(final Activity act, String title, final int defaultLabelRes,
                                   final OnPick cb) {
        final boolean allowDefault = defaultLabelRes != 0;
        final List<Target> list = BrowserCatalog.browsers(act);
        if (list.isEmpty()) {
            new AlertDialog.Builder(act)
                    .setTitle(R.string.browser_list_empty_title)
                    .setMessage(R.string.browser_list_empty_msg_short)
                    .setPositiveButton(R.string.ok, null)
                    .show();
            return;
        }
        final List<String> labels = new ArrayList<String>();
        if (allowDefault) {
            labels.add(act.getString(defaultLabelRes));
        }
        for (Target t : list) {
            labels.add(t.preferred ? t.label + "（当前默认）" : t.label);
        }
        new AlertDialog.Builder(act)
                .setTitle(title)
                .setItems(labels.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        int idx = allowDefault ? which - 1 : which;
                        if (idx < 0) {
                            cb.onPick(null, true);
                        } else {
                            cb.onPick(list.get(idx), false);
                        }
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
