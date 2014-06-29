package com.kuna.bgmstoremobile;

import java.util.List;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

public class ListAdapter extends BaseAdapter {
    List<SongData> lsd;
    LayoutInflater inflater;

    public ListAdapter (Context c, List<SongData> lsd) {
        this.lsd = lsd;
        inflater = (LayoutInflater)c.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    @Override
    public int getCount() {
        // TODO Auto-generated method stub
        return lsd.size();
    }

    @Override
    public Object getItem(int position) {
        // TODO Auto-generated method stub
        return lsd.get(position);
    }

    @Override
    public long getItemId(int position) {
        // TODO Auto-generated method stub
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = convertView;
        ViewHolder viewHolder;
        if (view == null) {
            viewHolder = new ViewHolder();
            view = inflater.inflate(R.layout.listview, parent, false);
            viewHolder.txt = (TextView) view.findViewById(R.id.txt);
            view.setTag(viewHolder);
        } else {
            viewHolder = (ViewHolder) view.getTag();
        }

        viewHolder.txt.setText(lsd.get(position).title);
        return view;
    }

    private static class ViewHolder {
        public TextView txt;
    }
}
