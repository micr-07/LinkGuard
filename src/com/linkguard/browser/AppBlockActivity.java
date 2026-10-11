package com.linkguard.browser;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** 应用屏蔽：勾选后，该应用拉起的所有链接一律拦下。 */
public class AppBlockActivity extends ListActivityBase {

    @Override
    protected boolean showSearch() {
        return true;
    }

    @Override
    protected boolean showCheck() {
        return true;
    }

    @Override
    protected boolean showIcon() {
        return true;
    }

    @Override
    protected boolean showDelete() {
        return false;
    }

    @Override
    protected String searchHint() {
        return Lang.s(this, "search_hint_app");
    }

    @Override
    protected String addButtonLabel() {
        return null;
    }

    @Override
    protected String emptyText() {
        return Lang.s(this, "empty_appblock");
    }

    @Override
    protected String note() {
        return Lang.s(this, "app_block_note");
    }

    @Override
    protected List<RowAdapter.Row> buildRows() {
        PackageManager pm = getPackageManager();
        List<RowAdapter.Row> rows = new ArrayList<RowAdapter.Row>();
        Set<String> seen = new HashSet<String>();

        List<ResolveInfo> ris;
        try {
            Intent main = new Intent(Intent.ACTION_MAIN);
            main.addCategory(Intent.CATEGORY_LAUNCHER);
            ris = pm.queryIntentActivities(main, 0);
        } catch (Exception e) {
            ris = Collections.emptyList();
        }

        for (ResolveInfo ri : ris) {
            ActivityInfo ai = ri.activityInfo;
            if (ai == null) {
                continue;
            }
            String pkg = ai.applicationInfo != null ? ai.applicationInfo.packageName : ai.packageName;
            if (pkg == null || pkg.equals(getPackageName()) || !seen.add(pkg)) {
                continue;
            }
            RowAdapter.Row r = new RowAdapter.Row(pkg);
            CharSequence label = ri.loadLabel(pm);
            r.title = (label == null || label.length() == 0) ? pkg : label.toString();
            r.subtitle = pkg;
            r.checked = Lists.isAppBlocked(this, pkg);
            try {
                r.icon = ri.loadIcon(pm);
            } catch (Exception ignored) {
            }
            rows.add(r);
        }

        // 被屏蔽但没有桌面图标的应用（系统组件、后台服务等）也要能在这里取消屏蔽
        for (String pkg : Lists.appBlocks(this)) {
            if (pkg.equals(getPackageName()) || !seen.add(pkg)) {
                continue;
            }
            RowAdapter.Row r = new RowAdapter.Row(pkg);
            r.title = labelOf(pm, pkg);
            r.subtitle = pkg;
            r.checked = true;
            try {
                r.icon = pm.getApplicationIcon(pkg);
            } catch (Exception ignored) {
            }
            rows.add(r);
        }

        final Collator collator = Collator.getInstance(Locale.CHINA);
        Collections.sort(rows, new Comparator<RowAdapter.Row>() {
            @Override
            public int compare(RowAdapter.Row a, RowAdapter.Row b) {
                return collator.compare(a.title, b.title);
            }
        });
        return rows;
    }

    @Override
    protected void onRowClicked(RowAdapter.Row row) {
        row.checked = !row.checked;
        Lists.setAppBlocked(this, row.key, row.checked);
        adapter.notifyDataSetChanged();
    }

    @Override
    protected void onToggle(RowAdapter.Row row, boolean checked) {
        Lists.setAppBlocked(this, row.key, checked);
    }

    private String labelOf(PackageManager pm, String pkg) {
        try {
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            CharSequence cs = pm.getApplicationLabel(ai);
            return (cs == null || cs.length() == 0) ? pkg : cs.toString();
        } catch (Exception e) {
            return pkg;
        }
    }
}
