package com.linkguard.browser;



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
    protected String searchHint() {
        return Lang.s(this, "search_hint_domain");
    }

    @Override
    protected String addButtonLabel() {
        return Lang.s(this, "add_white_domain");
    }

    @Override
    protected String emptyText() {
        return Lang.s(this, "empty_whitelist");
    }

    @Override
    protected List<RowAdapter.Row> buildRows() {
        List<RowAdapter.Row> rows = new ArrayList<RowAdapter.Row>();
        for (String domain : Lists.whitelist(this)) {
            RowAdapter.Row r = new RowAdapter.Row(domain);
            Target t = Lists.whiteTarget(this, domain);
            r.title = domain;
            r.subtitle = t == null
                    ? Lang.s(this, "white_no_browser")
                    : Lang.f(this, "rule_app", t.label);
            rows.add(r);
        }
        return rows;
    }

    @Override
    protected void onRowClicked(final RowAdapter.Row row) {
        MdDialog.create(this)
                .title(row.key)
                .items(new CharSequence[]{
                        Lang.s(this, "white_pick_short"),
                        Lang.s(this, "move_to_black"),
                        Lang.s(this, "btn_delete")},
                        new MdDialog.OnItem() {
                            @Override
                            public void onItem(int which) {
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
                .negative(Lang.s(this, "cancel"), null)
                .show();
    }

    private void pickBrowserFor(final String domain) {
        Dialogs.pickBrowser(this, Lang.f(this, "white_pick_browser", domain), true,
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
        Dialogs.inputDomain(this, Lang.s(this, "add_white_domain"), Lang.s(this, "add_rule_hint"),
                new Dialogs.OnText() {
                    @Override
                    public void onText(String text) {
                        Lists.removeBlack(WhitelistActivity.this, text);
                        // 默认只加进名单，跟随全局默认浏览器；
                        // 想给这个网址单独指定浏览器，点这一行再设置（特例）
                        Lists.addWhite(WhitelistActivity.this, text);
                        reload();
                        Toast.makeText(WhitelistActivity.this,
                                Lang.s(WhitelistActivity.this, "white_added_follow_default"),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }
}
