package com.linkguard.browser;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** 白名单：命中的网址不再弹窗询问，直接跟随默认浏览器打开；单独指定了浏览器的按指定来。 */
public class WhitelistActivity extends ListActivityBase {

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
    protected int searchHintRes() {
        return R.string.search_hint_domain;
    }

    @Override
    protected int addButtonRes() {
        return R.string.add_white_domain;
    }

    @Override
    protected int emptyRes() {
        return R.string.empty_whitelist;
    }

    @Override
    protected List<RowAdapter.Row> buildRows() {
        List<RowAdapter.Row> rows = new ArrayList<RowAdapter.Row>();
        for (String domain : Lists.whitelist(this)) {
            RowAdapter.Row r = new RowAdapter.Row(domain);
            Target t = Lists.whiteTarget(this, domain);
            r.title = domain;
            r.subtitle = t == null
                    ? getString(R.string.white_no_browser)
                    : getString(R.string.rule_app, t.label);
            rows.add(r);
        }
        return rows;
    }

    @Override
    protected void onRowClicked(final RowAdapter.Row row) {
        new AlertDialog.Builder(this)
                .setTitle(row.key)
                .setItems(new String[]{
                        getString(R.string.white_pick_short),
                        getString(R.string.move_to_black),
                        getString(R.string.btn_delete)},
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int which) {
                                if (which == 0) {
                                    pickBrowserFor(row.key);
                                } else if (which == 1) {
                                    Lists.removeWhite(WhitelistActivity.this, row.key);
                                    Lists.addBlack(WhitelistActivity.this, row.key);
                                    reload();
                                } else {
                                    Lists.removeWhite(WhitelistActivity.this, row.key);
                                    reload();
                                }
                            }
                        })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void pickBrowserFor(final String domain) {
        Dialogs.pickBrowser(this, getString(R.string.white_pick_browser, domain), true,
                new Dialogs.OnPick() {
                    @Override
                    public void onPick(Target target, boolean useDefault) {
                        Lists.putWhite(WhitelistActivity.this, domain, useDefault ? null : target);
                        reload();
                    }
                });
    }

    @Override
    protected void onDeleteClicked(RowAdapter.Row row) {
        Lists.removeWhite(this, row.key);
        reload();
    }

    @Override
    protected void onAddClicked() {
        Dialogs.inputDomain(this, R.string.add_white_domain, R.string.add_rule_hint,
                new Dialogs.OnText() {
                    @Override
                    public void onText(String text) {
                        Lists.removeBlack(WhitelistActivity.this, text);
                        // 默认只加进名单，跟随全局默认浏览器；
                        // 想给这个网址单独指定浏览器，点这一行再设置（特例）
                        Lists.addWhite(WhitelistActivity.this, text);
                        reload();
                        Toast.makeText(WhitelistActivity.this,
                                getString(R.string.white_added_follow_default),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }
}
