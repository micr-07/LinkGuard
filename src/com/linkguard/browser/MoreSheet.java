package com.linkguard.browser;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * 「更多」底部面板。
 *
 * <p>顶部的小白条可以上下拖：<b>收起档</b>只露出权重最高的三个处置动作，
 * <b>展开档</b>把预览设置和「打开应用主页面」也放出来。松手吸附到最近的一档。
 */
public final class MoreSheet {

    /** 面板上的动作回调。 */
    public interface Callbacks {
        void onAlwaysAllow();

        void onAlwaysBlock();

        void onBlockApp();

        void onOpenHome();

        void onZoom();

        void onPreviewToggle(boolean on);

        /** 复制完整链接。 */
        void onCopyLink();

        /** 隐私开关变化（由面板自己写 Prefs，这里只通知宿主刷新）。 */
        void onPrivacyChanged();
    }

    private final Activity act;
    private final Dialog dialog;
    private final LinearLayout panel;
    private final ScrollView scroll;
    private final LinearLayout content;

    private int collapsedH;
    private int expandedH;

    private MoreSheet(Activity act) {
        this.act = act;
        dialog = new Dialog(act);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.sheet_more);

        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            WindowManager.LayoutParams lp = w.getAttributes();
            lp.dimAmount = 0.32f;
            w.setAttributes(lp);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.setGravity(Gravity.BOTTOM);
        }

        panel = dialog.findViewById(R.id.sheetPanel);
        scroll = dialog.findViewById(R.id.sheetScroll);
        content = dialog.findViewById(R.id.sheetContent);

        // 点面板外面关掉
        dialog.findViewById(R.id.sheetScrim).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
        // 面板自己吃掉点击，避免穿透到遮罩
        panel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
            }
        });
    }

    public static MoreSheet create(Activity act) {
        return new MoreSheet(act);
    }

    // ------------------------------------------------------------------
    // 内容
    // ------------------------------------------------------------------

    private TextView zone(String text) {
        TextView tv = new TextView(act);
        int pl = dp(24);
        tv.setPadding(pl, dp(16), pl, dp(4));
        tv.setTextSize(13);
        tv.setTextColor(Themes.primary(act));
        tv.setTypeface(tv.getTypeface(), android.graphics.Typeface.BOLD);
        tv.setText(text);
        content.addView(tv);
        return tv;
    }

    private View row(int iconRes, String title, Rows.OnClick cb) {
        View v = Rows.add(content, title, null, null, iconRes, cb);
        return v;
    }

    private View dangerRow(int iconRes, String title, Rows.OnClick cb) {
        View v = row(iconRes, title, cb);
        ((TextView) v.findViewById(R.id.rowTitle)).setTextColor(Themes.danger(act));
        // 图标也跟着变红，跟设计稿一致
        ImageView ic = v.findViewById(R.id.rowIcon);
        if (ic != null) {
            ic.setImageTintList(ColorStateList.valueOf(Themes.danger(act)));
        }
        return v;
    }

    /**
     * 按「当前链接 + 来源应用」把整张面板搭出来。
     */
    public MoreSheet build(final String domain, final String appLabel, final Callbacks cb) {
        content.removeAllViews();

        TextView title = new TextView(act);
        int pl = dp(24);
        title.setPadding(pl, dp(6), pl, dp(2));
        title.setTextSize(22);
        title.setTextColor(Themes.onSurface(act));
        title.setText(Lang.s(act, "more"));
        content.addView(title);

        // ---- 记住选择（权重最高，收起时也看得见）----
        zone(Lang.s(act, "zone_remember"));
        row(R.drawable.ic_check_plain, Lang.s(act, "always_allow"), new Rows.OnClick() {
            @Override
            public void onClick() {
                dismiss();
                if (cb != null) {
                    cb.onAlwaysAllow();
                }
            }
        });
        row(R.drawable.ic_block, Lang.s(act, "always_block"), new Rows.OnClick() {
            @Override
            public void onClick() {
                dismiss();
                if (cb != null) {
                    cb.onAlwaysBlock();
                }
            }
        });
        dangerRow(R.drawable.ic_phone_block,
                Lang.f(act, "block_app", appLabel == null ? "" : appLabel),
                new Rows.OnClick() {
                    @Override
                    public void onClick() {
                        dismiss();
                        if (cb != null) {
                            cb.onBlockApp();
                        }
                    }
                });

        // 收起档到这里为止
        final View mark = new View(act);
        mark.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1));
        content.addView(mark);

        // ---- 这个链接（不改变任何规则的一次性动作）----
        zone(Lang.s(act, "zone_this"));
        row(R.drawable.ic_copy, Lang.s(act, "copy_link"), new Rows.OnClick() {
            @Override
            public void onClick() {
                dismiss();
                if (cb != null) {
                    cb.onCopyLink();
                }
            }
        });

        // ---- 隐私 ----
        zone(Lang.s(act, "zone_privacy"));
        Rows.addSwitch(content, Lang.s(act, "hide_url"), Lang.s(act, "hide_url_sub"),
                Prefs.hideUrl(act), 0, new android.widget.CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(android.widget.CompoundButton b, boolean on) {
                        Prefs.setHideUrl(act, on);
                        if (cb != null) {
                            cb.onPrivacyChanged();
                        }
                    }
                });
        Rows.addSwitch(content, Lang.s(act, "strip_params"), Lang.s(act, "strip_params_sub"),
                Prefs.stripParams(act), 0, new android.widget.CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(android.widget.CompoundButton b, boolean on) {
                        Prefs.setStripParams(act, on);
                        if (cb != null) {
                            cb.onPrivacyChanged();
                        }
                    }
                });
        Rows.addSwitch(content, Lang.s(act, "no_preview_remote"),
                Lang.s(act, "no_preview_remote_sub"),
                Prefs.noRemoteRes(act), 0, new android.widget.CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(android.widget.CompoundButton b, boolean on) {
                        Prefs.setNoRemoteRes(act, on);
                        if (cb != null) {
                            cb.onPrivacyChanged();
                        }
                    }
                });
        Rows.addSwitch(content, Lang.s(act, "clear_on_exit"), Lang.s(act, "clear_on_exit_sub"),
                Prefs.clearOnExit(act), 0, new android.widget.CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(android.widget.CompoundButton b, boolean on) {
                        Prefs.setClearOnExit(act, on);
                        if (cb != null) {
                            cb.onPrivacyChanged();
                        }
                    }
                });
        Rows.addSwitch(content, Lang.s(act, "incognito"), Lang.s(act, "incognito_sub"),
                Prefs.incognito(act), 0, new android.widget.CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(android.widget.CompoundButton b, boolean on) {
                        Prefs.setIncognito(act, on);
                        if (cb != null) {
                            cb.onPrivacyChanged();
                        }
                    }
                });

        // ---- 预览 ----
        zone(Lang.s(act, "zone_preview"));
        Rows.addSwitch(content, Lang.s(act, "web_preview"), null, Prefs.webPreview(act),
                R.drawable.ic_page,
                new android.widget.CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(android.widget.CompoundButton b, boolean on) {
                        Prefs.setWebPreview(act, on);
                        if (cb != null) {
                            cb.onPreviewToggle(on);
                        }
                    }
                });
        Rows.add(content, Lang.s(act, "preview_zoom"), null, Prefs.zoomLabel(act),
                R.drawable.ic_zoom, new Rows.OnClick() {
                    @Override
                    public void onClick() {
                        dismiss();
                        if (cb != null) {
                            cb.onZoom();
                        }
                    }
                });

        // ---- 打开主页面 ----
        content.addView(dividerFull());
        Rows.add(content, Lang.s(act, "open_home"), null, null, R.drawable.ic_home,
                new Rows.OnClick() {
                    @Override
                    public void onClick() {
                        dismiss();
                        if (cb != null) {
                            cb.onOpenHome();
                        }
                    }
                });

        panel.post(new Runnable() {
            @Override
            public void run() {
                expandedH = content.getHeight() + dp(28) + dp(16);
                collapsedH = mark.getTop() + dp(28) + dp(16);
                int screen = act.getResources().getDisplayMetrics().heightPixels;
                int cap = (int) (screen * 0.92f);
                if (expandedH > cap) {
                    expandedH = cap;
                }
                setHeight(collapsedH);
                attachDrag();
            }
        });
        return this;
    }

    private View dividerFull() {
        View v = new View(act);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1)));
        lp.topMargin = dp(8);
        lp.bottomMargin = dp(8);
        lp.leftMargin = dp(24);
        lp.rightMargin = dp(24);
        v.setLayoutParams(lp);
        v.setBackgroundColor(Themes.outlineVariant(act));
        return v;
    }

    // ------------------------------------------------------------------
    // 拖拽
    // ------------------------------------------------------------------

    private void attachDrag() {
        View drag = dialog.findViewById(R.id.sheetDragZone);
        drag.setOnTouchListener(new View.OnTouchListener() {
            private float startRaw;
            private int startH;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        startRaw = e.getRawY();
                        startH = panel.getHeight();
                        return true;
                    case MotionEvent.ACTION_MOVE: {
                        int dy = (int) (startRaw - e.getRawY());
                        int h = startH + dy;
                        int lo = Math.min(collapsedH, expandedH);
                        int hi = Math.max(collapsedH, expandedH);
                        setHeight(Math.max(lo, Math.min(hi, h)));
                        return true;
                    }
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL: {
                        int mid = (collapsedH + expandedH) / 2;
                        boolean expand = panel.getHeight() > mid;
                        setHeight(expand ? expandedH : collapsedH);
                        // 收起时不让内容滚动，免得和拖拽抢手势
                        scroll.setVerticalScrollBarEnabled(expand);
                        return true;
                    }
                    default:
                        return false;
                }
            }
        });
    }

    private void setHeight(int h) {
        ViewGroup.LayoutParams lp = panel.getLayoutParams();
        lp.height = h;
        panel.setLayoutParams(lp);
    }

    private int dp(int v) {
        return (int) (v * act.getResources().getDisplayMetrics().density + 0.5f);
    }

    public void dismiss() {
        dialog.dismiss();
    }

    public MoreSheet show() {
        dialog.show();
        return this;
    }
}
