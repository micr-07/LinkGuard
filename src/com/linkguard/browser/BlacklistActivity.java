package com.linkguard.browser;

import android.app.AlertDialog;
import android.content.DialogInterface;

import java.util.ArrayList;
import java.util.List;

/** 黑名单：命中的网址直接丢弃，只用系统小字提示。 */
public class BlacklistActivity extends ListActivityBase {

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
        return R.string.add_black_domain;
    }

    @Override
    protected int emptyRes() {
        return R.string.empty_blacklist;
    }

    @Override
    protected List<RowAdapter.Row> buildRows() {
        List<RowAdapter.Row> rows = new ArrayList<RowAdapter.Row>();
        for (String domain : Lists.blacklist(this)) {
            RowAdapter.Row r = new RowAdapter.Row(domain);
            r.title = domain;
            r.subtitle = getString(R.string.rule_block);
            rows.add(r);
        }
        return rows;
    }

    @Override
    protected void onRowClicked(final RowAdapter.Row row) {
        new AlertDialog.Builder(this)
                .setTitle(row.key)
                .setItems(new String[]{
                        getString(R.string.move_to_white),
                        getString(R.string.btn_delete)},
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int which) {
                                if (which == 0) {
                                    Lists.removeBlack(BlacklistActivity.this, row.key);
                                    Lists.addWhite(BlacklistActivity.this, row.key);
                                } else {
                                    Lists.removeBlack(BlacklistActivity.this, row.key);
                                }
                                reload();
                            }
                        })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @Override
    protected void onDeleteClicked(RowAdapter.Row row) {
        Lists.removeBlack(this, row.key);
        reload();
    }

    @Override
    protected void onAddClicked() {
        Dialogs.inputDomain(this, R.string.add_black_domain, R.string.add_rule_hint,
                new Dialogs.OnText() {
                    @Override
                    public void onText(String text) {
                        Lists.removeWhite(BlacklistActivity.this, text);
                        Lists.addBlack(BlacklistActivity.this, text);
                        reload();
                    }
                });
    }
}
