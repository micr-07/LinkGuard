package com.linkguard.browser;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.Method;
import java.util.Locale;

/**
 * 拦截页。
 *
 * <p>决策顺序不变：
 * <ol>
 *   <li>来源应用被屏蔽 → 拦下</li>
 *   <li>黑名单 → 拦下</li>
 *   <li>白名单 → 用指定浏览器（或默认浏览器）直接打开</li>
 *   <li>关闭了「弹窗询问」→ 用默认浏览器直接打开</li>
 *   <li>其余 → 弹窗询问</li>
 * </ol>
 *
 * <p>弹窗按最终设计稿：标题 + 来源 + 详情卡（站点信息 + 3:2 实时预览）+ 右下角「拒绝 / 允许」。
 * 其余动作（总是允许 / 总是拦截 / 屏蔽此应用 / 预览设置）收进右上角 ⋯ 打开的「更多」底部面板。
 */
public class InterceptActivity extends Activity {

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

    private TextView tvTitle;
    private TextView tvSub;
    private TextView tvRoot;
    private TextView tvUrl;
    private TextView tvPreviewHint;
    private FrameLayout boxPreview;
    private WebView wvPreview;

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
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (wvPreview != null) {
            try {
                wvPreview.stopLoading();
                wvPreview.destroy();
            } catch (Exception ignored) {
            }
            wvPreview = null;
        }
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

        if (p.srcPkg != null && Lists.isAppBlocked(this, p.srcPkg)) {
            p.kind = PLAN_APP_BLOCK;
            return p;
        }
        if (Lists.isBlack(this, root)) {
            p.kind = PLAN_BLOCK;
            return p;
        }
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
        if (!Prefs.showDialog(this)) {
            Target t = defaultTarget();
            if (t != null && t.valid()) {
                p.kind = PLAN_OPEN;
                p.target = t;
                return p;
            }
        }
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
                toast(Lang.f(this, "toast_app_blocked", p.srcLabel));
                finishDelayed(320);
                return;
            }
            finish();
            return;
        }

        if (p.kind == PLAN_BLOCK) {
            LogStore.add(this, p.root, p.url, LogStore.ACTION_BLOCKED, "");
            if (Prefs.toastOnBlock(this)) {
                toast(Lang.f(this, "toast_blocked", p.root));
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
            String label = p.target == null ? "" : p.target.label;
            toast(Lang.f(this, "toast_launch_failed", label));
            p.kind = PLAN_DIALOG;
        }

        render();
    }

    // ------------------------------------------------------------------
    // 弹窗
    // ------------------------------------------------------------------

    private void render() {
        setContentView(R.layout.dialog_intercept);

        tvTitle = findViewById(R.id.tvTitle);
        tvSub = findViewById(R.id.tvSub);
        tvRoot = findViewById(R.id.tvRoot);
        tvUrl = findViewById(R.id.tvUrl);
        tvPreviewHint = findViewById(R.id.tvPreviewHint);
        boxPreview = findViewById(R.id.boxPreview);
        wvPreview = findViewById(R.id.wvPreview);

        tvTitle.setText(Lang.s(this, "dialog_title"));
        tvRoot.setText(plan.root);
        tvUrl.setText(plan.url);
        tvSub.setText(Lang.f(this, "dialog_from", plan.srcLabel == null ? "?" : plan.srcLabel));

        findViewById(R.id.btnMore).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showMore();
            }
        });

        tvPreviewHint.setText(Lang.s(this, "preview_loading"));

        Button btnAllow = findViewById(R.id.btnAllow);
        btnAllow.setText(Lang.s(this, "allow"));
        btnAllow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                allowOnce();
            }
        });

        TextView btnDeny = findViewById(R.id.btnDeny);
        btnDeny.setText(Lang.s(this, "deny"));
        btnDeny.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                LogStore.add(InterceptActivity.this, plan.root, plan.url,
                        LogStore.ACTION_DENIED, "");
                toast(Lang.s(InterceptActivity.this, "toast_denied"));
                finishDelayed(300);
            }
        });

        // 预览框按 4:3 定高（高 : 宽 = 3 : 4）
        boxPreview.post(new Runnable() {
            @Override
            public void run() {
                int w = boxPreview.getWidth();
                if (w > 0) {
                    // 宽度必须显式钉死：applyZoom() 会把 WebView 的布局宽度设成 w/f
                    // （60% 时是 1.67 倍），clipToOutline 只裁显示不约束布局，
                    // 不钉死的话这个超宽子视图会把整张卡片撑出屏幕。
                    ViewGroup.LayoutParams lp = boxPreview.getLayoutParams();
                    lp.width = w;
                    lp.height = w * 3 / 4;
                    boxPreview.setLayoutParams(lp);
                }
                applyZoom();
            }
        });

        // 四角补色 = 详情卡的底色，用来盖掉 WebView 溢出的直角
        if (boxPreview instanceof RoundedBox) {
            int behind = Themes.colorOf(this, Themes.current(this), R.attr.lgSurfaceContainerHigh);
            RoundedBox box = (RoundedBox) boxPreview;
            box.setCornerColor(behind);
        }

        setupPreview();
    }

    /** 预览：实时渲染真实页面，但完全不可操作。 */
    private void setupPreview() {
        if (!Prefs.webPreview(this)) {
            boxPreview.setVisibility(View.GONE);
            return;
        }
        boxPreview.setVisibility(View.VISIBLE);

        try {
            WebSettings s = wvPreview.getSettings();
            s.setJavaScriptEnabled(false);
            s.setLoadWithOverviewMode(true);
            s.setUseWideViewPort(false);
            s.setSupportZoom(false);
            s.setBuiltInZoomControls(false);
            s.setDisplayZoomControls(false);
        } catch (Exception ignored) {
        }

        wvPreview.setBackgroundColor(0xFFFFFFFF);

        // 关键：吃掉所有触摸事件 → 预览看得见、点不动、也滚不了
        wvPreview.setClickable(false);
        wvPreview.setLongClickable(false);
        wvPreview.setFocusable(false);
        wvPreview.setFocusableInTouchMode(false);
        wvPreview.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return true;
            }
        });

        wvPreview.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                tvPreviewHint.setVisibility(View.VISIBLE);
                tvPreviewHint.setText(Lang.s(InterceptActivity.this, "preview_loading"));
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                tvPreviewHint.setVisibility(View.GONE);
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description,
                                        String failingUrl) {
                tvPreviewHint.setVisibility(View.VISIBLE);
                tvPreviewHint.setText(Lang.s(InterceptActivity.this, "preview_failed"));
            }
        });

        applyZoom();
        try {
            wvPreview.loadUrl(plan.url);
        } catch (Exception e) {
            tvPreviewHint.setText(Lang.s(this, "preview_failed"));
        }
    }

    /**
     * 预览缩放：整体缩放 WebView 视图。
     * 比 setInitialScale 可靠 —— 那个方法在某些精简 android.jar 里没有。
     */
    private void applyZoom() {
        if (wvPreview == null || boxPreview == null) {
            return;
        }
        int cw = boxPreview.getWidth();
        // 注意：这里不能用 getHeight()。
        // 外部刚用 post() 改过框高，此刻布局还没跑，getHeight() 还是旧值，
        // 会导致 WebView 按旧高度算尺寸，撑不满新框（底部空一大块）。
        // 直接读刚写进去的布局参数才是本次真正的高度。
        android.view.ViewGroup.LayoutParams blp = boxPreview.getLayoutParams();
        int ch = blp != null && blp.height > 0 ? blp.height : boxPreview.getHeight();
        if (cw <= 0 || ch <= 0) {
            return;
        }
        float f = Prefs.zoom(this) / 100f;
        // 关键：先把 WebView 的布局尺寸放大 1/f，让页面按更宽的视口重排，
        // 再整体缩回 f —— 这样 60% 是「看得更多」，而不是把内容缩成一小块留在角上。
        ViewGroup.LayoutParams lp = wvPreview.getLayoutParams();
        lp.width = (int) (cw / f);
        lp.height = (int) (ch / f);
        wvPreview.setLayoutParams(lp);
        wvPreview.setPivotX(0f);
        wvPreview.setPivotY(0f);
        wvPreview.setScaleX(f);
        wvPreview.setScaleY(f);
    }

    /** 「允许」：有默认浏览器就直接开，没有就让用户挑一个。 */
    private void allowOnce() {
        Target def = defaultTarget();
        if (def != null && def.valid()) {
            if (LaunchHelper.launch(this, plan.uri, def)) {
                LogStore.add(this, plan.root, plan.url, LogStore.ACTION_ONCE, def.label);
                finishDelayed(300);
            } else {
                toast(Lang.f(this, "toast_launch_failed", def.label));
            }
            return;
        }
        Dialogs.pickBrowser(this, Lang.s(this, "pick_default_title"), false,
                new Dialogs.OnPick() {
                    @Override
                    public void onPick(Target t, boolean useDefault) {
                        if (t != null && LaunchHelper.launch(InterceptActivity.this, plan.uri, t)) {
                            LogStore.add(InterceptActivity.this, plan.root, plan.url,
                                    LogStore.ACTION_ONCE, t.label);
                            finishDelayed(300);
                        } else {
                            toast(Lang.s(InterceptActivity.this, "toast_launch_failed"));
                        }
                    }
                });
    }

    /** 右上角 ⋯ →「更多」底部面板。 */
    private void showMore() {
        MoreSheet.create(this)
                .build(plan.root, plan.srcLabel, new MoreSheet.Callbacks() {
                    @Override
                    public void onAlwaysAllow() {
                        Lists.removeBlack(InterceptActivity.this, plan.root);
                        Lists.addWhite(InterceptActivity.this, plan.root);
                        LogStore.add(InterceptActivity.this, plan.root, plan.url,
                                LogStore.ACTION_ALWAYS, plan.root);
                        toast(Lang.f(InterceptActivity.this, "toast_allow_all", plan.root));
                        Target def = defaultTarget();
                        if (def != null && def.valid()) {
                            LaunchHelper.launch(InterceptActivity.this, plan.uri, def);
                        }
                        finishDelayed(400);
                    }

                    @Override
                    public void onAlwaysBlock() {
                        Lists.removeWhite(InterceptActivity.this, plan.root);
                        Lists.addBlack(InterceptActivity.this, plan.root);
                        LogStore.add(InterceptActivity.this, plan.root, plan.url,
                                LogStore.ACTION_BLOCKED, "");
                        toast(Lang.f(InterceptActivity.this, "toast_block_all", plan.root));
                        finishDelayed(400);
                    }

                    @Override
                    public void onBlockApp() {
                        confirmAppBlock();
                    }

                    @Override
                    public void onOpenHome() {
                        try {
                            startActivity(new Intent(InterceptActivity.this, MainActivity.class));
                        } catch (Exception ignored) {
                        }
                        finish();
                    }

                    @Override
                    public void onZoom() {
                        pickZoom();
                    }

                    @Override
                    public void onPreviewToggle(boolean on) {
                        if (boxPreview != null) {
                            boxPreview.setVisibility(on ? View.VISIBLE : View.GONE);
                        }
                        if (on && wvPreview != null && wvPreview.getUrl() == null) {
                            setupPreview();
                        }
                    }
                })
                .show();
    }

    /** 缩放：直接输数字，枚举太别扭。 */
    private void pickZoom() {
        MdDialog.create(this)
                .title(Lang.s(this, "zoom_title"))
                .message(Lang.f(this, "preview_zoom_hint", Prefs.ZOOM_MIN, Prefs.ZOOM_MAX))
                .content(Dialogs.zoomField(this, Prefs.zoom(this)))
                .negative(Lang.s(this, "cancel"), null)
                .positive(Lang.s(this, "ok"), new MdDialog.OnClick() {
                    @Override
                    public void onClick() {
                        int v = Dialogs.zoomValue(InterceptActivity.this, Prefs.zoom(InterceptActivity.this));
                        if (v < Prefs.ZOOM_MIN || v > Prefs.ZOOM_MAX) {
                            toast(Lang.s(InterceptActivity.this, "zoom_invalid"));
                            return;
                        }
                        Prefs.setZoom(InterceptActivity.this, v);
                        applyZoom();
                    }
                })
                .show();
    }

    /** 屏蔽整个应用是最强一级拦截，按系统惯例做二次确认。 */
    private void confirmAppBlock() {
        final String pkg = plan.srcPkg;
        final String label = plan.srcLabel;
        MdDialog.create(this)
                .title(Lang.f(this, "app_block_confirm_title", label))
                .message(Lang.s(this, "app_block_confirm_msg"))
                .negative(Lang.s(this, "cancel"), null)
                .positiveDanger(Lang.s(this, "app_block_confirm_ok"), new MdDialog.OnClick() {
                    @Override
                    public void onClick() {
                        Lists.setAppBlocked(InterceptActivity.this, pkg, true);
                        LogStore.add(InterceptActivity.this, plan.root, plan.url,
                                LogStore.ACTION_APP_BLOCKED, label);
                        toast(Lang.f(InterceptActivity.this, "app_block_done", label));
                        finishDelayed(400);
                    }
                })
                .show();
    }

    // ------------------------------------------------------------------
    // 来源应用识别
    // ------------------------------------------------------------------

    private String sourcePackage() {
        if (Build.VERSION.SDK_INT >= 34) {
            try {
                String s = getLaunchedFromPackage();
                if (s != null && !s.isEmpty()) {
                    return checkSelf(s);
                }
            } catch (Throwable ignored) {
            }
        }
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
}
