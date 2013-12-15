/**
 The MIT License (MIT)

 Copyright (c) 2013 Valentin Konovalov

 Permission is hereby granted, free of charge, to any person obtaining a copy
 of this software and associated documentation files (the "Software"), to deal
 in the Software without restriction, including without limitation the rights
 to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 copies of the Software, and to permit persons to whom the Software is
 furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in
 all copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 THE SOFTWARE.*/
package ru.valle.safetrade;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import ru.valle.btc.BTCUtils;

import java.util.ArrayList;
import java.util.Collection;

public final class HistoryAdapter extends BaseAdapter {
    private final ArrayList<TradeRecord> items = new ArrayList<TradeRecord>();

    @Override
    public int getCount() {
        return items.size();
    }

    @Override
    public TradeRecord getItem(int position) {
        return position < 0 || position >= items.size() ? null : items.get(position);
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    @Override
    public long getItemId(int position) {
        return getItem(position).id;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Context context = parent.getContext();
        final View view;
        if (convertView != null) {
            view = convertView;
        } else {
            view = LayoutInflater.from(context).inflate(R.layout.trade_record_item, null);
        }

        TradeRecord item = getItem(position);
        if (item != null) {
            int status = item.getStatus();
            boolean allSet = status == R.string.all_set || status == R.string.successfully_sold;
            TextView typeView = (TextView) view.findViewById(R.id.type);
            if (item.type == TradeRecord.TYPE_SELL) {
                typeView.setText(context.getString(allSet ? R.string.sold : R.string.selling));
            } else if (item.type == TradeRecord.TYPE_BUY) {
                typeView.setText(context.getString(allSet ? R.string.bought : R.string.buying));
            } else {
                typeView.setText("");
            }

            TextView nameView = (TextView) view.findViewById(R.id.name);
            if (TextUtils.isEmpty(item.name)) {
                nameView.setText("");
            } else {
                nameView.setText(item.name);
            }

            TextView statusView = (TextView) view.findViewById(R.id.status);
            statusView.setText(context.getString(R.string.status_title) + " " + context.getString(status));

            TextView balanceView = (TextView) view.findViewById(R.id.price);
            if (item.balance < 0) {
                balanceView.setText("");
            } else {
                balanceView.setText(BTCUtils.formatValue(item.balance) + " " + context.getString(R.string.btc_suffix));
            }
        }
        return view;
    }

    public void setItems(Collection<TradeRecord> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }
}
