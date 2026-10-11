package com.linkguard.browser;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;

/**
 * 被其它应用拉起时真正执行的 Activity。
 *
 * <p>处理顺序：
 * <ol>
 *   <li>来源应用在「应用屏蔽」名单里 → 拦下</li>
 *   <li>网址在「黑名单」里 → 拦下</li>
 *   <li>网址在「白名单」里 → 有单独指定浏览器就用它，否则跟随默认浏览器</li>
 *   <li>关闭了「弹窗询问」→ 用默认浏览器直接打开</li>
 *   <li>其余 → 弹窗询问</li>
 * </ol>
 */
public class InterceptActivity extends Activity {

    private static final int MODE_CHOOSE = 0;
    private static final int MODE_ONCE = 1;
    /** 已经加进白名单、但还没设默认浏览器：本次先让用户选一个。 */
    private static final int MODE_NO_DEFAULT = 2;

    private static final int PLAN_DIALOG = 0;
    private static final int PLAN_BLOCK = 1;
    private static final int PLAN_OPEN = 2;
    private static final int PLAN_APP_BLOCK = 3;

    private static final class Plan {
        int kind = PLAN_DIALOG;
        Uri uri;
        String root = "";
        String url = "";
        Target target;
        String srcPkg;
        String srcLabel;
    }

    private final Handler handler = new Handler(Looper.getMainLooper());

    private Plan plan;
    private int mode = MODE_CHOOSE;

    private LinearLayout llActions;
    private LinearLayout llPicker;
    private LinearLayout llBrowsers;
    private ScrollView svBrowsers;
    private TextView tvTitle;
    private TextView tvRoot;
    private TextView tvUrl;
    private TextView tvSource;
    private TextView tvDefault;
    private TextView tvPickHint;
    private Button btnOnce;
    private Button btnAlways;
    private Button btnBlock;
    private Button btnAppBlock;
    private Button btnBack;

    // ------------------------------------------------------------------
    // 生命周期
    // ------------------------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Themes.applyIntercept(this);
        Plan p = null;
        try {
            Lists.migrate(this);
            p = decide(getIntent());
        } catch (Exception ignored) {
        }

        if (p == null) {
            super.onCreate(savedInstanceState);
            finish();
            return;
        }

        plan = p;
        super.onCreate(savedInstanceState);
        execute(p);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);

        Plan p = null;
        try {
            p = decide(intent);
        } catch (Exception ignored) {
        }
        if (p == null) {
            finish();
            return;
        }
        plan = p;
        execute(p);
    }

    @Override
    public void onBackPressed() {
        if (plan != null && plan.kind == PLAN_DIALOG && mode != MODE_CHOOSE) {
            render(MODE_CHOOSE);
            return;
        }
        finish();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    // ------------------------------------------------------------------
    // 决策
    // ------------------------------------------------------------------

    private Plan decide(Intent intent) {
        if (intent == null || !Intent.ACTION_VIEW.equals(intent.getAction())) {
            return null;
        }
        Uri data = intent.getData();
        if (data == null) {
            return null;
        }
        String scheme = data.getScheme() == null ? "" : data.getScheme().toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            return null;
        }
        String root = HostUtil.rootDomain(data.getHost());
        if (TextUtils.isEmpty(root)) {
            root = data.getHost() == null ? "" : data.getHost();
        }
        if (TextUtils.isEmpty(root)) {
            return null;
        }

        Plan p = new Plan();
        p.uri = data;
        p.root = root;
        p.url = data.toString();
        p.srcPkg = sourcePackage();
        p.srcLabel = labelOf(p.srcPkg);

        // 1. 来源应用被屏蔽
        if (p.srcPkg != null && Lists.isAppBlocked(this, p.srcPkg)) {
            p.kind = PLAN_APP_BLOCK;
            return p;
        }

        // 2. 黑名单
        if (Lists.isBlack(this, root)) {
            p.kind = PLAN_BLOCK;
            return p;
        }

        // 3. 白名单：用指定浏览器直接打开
        if (Lists.isWhite(this, root)) {
            Target t = Lists.whiteTarget(this, root);
            if (t == null) {
                t = defaultTarget();
            }
            if (t != null && t.valid()) {
                p.kind = PLAN_OPEN;
                p.target = t;
                return p;
            }
        }

        // 4. 关闭了弹窗询问 → 直接用默认浏览器
        if (!Prefs.showDialog(this)) {
            Target t = defaultTarget();
            if (t != null && t.valid()) {
                p.kind = PLAN_OPEN;
                p.target = t;
                return p;
            }
        }

        // 5. 弹窗询问
        p.kind = PLAN_DIALOG;
        return p;
    }

    private Target defaultTarget() {
        if (!Prefs.hasDefault(this)) {
            return null;
        }
        return new Target(Prefs.defaultLabel(this), Prefs.defaultPkg(this),
                Prefs.defaultAct(this), true);
    }

    private void execute(Plan p) {
        if (p.kind == PLAN_APP_BLOCK) {
            LogStore.add(this, p.root, p.url, LogStore.ACTION_APP_BLOCKED, p.srcLabel);
            if (Prefs.toastOnBlock(this)) {
                toast(getString(R.string.toast_app_blocked, p.srcLabel));
                finishDelayed(320);
                return;
            }
            finish();
            return;
        }

        if (p.kind == PLAN_BLOCK) {
            LogStore.add(this, p.root, p.url, LogStore.ACTION_BLOCKED, "");
            if (Prefs.toastOnBlock(this)) {
                toast(getString(R.string.toast_blocked, p.root));
                finishDelayed(320);
                return;
            }
            finish();
            return;
        }

        if (p.kind == PLAN_OPEN) {
            if (p.target != null && LaunchHelper.launch(this, p.uri, p.target)) {
                LogStore.add(this, p.root, p.url, LogStore.ACTION_OPENED, p.target.label);
                finish();
                return;
            }
            // 指定的浏览器打不开，退化成询问
            String label = p.target == null ? "" : p.target.label;
            toast(getString(R.string.toast_launch_failed, label));
            p.kind = PLAN_DIALOG;
        }

        render(MODE_CHOOSE);
    }

    // ------------------------------------------------------------------
    // 弹窗
    // ------------------------------------------------------------------

    private void render(int newMode) {
        mode = newMode;
        setContentView(R.layout.dialog_intercept);

        tvTitle = findViewById(R.id.tvTitle);
        tvRoot = findViewById(R.id.tvRoot);
        tvUrl = findViewById(R.id.tvUrl);
        tvSource = findViewById(R.id.tvSource);
        tvDefault = findViewById(R.id.tvDefault);
        tvPickHint = findViewById(R.id.tvPickHint);
        llActions = findViewById(R.id.llActions);
        llPicker = findViewById(R.id.llPicker);
        llBrowsers = findViewById(R.id.llBrowsers);
        svBrowsers = findViewById(R.id.svBrowsers);
        btnOnce = findViewById(R.id.btnOnce);
        btnAlways = findViewById(R.id.btnAlways);
        btnBlock = findViewById(R.id.btnBlock);
        btnAppBlock = findViewById(R.id.btnAppBlock);
        btnBack = findViewById(R.id.btnBack);

        tvTitle.setText(R.string.ask_title);
        tvRoot.setText(plan.root);
        tvUrl.setText(plan.url);

        if (plan.srcLabel != null) {
            tvSource.setText(getString(R.string.source_app, plan.srcLabel));
            tvSource.setVisibility(View.VISIBLE);
        } else {
            tvSource.setVisibility(View.GONE);
        }

        if (mode == MODE_CHOOSE) {
            llActions.setVisibility(View.VISIBLE);
            llPicker.setVisibility(View.GONE);

            // 把「会用哪个浏览器打开」直接写在弹窗里，免得用户猜
            Target shown = defaultTarget();
            if (shown != null && shown.valid()) {
                tvDefault.setText(getString(R.string.will_open_with, shown.label));
            } else {
                tvDefault.setText(R.string.will_open_ask);
            }
            tvDefault.setVisibility(View.VISIBLE);

            btnOnce.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // “仅这一次”= 用默认浏览器打开，什么都不记住
                    Target def = defaultTarget();
                    if (def != null && def.valid()) {
                        if (LaunchHelper.launch(InterceptActivity.this, plan.uri, def)) {
                            LogStore.add(InterceptActivity.this, plan.root, plan.url,
                                    LogStore.ACTION_ONCE, def.label);
                            finish();
                            return;
                        }
                        toast(getString(R.string.toast_launch_failed, def.label));
                    }
                    // 还没设默认浏览器 → 选一个只用于本次
                    render(MODE_ONCE);
                }
            });
            btnAlways.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // “始终”= 加入白名单，之后跟随设置里的默认浏览器，不再询问。
                    // 想给某个网址单独指定浏览器，到白名单页里设置（那是特例）。
                    Lists.removeBlack(InterceptActivity.this, plan.root);
                    Lists.addWhite(InterceptActivity.this, plan.root);

                    Target def = defaultTarget();
                    if (def != null && def.valid()
                            && LaunchHelper.launch(InterceptActivity.this, plan.uri, def)) {
                        LogStore.add(InterceptActivity.this, plan.root, plan.url,
                                LogStore.ACTION_ALWAYS, def.label);
                        toast(getString(R.string.toast_added_white, plan.root, def.label));
                        finishDelayed(400);
                        return;
                    }
                    // 还没设置默认浏览器：名单已经加好了，本次先选一个打开
                    toast(getString(R.string.toast_white_need_default, plan.root));
                    render(MODE_NO_DEFAULT);
                }
            });
            btnBlock.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Lists.removeWhite(InterceptActivity.this, plan.root);
                    Lists.addBlack(InterceptActivity.this, plan.root);
                    LogStore.add(InterceptActivity.this, plan.root, plan.url,
                            LogStore.ACTION_BLOCKED, "");
                    toast(getString(R.string.toast_added_black, plan.root));
                    finishDelayed(400);
                }
            });

            if (plan.srcPkg != null) {
                btnAppBlock.setVisibility(View.VISIBLE);
                btnAppBlock.setText(getString(R.string.block_this_app, plan.srcLabel));
                btnAppBlock.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Lists.setAppBlocked(InterceptActivity.this, plan.srcPkg, true);
                        LogStore.add(InterceptActivity.this, plan.root, plan.url,
                                LogStore.ACTION_APP_BLOCKED, plan.srcLabel);
                        toast(getString(R.string.toast_app_blocked, plan.srcLabel));
                        finishDelayed(400);
                    }
                });
            } else {
                btnAppBlock.setVisibility(View.GONE);
            }
        } else {
            llActions.setVisibility(View.GONE);
            llPicker.setVisibility(View.VISIBLE);
            tvDefault.setVisibility(View.GONE);
            tvPickHint.setText(mode == MODE_NO_DEFAULT
                    ? getString(R.string.pick_hint_after_always, plan.root)
                    : getString(R.string.pick_hint_once));
            btnBack.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    render(MODE_CHOOSE);
                }
            });
            // 选择列表只负责“用哪个打开这一次”，不再往名单里写东西
            fillBrowsers();
        }
    }

    private void fillBrowsers() {
        llBrowsers.removeAllViews();
        LayoutInflater inf = getLayoutInflater();

        BrowserCatalog.Diag diag = new BrowserCatalog.Diag();
        List<Target> list = BrowserCatalog.browsers(this, diag);

        if (list.isEmpty()) {
            TextView tv = new TextView(this);
            tv.setText(R.string.no_browser);
            tv.setTextSize(14f);
            tv.setTextColor(Themes.danger(this));
            tv.setPadding(0, dp(12), 0, dp(8));
            llBrowsers.addView(tv);

            TextView hint = new TextView(this);
            hint.setText(getString(R.string.no_browser_hint, diag.installedVisible));
            hint.setTextSize(12f);
            hint.setTextColor(Themes.onSurfaceVariant(this));
            hint.setPadding(0, 0, 0, dp(10));
            llBrowsers.addView(hint);
        }

        for (final Target t : list) {
            View row = inf.inflate(R.layout.item_choice, llBrowsers, false);
            ImageView iv = row.findViewById(R.id.ivIcon);
            TextView tv = row.findViewById(R.id.tvLabel);
            TextView tag = row.findViewById(R.id.tvTag);

            tv.setText(t.label);
            Drawable d = LaunchHelper.iconOf(this, t);
            if (d != null) {
                iv.setImageDrawable(d);
            } else {
                iv.setImageResource(android.R.drawable.sym_def_app_icon);
            }
            tag.setVisibility(t.preferred ? View.VISIBLE : View.GONE);

            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onPick(t);
                }
            });
            llBrowsers.addView(row);
        }

        // 兜底：交回系统的“打开方式”选择器
        View sys = inf.inflate(R.layout.item_choice, llBrowsers, false);
        ImageView sysIcon = sys.findViewById(R.id.ivIcon);
        sysIcon.setImageResource(android.R.drawable.ic_menu_more);
        TextView sysLabel = sys.findViewById(R.id.tvLabel);
        sysLabel.setText(list.isEmpty() ? R.string.pick_system_list : R.string.pick_system_other);
        sys.findViewById(R.id.tvTag).setVisibility(View.GONE);
        sys.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (LaunchHelper.systemPicker(InterceptActivity.this, plan.uri)) {
                    LogStore.add(InterceptActivity.this, plan.root, plan.url,
                            LogStore.ACTION_ONCE, getString(R.string.pick_system_short));
                    finish();
                } else {
                    toast(getString(R.string.toast_launch_failed,
                            getString(R.string.pick_system_short)));
                }
            }
        });
        llBrowsers.addView(sys);

        // 浏览器很多时限制列表高度，避免弹窗超出屏幕。
        // 上限取「整行」的高度，免得把某一行截成半截。
        final int rowPx = dp(58);
        final int capPx = rowPx * 4;
        int wSpec = View.MeasureSpec.makeMeasureSpec(
                getResources().getDisplayMetrics().widthPixels * 72 / 100,
                View.MeasureSpec.AT_MOST);
        int hSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        llBrowsers.measure(wSpec, hSpec);
        if (llBrowsers.getMeasuredHeight() > capPx) {
            ViewGroup.LayoutParams lp = svBrowsers.getLayoutParams();
            lp.height = capPx;
            svBrowsers.setLayoutParams(lp);
        }
    }

    private void onPick(Target t) {
        if (LaunchHelper.launch(this, plan.uri, t)) {
            LogStore.add(this, plan.root, plan.url, LogStore.ACTION_ONCE, t.label);
            finish();
            return;
        }
        toast(getString(R.string.toast_launch_failed, t.label));
    }

    // ------------------------------------------------------------------
    // 来源应用识别
    // ------------------------------------------------------------------

    private String sourcePackage() {
        // 1. Android 14+ 的公开 API
        if (Build.VERSION.SDK_INT >= 34) {
            try {
                String s = getLaunchedFromPackage();
                if (s != null && !s.isEmpty()) {
                    return checkSelf(s);
                }
            } catch (Throwable ignored) {
            }
        }
        // 2. 反射 getLaunchedFromPackage()（Android 10~13 上属于浅灰名单，通常可用）
        try {
            Method m = Activity.class.getMethod("getLaunchedFromPackage");
            Object r = m.invoke(this);
            if (r instanceof String) {
                String s = checkSelf((String) r);
                if (s != null) {
                    return s;
                }
            }
        } catch (Throwable ignored) {
        }
        // 3. 反射 getLaunchedFromUid()，再用 uid 反查包名
        try {
            Method m = Activity.class.getMethod("getLaunchedFromUid");
            Object r = m.invoke(this);
            if (r instanceof Integer) {
                String[] pkgs = getPackageManager().getPackagesForUid((Integer) r);
                if (pkgs != null) {
                    for (String pkg : pkgs) {
                        String s = checkSelf(pkg);
                        if (s != null) {
                            return s;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        // 4. referrer
        try {
            Uri ref = getReferrer();
            if (ref != null && "android-app".equals(ref.getScheme())) {
                return checkSelf(ref.getHost());
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private String checkSelf(String pkg) {
        if (pkg == null || pkg.isEmpty() || pkg.equals(getPackageName())) {
            return null;
        }
        if ("android".equals(pkg)) {
            return null;
        }
        return pkg;
    }

    private String labelOf(String pkg) {
        if (pkg == null) {
            return null;
        }
        try {
            PackageManager pm = getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            CharSequence label = pm.getApplicationLabel(ai);
            return label == null ? pkg : label.toString();
        } catch (Exception e) {
            return pkg;
        }
    }

    private void toast(String msg) {
        try {
            Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show();
        } catch (Exception ignored) {
        }
    }

    private void finishDelayed(long ms) {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                finish();
            }
        }, ms);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
