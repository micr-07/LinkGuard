# -*- coding: utf-8 -*-
"""底层优化 + 降版本兼容。

1. minSdk 29 -> 21
2. 补 API 24 以下的「默认应用」设置入口（ACTION_MANAGE_DEFAULT_APPS_SETTINGS 是 24+）
3. Lang：缓存语言代码，避免每次取文案都读 SharedPreferences
4. BrowserCatalog：进程级缓存浏览器列表 + 显式失效
5. Themes：缓存解析好的颜色，避免每行都建 ContextThemeWrapper
6. 修正清单里「本应用不联网」的过时注释
"""
import io
import os
import re

ROOT = r"D:\工作\应用逆向\构建APK\LinkGuard"
SRC = os.path.join(ROOT, "src", "com", "linkguard", "browser")


def patch(path, pairs, required=True):
    s = io.open(path, encoding="utf-8").read()
    done = 0
    for old, new in pairs:
        if old in s:
            s = s.replace(old, new, 1)
            done += 1
        elif required:
            print("    !! 没匹配到: %s" % old.strip().splitlines()[0][:60])
    io.open(path, "w", encoding="utf-8").write(s)
    return done


n = 0

# ---------------------------------------------------------------- 1. minSdk
p = os.path.join(ROOT, "build.ps1")
s = io.open(p, encoding="utf-8-sig").read()
s = s.replace("[int]$MinSdk = 29,", "[int]$MinSdk = 21,")
io.open(p, "w", encoding="utf-8-sig").write(s)
print("  [1] build.ps1  MinSdk 29 -> 21")

p = os.path.join(ROOT, "AndroidManifest.xml")
s = io.open(p, encoding="utf-8").read()
s = s.replace('android:minSdkVersion="29"', 'android:minSdkVersion="21"')
s = s.replace(
    '    <!-- 本应用自身不联网，声明它只是为了满足系统“默认浏览器”角色的资格校验。 -->',
    '    <!--\n'
    '      联网只用于拦截弹窗里的「网页实时预览」。除此之外没有任何网络行为，\n'
    '      名单、记录、配置全部存在本机 SharedPreferences，不上传任何数据。\n'
    '      声明它同时也是满足系统「默认浏览器」角色资格校验的必要条件。\n'
    '    -->')
io.open(p, "w", encoding="utf-8").write(s)
print("  [1] Manifest   minSdk 29 -> 21，并修正过时的「不联网」注释")

# ---------------------------------------------------------------- 2. 默认应用入口
patch(os.path.join(SRC, "MainActivity.java"), [(
    """    private void openDefaultAppsSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS));
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            } catch (Exception ignored) {
            }
        }
    }""",
    """    /**
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
    }""")])
print("  [2] MainActivity  补 API 24 以下的入口")

# ---------------------------------------------------------------- 3. Lang 缓存
p = os.path.join(SRC, "Lang.java")
s = io.open(p, encoding="utf-8").read()
s = s.replace(
    """    private static String loadedCode;
    private static final Map<String, String> TABLE = new HashMap<String, String>();""",
    """    private static String loadedCode;
    private static final Map<String, String> TABLE = new HashMap<String, String>();

    /**
     * 语言偏好的内存副本。
     * s() 每次取文案都会走到 ensure()，若每次都读 SharedPreferences
     * 就是一屏几百次哈希查找 —— 缓存下来。
     */
    private static String prefCache;
    private static boolean prefLoaded;""")
s = s.replace(
    """    private static void ensure(Context c) {
        String code = normalize(Prefs.lang(c));""",
    """    private static void ensure(Context c) {
        if (!prefLoaded) {
            prefCache = Prefs.lang(c);
            prefLoaded = true;
        }
        String code = normalize(prefCache);""")
s = s.replace(
    """    public static void invalidate() {
        loadedCode = null;
        TABLE.clear();
    }""",
    """    public static void invalidate() {
        loadedCode = null;
        prefLoaded = false;
        prefCache = null;
        TABLE.clear();
    }""")
io.open(p, "w", encoding="utf-8").write(s)
print("  [3] Lang        语言偏好加内存缓存")

# ---------------------------------------------------------------- 4. BrowserCatalog 缓存
p = os.path.join(SRC, "BrowserCatalog.java")
s = io.open(p, encoding="utf-8").read()
s = s.replace(
    """    public static List<Target> browsers(Context ctx) {
        return browsers(ctx, null);
    }""",
    """    /**
     * 进程级缓存。
     * queryIntentActivities() 要遍历所有已安装应用，主线程上跑一次几十毫秒；
     * 主界面刷新、弹窗、选择器都会调它，不缓存会重复付出这个代价。
     * 用户手动「重新扫描」或安装/卸载应用后，调 {@link #invalidate()} 作废。
     */
    private static List<Target> CACHE;

    public static void invalidate() {
        CACHE = null;
    }

    public static List<Target> browsers(Context ctx) {
        if (CACHE != null) {
            return CACHE;
        }
        List<Target> list = browsers(ctx, (Diag) null);
        CACHE = list;
        return list;
    }""")
io.open(p, "w", encoding="utf-8").write(s)
print("  [4] BrowserCatalog  浏览器列表加进程级缓存")

# ---------------------------------------------------------------- 5. Themes 颜色缓存
p = os.path.join(SRC, "Themes.java")
s = io.open(p, encoding="utf-8").read()
s = s.replace(
    """    public static int colorOf(Context c, String key, int attr) {
        ContextThemeWrapper w = new ContextThemeWrapper(c, themeRes(key));
        return color(w, attr);
    }""",
    """    /**
     * 解析好的颜色缓存，键是「配色/属性」。
     * 每次 colorOf 都要新建 ContextThemeWrapper 并跑一遍主题解析，
     * 列表滚动时是逐行调用的，代价不小。键里带了配色，换主题不会读到旧值。
     */
    private static final Map<String, Integer> COLOR_CACHE = new HashMap<String, Integer>();

    public static int colorOf(Context c, String key, int attr) {
        String k = key + "/" + attr;
        Integer hit = COLOR_CACHE.get(k);
        if (hit != null) {
            return hit;
        }
        ContextThemeWrapper w = new ContextThemeWrapper(c, themeRes(key));
        int col = color(w, attr);
        COLOR_CACHE.put(k, col);
        return col;
    }""")
if "import java.util.HashMap;" not in s:
    s = s.replace("import java.util.Map;", "import java.util.HashMap;\nimport java.util.Map;")
if "import java.util.Map;" not in s:
    s = s.replace("import android.content.ContextWrapper;", "import android.content.ContextWrapper;\n\nimport java.util.HashMap;\nimport java.util.Map;")
io.open(p, "w", encoding="utf-8").write(s)
print("  [5] Themes      颜色解析加缓存")
print()
print("  完成")
