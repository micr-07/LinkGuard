package com.linkguard.browser;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;

/** 把链接交给指定的浏览器打开。 */
public final class LaunchHelper {

    private LaunchHelper() {
    }

    public static boolean launch(Context ctx, Uri uri, Target t) {
        if (t == null || !t.valid() || uri == null) {
            return false;
        }
        if (t.pkg.equals(ctx.getPackageName())) {
            return false; // 防止自己调用自己
        }
        Intent i = new Intent(Intent.ACTION_VIEW, uri);
        i.addCategory(Intent.CATEGORY_BROWSABLE);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (!t.act.isEmpty()) {
            i.setComponent(new ComponentName(t.pkg, t.act));
        } else {
            i.setPackage(t.pkg);
        }
        try {
            ctx.startActivity(i);
            return true;
        } catch (Exception e) {
            // 组件失效时退化为“只指定包名”
        }
        if (!t.act.isEmpty()) {
            Intent j = new Intent(Intent.ACTION_VIEW, uri);
            j.addCategory(Intent.CATEGORY_BROWSABLE);
            j.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            j.setPackage(t.pkg);
            try {
                ctx.startActivity(j);
                return true;
            } catch (Exception e) {
                return false;
            }
        }
        return false;
    }

    public static Drawable iconOf(Context ctx, Target t) {
        if (t == null || !t.valid()) {
            return null;
        }
        try {
            return ctx.getPackageManager().getApplicationIcon(t.pkg);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 兜底方案：把链接交回系统选择器，由用户自己挑浏览器。
     * 会把自己排除掉，避免用户选中本应用造成死循环。
     */
    public static boolean systemPicker(Context ctx, Uri uri) {
        if (uri == null) {
            return false;
        }
        try {
            Intent target = new Intent(Intent.ACTION_VIEW, uri);
            target.addCategory(Intent.CATEGORY_BROWSABLE);
            Intent chooser = Intent.createChooser(target, null);
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            chooser.putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS,
                    new ComponentName[]{new ComponentName(ctx, InterceptActivity.class)});
            ctx.startActivity(chooser);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
