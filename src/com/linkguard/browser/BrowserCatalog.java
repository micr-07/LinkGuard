package com.linkguard.browser;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 枚举系统里所有能打开 http / https 链接的应用（不含自己）。
 *
 * <p>Android 11+ 有“包可见性”限制，国产 ROM（vivo / OPPO / 小米等）还会额外加一道
 * “读取应用列表”权限，被限制时 {@code queryIntentActivities()} 会直接返回空列表。
 * 所以这里用四层回退策略：
 * <ol>
 *   <li>标准查询（依赖清单里的 {@code <queries>}）</li>
 *   <li>再带 MATCH_ALL 查一次</li>
 *   <li>内置常见浏览器包名，逐个探测并按包名拉起</li>
 *   <li>都失败时，界面会退回“系统选择器”兜底</li>
 * </ol>
 */
public final class BrowserCatalog {

    public static final String PROBE_URL = "https://example.com/";

    /** PackageManager.MATCH_ALL，用字面量避免个别 SDK 的可见性差异。 */
    private static final int FLAG_MATCH_ALL = 0x00010000;

    /**
     * 常见浏览器包名。即使系统完全禁止枚举，也可以按包名把它们列出来并直接拉起，
     * 因为“用 setPackage 启动”不受包可见性限制。
     *
     * <p>注意：这只是兜底，不是唯一来源。正常情况下靠 {@code <queries>} 里的
     * “不带 category 的 VIEW 意图”就能看到全部浏览器。
     */
    public static final String[] KNOWN_BROWSERS = {
            "com.android.chrome",
            "com.chrome.beta",
            "com.chrome.dev",
            "com.chrome.canary",
            "mark.via",
            "mark.via.gp",
            "com.mmbox.xbrowser",
            "com.mmbox.xbrowser.pro",
            "com.quark.browser",
            "com.UCMobile",
            "com.uc.browser",
            "com.tencent.mtt",
            "com.tencent.mtt.x86",
            "com.baidu.searchbox",
            "com.baidu.browser.apps",
            "com.browser2345",
            "com.qihoo.browser",
            "com.qihoo.contents",
            "com.sogou.browser",
            "com.sogou.explorer",
            "com.vivo.browser",
            "com.android.browser",
            "com.miui.browser",
            "com.mi.globalbrowser",
            "com.heytap.browser",
            "com.coloros.browser",
            "com.oppo.browser",
            "com.huawei.browser",
            "com.hihonor.browser",
            "com.transsion.phoenix",
            "com.zui.browser",
            "com.ume.browser",
            "com.dolphin.browser.express.web",
            "com.jiubang.browser",
            "com.sec.android.app.sbrowser",
            "com.sec.android.app.sbrowser.beta",
            "org.mozilla.firefox",
            "org.mozilla.firefox_beta",
            "org.mozilla.focus",
            "org.mozilla.klar",
            "com.microsoft.emmx",
            "com.brave.browser",
            "com.brave.browser_beta",
            "com.vivaldi.browser",
            "com.opera.browser",
            "com.opera.browser.beta",
            "com.opera.mini.native",
            "com.opera.gx",
            "com.duckduckgo.mobile.android",
            "com.ecosia.android",
            "com.kiwibrowser.browser",
            "com.yandex.browser",
            "com.cloudmosa.puffinFree",
            "com.google.android.googlequicksearchbox",
            "org.chromium.webview_shell",
            "com.android.htmlviewer",
            "com.ghostery.android.ghostery",
            "acr.browser.lightning",
    };

    /** 枚举过程的诊断信息，用于在设置页里告诉用户为什么列表是空的。 */
    public static final class Diag {
        /** 标准查询能找到几个。 */
        public int countQuery;
        /** 带 MATCH_ALL 查询能找到几个。 */
        public int countQueryAll;
        /** 加上已知浏览器兜底后总共几个。 */
        public int countTotal;
        /** 已知浏览器里有几个确实装着。 */
        public int knownInstalled;
        /** 能看到的已安装应用数量（越小说明被限制得越狠）。 */
        public int installedVisible;
        /** 是否声明并拿到了 QUERY_ALL_PACKAGES。 */
        public boolean queryAllGranted;
    }

    private BrowserCatalog() {
    }

    public static Intent probeIntent() {
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(PROBE_URL));
        i.addCategory(Intent.CATEGORY_BROWSABLE);
        return i;
    }

    public static List<Target> browsers(Context ctx) {
        return browsers(ctx, null);
    }

    public static List<Target> browsers(Context ctx, Diag diag) {
        PackageManager pm = ctx.getPackageManager();
        String me = ctx.getPackageName();
        Map<String, Target> map = new LinkedHashMap<String, Target>();

        // 策略 1：标准查询
        collect(pm, me, 0, map);
        if (diag != null) {
            diag.countQuery = map.size();
            diag.queryAllGranted = hasQueryAll(ctx);
        }

        // 策略 2：带 MATCH_ALL 再查一次（个别 ROM 只有带这个标志才返回完整列表）
        if (map.isEmpty()) {
            collect(pm, me, FLAG_MATCH_ALL, map);
        }
        if (diag != null) {
            diag.countQueryAll = map.size();
        }

        // 策略 3：按内置的常见浏览器包名逐个探测
        int knownInstalled = 0;
        for (String pkg : KNOWN_BROWSERS) {
            if (map.containsKey(pkg)) {
                continue;
            }
            if (!isInstalled(pm, pkg)) {
                continue;
            }
            knownInstalled++;
            Target t = targetOfPackage(pm, pkg);
            if (t != null) {
                map.put(pkg, t);
            }
        }
        if (diag != null) {
            diag.knownInstalled = knownInstalled;
            diag.countTotal = map.size();
            diag.installedVisible = countInstalled(pm);
        }

        List<Target> out = new ArrayList<Target>(map.values());
        final Collator collator = Collator.getInstance(Locale.CHINA);
        Collections.sort(out, new Comparator<Target>() {
            @Override
            public int compare(Target a, Target b) {
                return collator.compare(a.label, b.label);
            }
        });

        String defPkg = Prefs.defaultPkg(ctx);
        if (!defPkg.isEmpty()) {
            for (int i = 0; i < out.size(); i++) {
                if (defPkg.equals(out.get(i).pkg)) {
                    Target t = out.remove(i);
                    out.add(0, new Target(t.label, t.pkg, t.act, true));
                    break;
                }
            }
        }
        return out;
    }

    // ------------------------------------------------------------------
    // 内部实现
    // ------------------------------------------------------------------

    private static void collect(PackageManager pm, String me, int flags, Map<String, Target> map) {
        // 探针 1：不带任何 category —— 最宽松，等同“所有能处理 http/https VIEW 的应用”
        merge(pm, me, new Intent(Intent.ACTION_VIEW, Uri.parse(PROBE_URL)), flags, map);

        // 探针 2：带 BROWSABLE（与清单里 <queries> 的声明一致）
        Intent p2 = new Intent(Intent.ACTION_VIEW, Uri.parse(PROBE_URL));
        p2.addCategory(Intent.CATEGORY_BROWSABLE);
        merge(pm, me, p2, flags, map);

        // 探针 3：同时带 DEFAULT，能多捞到一些浏览器
        Intent p3 = new Intent(Intent.ACTION_VIEW, Uri.parse(PROBE_URL));
        p3.addCategory(Intent.CATEGORY_DEFAULT);
        p3.addCategory(Intent.CATEGORY_BROWSABLE);
        merge(pm, me, p3, flags, map);
    }

    private static void merge(PackageManager pm, String me, Intent probe, int flags,
                              Map<String, Target> map) {
        List<ResolveInfo> ris;
        try {
            ris = pm.queryIntentActivities(probe, flags);
        } catch (Exception e) {
            return;
        }
        if (ris == null) {
            return;
        }
        for (ResolveInfo ri : ris) {
            ActivityInfo ai = ri.activityInfo;
            if (ai == null || ai.packageName == null) {
                continue;
            }
            if (me.equals(ai.packageName) || map.containsKey(ai.packageName)) {
                continue;
            }
            CharSequence cs = null;
            try {
                cs = ri.loadLabel(pm);
            } catch (Exception ignored) {
            }
            String label = (cs == null || cs.length() == 0) ? ai.packageName : cs.toString();
            map.put(ai.packageName, new Target(label, ai.packageName, ai.name, false));
        }
    }

    /** 先尝试真正解析出组件，失败就退回“只知道包名”，靠 setPackage 拉起。 */
    private static Target targetOfPackage(PackageManager pm, String pkg) {
        try {
            Intent probe = new Intent(Intent.ACTION_VIEW, Uri.parse(PROBE_URL));
            probe.addCategory(Intent.CATEGORY_BROWSABLE);
            probe.setPackage(pkg);
            List<ResolveInfo> ris = pm.queryIntentActivities(probe, FLAG_MATCH_ALL);
            if (ris != null) {
                for (ResolveInfo ri : ris) {
                    ActivityInfo ai = ri.activityInfo;
                    if (ai != null && pkg.equals(ai.packageName)) {
                        CharSequence cs = null;
                        try {
                            cs = ri.loadLabel(pm);
                        } catch (Exception ignored) {
                        }
                        String label = (cs == null || cs.length() == 0) ? pkg : cs.toString();
                        return new Target(label, pkg, ai.name, false);
                    }
                }
            }
        } catch (Exception ignored) {
        }
        String label = appLabel(pm, pkg);
        if (label == null) {
            return null;
        }
        return new Target(label, pkg, "", false);
    }

    private static String appLabel(PackageManager pm, String pkg) {
        try {
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            CharSequence cs = pm.getApplicationLabel(ai);
            return (cs == null || cs.length() == 0) ? pkg : cs.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isInstalled(PackageManager pm, String pkg) {
        try {
            pm.getPackageInfo(pkg, 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static int countInstalled(PackageManager pm) {
        try {
            List<PackageInfo> list = pm.getInstalledPackages(0);
            return list == null ? 0 : list.size();
        } catch (Exception e) {
            return 0;
        }
    }

    /** 是否声明并获得了 QUERY_ALL_PACKAGES（国产 ROM 的“读取应用列表”对应它）。 */
    public static boolean hasQueryAll(Context ctx) {
        try {
            return ctx.getPackageManager().checkPermission(
                    "android.permission.QUERY_ALL_PACKAGES", ctx.getPackageName())
                    == PackageManager.PERMISSION_GRANTED;
        } catch (Exception e) {
            return false;
        }
    }

    /** 本应用是不是系统当前默认浏览器。 */
    public static boolean isUsDefault(Context ctx) {
        try {
            Intent probe = probeIntent();
            ResolveInfo ri = ctx.getPackageManager()
                    .resolveActivity(probe, PackageManager.MATCH_DEFAULT_ONLY);
            if (ri == null || ri.activityInfo == null) {
                return false;
            }
            return ctx.getPackageName().equals(ri.activityInfo.packageName);
        } catch (Exception e) {
            return false;
        }
    }
}
