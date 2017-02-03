package me.ggum.gum.holder;

import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import me.ggum.gum.R;

/**
 * Created by sb on 2017. 1. 10..
 */

public class HeaderHolder extends RecyclerView.ViewHolder {

    public TextView titleText;


    public HeaderHolder(View itemView) {
        super(itemView);
        titleText = (TextView) itemView.findViewById(R.id.picker_header_title);

    }

}
