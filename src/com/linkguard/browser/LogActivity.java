package com.linkguard.browser;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** 浏览 / 拦截记录。 */
public class LogActivity extends ListActivityBase {

    @Override
    protected boolean showSearch() {
        return true;
    }

    @Override
    protected boolean showCheck() {
        return false;
    }

    @Override
    protected boolean showIcon() {
        return false;
    }

    @Override
    protected boolean showDelete() {
        return false;
    }

    @Override
    protected String searchHint() {
        return Lang.s(this, "search_hint_log");
    }

    @Override
    protected String addButtonLabel() {
        return Lang.s(this, "btn_clear_logs");
    }

    @Override
    protected String emptyText() {
        return Lang.s(this, "empty_logs");
    }

    @Override
    protected List<RowAdapter.Row> buildRows() {
        List<RowAdapter.Row> rows = new ArrayList<RowAdapter.Row>();
        JSONArray arr = LogStore.all(this);
        SimpleDateFormat fmt = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) {
                continue;
            }
            String action = o.optString("a", "");
            String target = o.optString("tg", "");
            String root = o.optString("root", "");
            RowAdapter.Row r = new RowAdapter.Row(root + "#" + i + " " + o.optString("url", ""));
            r.title = actionText(action, target) + "  " + root;
            r.subtitle = fmt.format(new Date(o.optLong("t", 0L))) + "  " + o.optString("url", "");
            rows.add(r);
        }
        return rows;
    }

    private String actionText(String action, String target) {
        if (LogStore.ACTION_BLOCKED.equals(action)) {
            return Lang.s(this, "action_blocked");
        }
        if (LogStore.ACTION_APP_BLOCKED.equals(action)) {
            return Lang.s(this, "action_app_blocked") + " · " + target;
        }
        if (LogStore.ACTION_ALWAYS.equals(action)) {
            return Lang.s(this, "action_always") + " · " + target;
        }
        if (LogStore.ACTION_ONCE.equals(action)) {
            return Lang.s(this, "action_once") + " · " + target;
        }
        if (LogStore.ACTION_DENIED.equals(action)) {
            return Lang.s(this, "action_denied");
        }
        return Lang.s(this, "action_opened") + " · " + target;
    }

    @Override
    protected void onRowClicked(RowAdapter.Row row) {
        // 记录只读，点击不做事
    }

    @Override
    protected void onAddClicked() {
        LogStore.clear(this);
        reload();
    }
}
