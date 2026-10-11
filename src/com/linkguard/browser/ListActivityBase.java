package com.linkguard.browser;

import android.app.Activity;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import java.util.List;

/** 四个名单 / 记录界面共用的骨架：搜索框 + 列表 + 空状态 + 底部按钮。 */
public abstract class ListActivityBase extends Activity {

    protected RowAdapter adapter;
    protected ListView listView;
    protected EditText searchBox;
    protected TextView emptyView;
    protected TextView noteView;
    protected Button addButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Themes.apply(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list);

        listView = findViewById(R.id.list);
        searchBox = findViewById(R.id.search);
        emptyView = findViewById(R.id.empty);
        noteView = findViewById(R.id.note);
        addButton = findViewById(R.id.addButton);

        View back = findViewById(R.id.btnBack);
        back.setContentDescription(Lang.s(this, "back"));
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        TextView barTitle = findViewById(R.id.barTitle);
        barTitle.setText(getTitle());

        listView.setDivider(new ColorDrawable(Themes.outlineVariant(this)));
        listView.setDividerHeight(Math.max(1,
                (int) getResources().getDisplayMetrics().density));

        adapter = new RowAdapter(this);
        adapter.setShowCheck(showCheck());
        adapter.setShowIcon(showIcon());
        adapter.setShowDelete(showDelete());
        adapter.setOnDelete(new RowAdapter.OnDelete() {
            @Override
            public void onDelete(RowAdapter.Row row) {
                onDeleteClicked(row);
            }
        });
        adapter.setOnToggle(new RowAdapter.OnToggle() {
            @Override
            public void onToggle(RowAdapter.Row row, boolean checked) {
                ListActivityBase.this.onToggle(row, checked);
            }
        });
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                onRowClicked(adapter.getItem(position));
            }
        });

        if (showSearch()) {
            searchBox.setVisibility(View.VISIBLE);
            searchBox.setHint(searchHint());
            searchBox.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int a, int b, int c) {
                }

                @Override
                public void onTextChanged(CharSequence s, int a, int b, int c) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    adapter.setQuery(s.toString());
                    updateEmpty();
                }
            });
        } else {
            searchBox.setVisibility(View.GONE);
        }

        String addRes = addButtonLabel();
        if (addRes != null) {
            addButton.setVisibility(View.VISIBLE);
            addButton.setText(addRes);
            addButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onAddClicked();
                }
            });
        } else {
            addButton.setVisibility(View.GONE);
        }

        String note = note();
        if (note != null) {
            noteView.setVisibility(View.VISIBLE);
            noteView.setText(note);
        } else {
            noteView.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    protected void reload() {
        adapter.setRows(buildRows());
        updateEmpty();
    }

    protected void updateEmpty() {
        if (adapter.getTotalCount() == 0) {
            emptyView.setText(emptyText());
            emptyView.setVisibility(View.VISIBLE);
        } else if (adapter.getCount() == 0) {
            emptyView.setText(Lang.s(this, "no_match"));
            emptyView.setVisibility(View.VISIBLE);
        } else {
            emptyView.setVisibility(View.GONE);
        }
    }

    protected boolean showDelete() {
        return true;
    }

    protected String note() {
        return null;
    }

    protected abstract boolean showSearch();

    protected abstract boolean showCheck();

    protected abstract boolean showIcon();

    protected abstract String searchHint();

    /** 底部按钮文字，0 表示不显示。 */
    protected abstract String addButtonLabel();

    protected abstract String emptyText();

    protected abstract List<RowAdapter.Row> buildRows();

    protected abstract void onRowClicked(RowAdapter.Row row);

    protected void onDeleteClicked(RowAdapter.Row row) {
    }

    protected void onToggle(RowAdapter.Row row, boolean checked) {
    }

    protected void onAddClicked() {
    }
}
