package com.linkguard.browser;

import android.app.Activity;

import android.app.role.RoleManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.widget.ScrollView;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/**
 * 桌面入口（设置页）。
 *
 * <p>一屏说清三件事：顶部状态卡回答「现在生效了吗、要做什么」，
 * 下面三组列表行分别通向网址规则、行为开关和外观。
 * 除了状态卡上的主操作，其余入口一律是 M3 List 的行，不再堆满全宽按钮。
 */
public class MainActivity extends Activity {

    private static final int REQ_ROLE = 1001;

    private ScrollView svMain;
    private LinearLayout cardStatus;
    private TextView tvStatusTitle;
    private TextView tvStatusDesc;
    private Button btnSetDefault;
    private Button btnAppSettings;

    private View rowDefault;
    private View rowBlack;
    private View rowWhite;
    private View rowAppBlock;
    private View rowLogs;
    private View rowTheme;
    private View rowLang;
    private View rowZoom;
    private Switch swWebPreview;

    private Switch swShowDialog;
    private Switch swToast;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Themes.apply(this);
        super.onCreate(savedInstanceState);
        Lists.migrate(this);
        setContentView(R.layout.activity_main);

        svMain = findViewById(R.id.svMain);
        cardStatus = findViewById(R.id.cardStatus);
        tvStatusTitle = findViewById(R.id.tvStatusTitle);
        tvStatusDesc = findViewById(R.id.tvStatusDesc);
        btnSetDefault = findViewById(R.id.btnSetDefault);
        btnAppSettings = findViewById(R.id.btnAppSettings);

        // 这几个原来写死在布局里，走 @string 不受运行时切语言影响，改由代码填
        ((TextView) findViewById(R.id.tvSlogan)).setText(Lang.s(this, "app_slogan"));
        ((TextView) findViewById(R.id.tvGroupRules)).setText(Lang.s(this, "group_rules"));
        ((TextView) findViewById(R.id.tvGroupBehavior)).setText(Lang.s(this, "group_behavior"));
        ((TextView) findViewById(R.id.tvGroupLook)).setText(Lang.s(this, "group_look"));
        ((TextView) findViewById(R.id.tvGroupDialog)).setText(Lang.s(this, "group_dialog"));

        btnSetDefault.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestDefaultBrowser();
            }
        });
        btnAppSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openAppSettings();
            }
        });

        buildRows();

        // 换配色会 recreate()，把原来滚到哪恢复回来（等布局完成再滚才不会被夹成 0）
        if (savedInstanceState != null) {
            final int y = savedInstanceState.getInt("scrollY", 0);
            if (y > 0) {
                svMain.post(new Runnable() {
                    @Override
                    public void run() {
                        svMain.scrollTo(0, y);
                    }
                });
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (svMain != null) {
            outState.putInt("scrollY", svMain.getScrollY());
        }
    }

    /** 取两个颜色里更深的一个（按感知亮度），用于配色预览的圆点。 */
    private static int darker(int a, int b) {
        return lum(a) <= lum(b) ? a : b;
    }

    private static int lum(int c) {
        return (299 * ((c >> 16) & 0xFF) + 587 * ((c >> 8) & 0xFF) + 114 * (c & 0xFF)) / 1000;
    }

    @Override
    protected void onResume() {
        // 回到应用时重新扫一遍浏览器列表：装/卸了浏览器能立刻反映出来，
        // 而同一次停留里的多次调用（主界面 + 选择器）仍走缓存。
        BrowserCatalog.invalidate();
        super.onResume();
        refresh();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_ROLE) {
            refresh();
        }
    }

    // ------------------------------------------------------------------
    // 列表行
    // ------------------------------------------------------------------

    private void buildRows() {
        LinearLayout rules = findViewById(R.id.listRules);

        rowDefault = Rows.add(rules, Lang.s(this, "row_default_browser"), null, null,
                new Rows.OnClick() {
                    @Override
                    public void onClick() {
                        showPickDefaultDialog();
                    }
                });
        Rows.divider(rules);

        rowBlack = Rows.add(rules, Lang.s(this, "entry_blacklist"), null, null,
                new Rows.OnClick() {
                    @Override
                    public void onClick() {
                        open(BlacklistActivity.class);
                    }
                });
        Rows.divider(rules);

        rowWhite = Rows.add(rules, Lang.s(this, "entry_whitelist"), null, null,
                new Rows.OnClick() {
                    @Override
                    public void onClick() {
                        open(WhitelistActivity.class);
                    }
                });
        Rows.divider(rules);

        rowAppBlock = Rows.add(rules, Lang.s(this, "entry_appblock"), null, null,
                new Rows.OnClick() {
                    @Override
                    public void onClick() {
                        open(AppBlockActivity.class);
                    }
                });
        Rows.divider(rules);

        rowLogs = Rows.add(rules, Lang.s(this, "entry_logs"), null, null,
                new Rows.OnClick() {
                    @Override
                    public void onClick() {
                        open(LogActivity.class);
                    }
                });

        LinearLayout behavior = findViewById(R.id.listBehavior);
        swShowDialog = Rows.addSwitch(behavior,
                Lang.s(this, "row_show_dialog"),
                Lang.s(this, "row_show_dialog_sub"),
                Prefs.showDialog(this),
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton b, boolean checked) {
                        Prefs.setShowDialog(MainActivity.this, checked);
                    }
                });
        Rows.divider(behavior);

        swToast = Rows.addSwitch(behavior,
                Lang.s(this, "row_toast"),
                Lang.s(this, "row_toast_sub"),
                Prefs.toastOnBlock(this),
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton b, boolean checked) {
                        Prefs.setToastOnBlock(MainActivity.this, checked);
                    }
                });

        LinearLayout dlg = findViewById(R.id.listDialog);
        swWebPreview = Rows.addSwitch(dlg, Lang.s(this, "web_preview"), null,
                Prefs.webPreview(this), R.drawable.ic_page,
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton b, boolean on) {
                        Prefs.setWebPreview(MainActivity.this, on);
                    }
                });
        Rows.divider(dlg);
        rowZoom = Rows.add(dlg, Lang.s(this, "preview_zoom"), null, Prefs.zoomLabel(this),
                R.drawable.ic_zoom, new Rows.OnClick() {
                    @Override
                    public void onClick() {
                        showPickZoomDialog();
                    }
                });
        Rows.divider(dlg);
        Rows.add(dlg, Lang.s(this, "row_test_dialog"), Lang.s(this, "row_test_dialog_sub"),
                null, R.drawable.ic_browser, new Rows.OnClick() {
                    @Override
                    public void onClick() {
                        testDialog();
                    }
                });

        LinearLayout look = findViewById(R.id.listLook);
        rowTheme = Rows.add(look, Lang.s(this, "row_theme"), null, null,
                new Rows.OnClick() {
                    @Override
                    public void onClick() {
                        showPickThemeDialog();
                    }
                });
        Rows.divider(look);

        rowLang = Rows.add(look, Lang.s(this, "row_language"), null, null, new Rows.OnClick() {
            @Override
            public void onClick() {
                showPickLanguageDialog();
            }
        });
        Rows.divider(look);

        Rows.add(look, Lang.s(this, "row_about"), null, null, new Rows.OnClick() {
            @Override
            public void onClick() {
                showAboutDialog();
            }
        });
    }

    // ------------------------------------------------------------------
    // 刷新
    // ------------------------------------------------------------------

    private void refresh() {
        boolean active = BrowserCatalog.isUsDefault(this);

        ImageView icon = findViewById(R.id.ivStatusIcon);
        if (active) {
            cardStatus.setBackgroundResource(R.drawable.md_card);
            tvStatusTitle.setTextColor(Themes.primary(this));
            tvStatusDesc.setTextColor(Themes.onSurfaceVariant(this));
            tvStatusTitle.setText(Lang.s(this, "status_ok_title"));
            tvStatusDesc.setText(Lang.s(this, "status_ok_desc"));
            icon.setImageResource(R.drawable.ic_check_circle);
            icon.setColorFilter(Themes.primary(this));
            btnSetDefault.setVisibility(View.GONE);
            btnAppSettings.setVisibility(View.GONE);
        } else {
            cardStatus.setBackgroundResource(R.drawable.md_card_accent);
            tvStatusTitle.setTextColor(Themes.onPrimaryContainer(this));
            tvStatusDesc.setTextColor(Themes.onPrimaryContainer(this));
            tvStatusTitle.setText(Lang.s(this, "status_no_title"));
            tvStatusDesc.setText(Lang.s(this, "status_no_desc"));
            icon.setImageResource(R.drawable.ic_info);
            icon.setColorFilter(Themes.onPrimaryContainer(this));
            btnSetDefault.setText(Lang.s(this, "status_btn_set"));
            btnSetDefault.setEnabled(true);
            btnSetDefault.setVisibility(View.VISIBLE);
            btnAppSettings.setText(Lang.s(this, "status_btn_settings"));
            btnAppSettings.setVisibility(View.VISIBLE);
        }

        String label = Prefs.defaultLabel(this);
        if (!Prefs.hasDefault(this) || TextUtils.isEmpty(label)) {
            label = Lang.s(this, "val_none");
        }
        setValue(rowDefault, label);
        setValue(rowBlack, Lang.f(this, "val_count", Lists.blackCount(this)));
        setValue(rowWhite, Lang.f(this, "val_count", Lists.whiteCount(this)));
        setValue(rowAppBlock, Lang.f(this, "val_count", Lists.appBlockCount(this)));
        setValue(rowLogs, Lang.f(this, "val_count_logs", LogStore.all(this).length()));
        setValue(rowTheme, Themes.name(this, Themes.current(this)));
        setValue(rowLang, langLabel());
        setValue(rowZoom, Prefs.zoomLabel(this));
        swWebPreview.setChecked(Prefs.webPreview(this));

        // 扫描结果放「默认打开方式」的副标题：这是它真正相关的上下文
        BrowserCatalog.Diag diag = new BrowserCatalog.Diag();
        List<Target> browsers = BrowserCatalog.browsers(this, diag);
        TextView sub = rowDefault.findViewById(R.id.rowSub);
        if (browsers.isEmpty()) {
            sub.setTextColor(Themes.danger(this));
            setSubtitle(rowDefault, Lang.s(this, "val_scan_none"));
        } else {
            sub.setTextColor(Themes.onSurfaceVariant(this));
            // 只报数量：具体是哪几个浏览器，点这一行就能看到完整列表
            setSubtitle(rowDefault, Lang.f(this, "val_scan_ok", browsers.size()));
        }
        swShowDialog.setChecked(Prefs.showDialog(this));
        swToast.setChecked(Prefs.toastOnBlock(this));
    }

    private void setValue(View row, String text) {
        TextView tv = row.findViewById(R.id.rowValue);
        if (TextUtils.isEmpty(text)) {
            tv.setVisibility(View.GONE);
        } else {
            tv.setVisibility(View.VISIBLE);
            tv.setText(text);
        }
    }

    private void setSubtitle(View row, String text) {
        TextView tv = row.findViewById(R.id.rowSub);
        if (TextUtils.isEmpty(text)) {
            tv.setVisibility(View.GONE);
        } else {
            tv.setVisibility(View.VISIBLE);
            tv.setText(text);
        }
    }

    // ------------------------------------------------------------------
    // 交互
    // ------------------------------------------------------------------

    private void open(Class<?> cls) {
        try {
            startActivity(new Intent(this, cls));
        } catch (Exception ignored) {
        }
    }

    private void requestDefaultBrowser() {
        if (BrowserCatalog.isUsDefault(this)) {
            toast(Lang.s(this, "toast_is_default"));
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                RoleManager rm = getSystemService(RoleManager.class);
                if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_BROWSER)
                        && !rm.isRoleHeld(RoleManager.ROLE_BROWSER)) {
                    startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_BROWSER), REQ_ROLE);
                    return;
                }
            } catch (Exception ignored) {
            }
        }
        toast(Lang.s(this, "toast_not_support_role"));
        openDefaultAppsSettings();
    }

    /**
     * 打开「默认应用」设置。
     *
     * <p>ACTION_MANAGE_DEFAULT_APPS_SETTINGS 是 API 24 才有的常量 —— 直接引用它，
     * 在 API 21~23 上会抛 NoSuchFieldError（Error 而非 Exception，catch 不住），
     * 所以必须先用版本判断把这条路径挡掉。
     */
    private void openDefaultAppsSettings() {
        if (Build.VERSION.SDK_INT >= 24) {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS));
                return;
            } catch (Exception ignored) {
            }
        }
        // API 21~23：没有「默认应用」这个总入口，只能退到应用设置页
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_SETTINGS));
            return;
        } catch (Exception ignored) {
        }
        try {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        } catch (Exception ignored) {
        }
    }

    private void openAppSettings() {
        try {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            i.setData(Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Exception e) {
            openDefaultAppsSettings();
        }
    }

    private void showPickDefaultDialog() {
        Dialogs.pickBrowser(this, Lang.s(this, "pick_default_title"),
                Lang.s(this, "no_default_browser"),
                new Dialogs.OnPick() {
                    @Override
                    public void onPick(Target target, boolean useDefault) {
                        // 只改「默认打开方式」，不去动「被拉起时先弹窗询问」那个开关 ——
                        // 两者是独立的：默认浏览器决定“用哪个打开”，开关决定“要不要问”。
                        if (useDefault) {
                            Prefs.clearDefault(MainActivity.this);
                        } else {
                            Prefs.setDefault(MainActivity.this, target.pkg, target.act, target.label);
                        }
                        refresh();
                    }
                });
    }

    private void showPickThemeDialog() {
        final String[] keys = Themes.KEYS;
        final String current = Themes.current(this);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        LayoutInflater inf = LayoutInflater.from(this);

        for (int i = 0; i < keys.length; i++) {
            final String key = keys[i];
            View row = inf.inflate(R.layout.item_theme, box, false);
            ((TextView) row.findViewById(R.id.themeName)).setText(Themes.name(this, key));

            // 单个圆点，取该方案 primary / primaryContainer 里更深的那一个：
            // 深色方案的主色本身就是浅蓝，只取主色会让人误以为是浅色主题。
            GradientDrawable dot = new GradientDrawable();
            dot.setShape(GradientDrawable.OVAL);
            dot.setColor(darker(Themes.colorOf(this, key, R.attr.lgPrimary),
                    Themes.colorOf(this, key, R.attr.lgPrimaryContainer)));
            row.findViewById(R.id.themeSwatch).setBackground(dot);

            if (key.equals(current)) {
                row.findViewById(R.id.themeCheck).setVisibility(View.VISIBLE);
                ((TextView) row.findViewById(R.id.themeUsing)).setText(Lang.s(this, "theme_in_use"));
                row.findViewById(R.id.themeUsing).setVisibility(View.VISIBLE);
            }

            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Themes.set(MainActivity.this, key);
                    recreate();
                }
            });
            box.addView(row);
        }

        // 滚动交给 MdDialog 内部的 MdScrollView，这里不再套一层
        MdDialog.create(this)
                .title(Lang.s(this, "pick_theme_title"))
                .content(box)
                .negative(Lang.s(this, "cancel"), null)
                .show();
    }

    /** 当前语言的显示名；跟随系统时标出来。 */
    private String langLabel() {
        String code = Prefs.lang(this);
        String sys = Lang.selfName(Lang.indexOf(Lang.systemDefault(this)));
        if (code == null || code.length() == 0) {
            return Lang.s(this, "lang_system") + "（" + sys + "）";
        }
        return Lang.selfName(Lang.indexOf(code));
    }

    /** 缩放：直接输数字。 */
    private void showPickZoomDialog() {
        MdDialog.create(this)
                .title(Lang.s(this, "zoom_title"))
                .message(Lang.f(this, "preview_zoom_hint", Prefs.ZOOM_MIN, Prefs.ZOOM_MAX))
                .content(Dialogs.zoomField(this, Prefs.zoom(this)))
                .negative(Lang.s(this, "cancel"), null)
                .positive(Lang.s(this, "ok"), new MdDialog.OnClick() {
                    @Override
                    public void onClick() {
                        int v = Dialogs.zoomValue(MainActivity.this, Prefs.zoom(MainActivity.this));
                        if (v < Prefs.ZOOM_MIN || v > Prefs.ZOOM_MAX) {
                            toast(Lang.s(MainActivity.this, "zoom_invalid"));
                            return;
                        }
                        Prefs.setZoom(MainActivity.this, v);
                        refresh();
                    }
                })
                .show();
    }

    /** 用一个示例链接把拦截弹窗调起来，看看实际长什么样。 */
    private void testDialog() {
        try {
            Intent i = new Intent(this, InterceptActivity.class);
            i.setAction(Intent.ACTION_VIEW);
            i.setData(Uri.parse("https://example.com/linkguard-test"));
            startActivity(i);
        } catch (Exception e) {
            toast(Lang.s(this, "toast_launch_failed"));
        }
    }
    private void showPickLanguageDialog() {
        final String[] codes = Lang.CODES;
        CharSequence[] labels = new CharSequence[codes.length + 1];
        labels[0] = Lang.s(this, "lang_system");
        for (int i = 0; i < codes.length; i++) {
            labels[i + 1] = Lang.selfName(i);
        }
        MdDialog.create(this)
                .title(Lang.s(this, "pick_language_title"))
                .items(labels, new MdDialog.OnItem() {
                    @Override
                    public void onItem(int which) {
                        Prefs.setLang(MainActivity.this, which == 0 ? "" : codes[which - 1]);
                        Lang.invalidate();
                        recreate();
                    }
                })
                .negative(Lang.s(this, "cancel"), null)
                .show();
    }
    /** 从系统里读真实版本号，免得手写跟构建对不上。 */
    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "";
        }
    }
    private void showAboutDialog() {
        MdDialog.create(this)
                .title(Lang.s(this, "row_about"))
                .message(Lang.f(this, "about_text", versionName()))
                .positive(Lang.s(this, "ok"), null)
                .show();
    }

    private void toast(String msg) {
        try {
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        } catch (Exception ignored) {
        }
    }
}
