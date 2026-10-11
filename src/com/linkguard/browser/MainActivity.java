package com.linkguard.browser;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.role.RoleManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/** 桌面入口：设置界面。名单都拆到独立页面里了。 */
public class MainActivity extends Activity {

    private static final int REQ_ROLE = 1001;

    private TextView tvStatus;
    private TextView tvDefaultBrowser;
    private TextView tvScanResult;
    private TextView tvTheme;
    private Button btnSetDefault;
    private Button btnBlacklist;
    private Button btnWhitelist;
    private Button btnAppBlock;
    private Button btnLogs;
    private CheckBox cbShowDialog;
    private CheckBox cbToast;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Themes.apply(this);
        super.onCreate(savedInstanceState);
        Lists.migrate(this);
        setContentView(R.layout.activity_main);

        tvStatus = findViewById(R.id.tvStatus);
        tvDefaultBrowser = findViewById(R.id.tvDefaultBrowser);
        tvScanResult = findViewById(R.id.tvScanResult);
        tvTheme = findViewById(R.id.tvTheme);
        btnSetDefault = findViewById(R.id.btnSetDefault);
        btnBlacklist = findViewById(R.id.btnBlacklist);
        btnWhitelist = findViewById(R.id.btnWhitelist);
        btnAppBlock = findViewById(R.id.btnAppBlock);
        btnLogs = findViewById(R.id.btnLogs);
        cbShowDialog = findViewById(R.id.cbShowDialog);
        cbToast = findViewById(R.id.cbToast);

        findViewById(R.id.btnPickTheme).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showPickThemeDialog();
            }
        });

        btnSetDefault.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestDefaultBrowser();
            }
        });

        findViewById(R.id.btnAppSettings).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openAppSettings();
            }
        });

        findViewById(R.id.btnPickDefault).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showPickDefaultDialog();
            }
        });

        findViewById(R.id.btnScanBrowsers).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                scanBrowsers();
            }
        });

        btnBlacklist.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                open(BlacklistActivity.class);
            }
        });
        btnWhitelist.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                open(WhitelistActivity.class);
            }
        });
        btnAppBlock.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                open(AppBlockActivity.class);
            }
        });
        btnLogs.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                open(LogActivity.class);
            }
        });

        cbShowDialog.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Prefs.setShowDialog(MainActivity.this, cbShowDialog.isChecked());
            }
        });

        cbToast.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Prefs.setToastOnBlock(MainActivity.this, cbToast.isChecked());
            }
        });
    }

    @Override
    protected void onResume() {
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

    private void open(Class<?> cls) {
        try {
            startActivity(new Intent(this, cls));
        } catch (Exception ignored) {
        }
    }

    // ------------------------------------------------------------------
    // 刷新
    // ------------------------------------------------------------------

    private void refresh() {
        boolean isDefault = BrowserCatalog.isUsDefault(this);
        if (isDefault) {
            tvStatus.setText(R.string.status_default_yes);
            tvStatus.setTextColor(Themes.primary(this));
            btnSetDefault.setEnabled(false);
            btnSetDefault.setText(R.string.status_default_yes_btn);
        } else {
            tvStatus.setText(R.string.status_default_no);
            tvStatus.setTextColor(Themes.danger(this));
            btnSetDefault.setEnabled(true);
            btnSetDefault.setText(R.string.btn_set_default);
        }

        tvTheme.setText(getString(R.string.theme_current,
                getString(Themes.nameRes(Themes.current(this)))));

        String label = Prefs.defaultLabel(this);
        if (TextUtils.isEmpty(label) || !Prefs.hasDefault(this)) {
            tvDefaultBrowser.setText(R.string.default_none);
        } else {
            tvDefaultBrowser.setText(getString(R.string.default_is, label));
        }

        cbShowDialog.setChecked(Prefs.showDialog(this));
        cbToast.setChecked(Prefs.toastOnBlock(this));

        btnBlacklist.setText(getString(R.string.entry_fmt,
                getString(R.string.entry_blacklist), Lists.blackCount(this)));
        btnWhitelist.setText(getString(R.string.entry_fmt,
                getString(R.string.entry_whitelist), Lists.whiteCount(this)));
        btnAppBlock.setText(getString(R.string.entry_fmt,
                getString(R.string.entry_appblock), Lists.appBlockCount(this)));
        btnLogs.setText(getString(R.string.entry_fmt,
                getString(R.string.entry_logs), LogStore.all(this).length()));

        scanBrowsers();
    }

    /** 扫描可用浏览器，并把「系统只让看到几个应用」这类信息直接显示出来。 */
    private void scanBrowsers() {
        BrowserCatalog.Diag diag = new BrowserCatalog.Diag();
        List<Target> list = BrowserCatalog.browsers(this, diag);

        StringBuilder sb = new StringBuilder();
        if (list.isEmpty()) {
            sb.append(getString(R.string.scan_empty, diag.installedVisible));
            tvScanResult.setTextColor(Themes.danger(this));
        } else {
            sb.append(getString(R.string.scan_ok, list.size()));
            for (int i = 0; i < list.size() && i < 6; i++) {
                sb.append(i == 0 ? "：" : "、").append(list.get(i).label);
            }
            if (list.size() > 6) {
                sb.append(" 等");
            }
            tvScanResult.setTextColor(Themes.primary(this));
        }
        sb.append("\n").append(getString(R.string.scan_perm,
                getString(diag.queryAllGranted ? R.string.perm_yes : R.string.perm_no)));
        tvScanResult.setText(sb.toString());
    }

    // ------------------------------------------------------------------
    // 交互
    // ------------------------------------------------------------------

    private void requestDefaultBrowser() {
        if (BrowserCatalog.isUsDefault(this)) {
            toast(getString(R.string.toast_is_default));
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
        toast(getString(R.string.toast_not_support_role));
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS));
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            } catch (Exception ignored) {
            }
        }
    }

    private void openAppSettings() {
        try {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            i.setData(Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Exception e) {
            toast(getString(R.string.toast_not_support_role));
        }
    }

    private void showPickDefaultDialog() {
        Dialogs.pickBrowser(this, getString(R.string.pick_default_title),
                R.string.no_default_browser,
                new Dialogs.OnPick() {
                    @Override
                    public void onPick(Target target, boolean useDefault) {
                        // 只改「默认打开方式」，不去动「被拉起时先弹窗询问」那个开关 ——
                        // 两者是独立的：默认浏览器决定“用哪个打开”，开关决定“要不要问”。
                        // （以前这里会顺手把开关关掉，导致设完默认浏览器后白名单直接失效。）
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
        String[] labels = new String[keys.length];
        for (int i = 0; i < keys.length; i++) {
            labels[i] = getString(Themes.nameRes(keys[i]));
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.pick_theme_title)
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        Themes.set(MainActivity.this, keys[which]);
                        recreate();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void toast(String msg) {
        try {
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        } catch (Exception ignored) {
        }
    }
}
