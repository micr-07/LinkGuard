package com.linkguard.browser;




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
    protected String searchHint() {
        return Lang.s(this, "search_hint_domain");
    }

    @Override
    protected String addButtonLabel() {
        return Lang.s(this, "add_black_domain");
    }

    @Override
    protected String emptyText() {
        return Lang.s(this, "empty_blacklist");
    }

    @Override
    protected List<RowAdapter.Row> buildRows() {
        List<RowAdapter.Row> rows = new ArrayList<RowAdapter.Row>();
        for (String domain : Lists.blacklist(this)) {
            RowAdapter.Row r = new RowAdapter.Row(domain);
            r.title = domain;
            r.subtitle = Lang.s(this, "rule_block");
            rows.add(r);
        }
        return rows;
    }

    @Override
    protected void onRowClicked(final RowAdapter.Row row) {
        MdDialog.create(this)
                .title(row.key)
                .items(new CharSequence[]{
                        Lang.s(this, "move_to_white"),
                        Lang.s(this, "btn_delete")},
                        new MdDialog.OnItem() {
                            @Override
                            public void onItem(int which) {
                                if (which == 0) {
                                    Lists.removeBlack(BlacklistActivity.this, row.key);
                                    Lists.addWhite(BlacklistActivity.this, row.key);
                                } else {
                                    Lists.removeBlack(BlacklistActivity.this, row.key);
                                }
                                reload();
                            }
                        })
                .negative(Lang.s(this, "cancel"), null)
                .show();
    }

    @Override
    protected void onDeleteClicked(RowAdapter.Row row) {
        Lists.removeBlack(this, row.key);
        reload();
    }

    @Override
    protected void onAddClicked() {
        Dialogs.inputDomain(this, Lang.s(this, "add_black_domain"), Lang.s(this, "add_rule_hint"),
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
