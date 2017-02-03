package me.ggum.gum.adapter;

import android.app.LoaderManager;
import android.content.Intent;
import android.content.Loader;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.v7.widget.CardView;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.bumptech.glide.Glide;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import me.ggum.gum.R;
import me.ggum.gum.activity.EffectActivity;
import me.ggum.gum.activity.PickerActivity;
import me.ggum.gum.activity.PreviewActivity;
import me.ggum.gum.data.VideoItem;
import me.ggum.gum.holder.HeaderHolder;
import me.ggum.gum.holder.VideoThumbHolder;
import me.ggum.gum.loader.PickerLoader;
import me.ggum.gum.string.S;
import me.ggum.gum.utils.FileUtil;

/**
 * Created by sb on 2017. 1. 10..
 */

public class PickerAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> implements LoaderManager.LoaderCallbacks<List<VideoItem>>{
    private static final String TAG = "PickerAdapter";

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ITEM = 1;

    private static final float CARD_ELEVATION = 12.0f;
    private static final int THUMB_WIDTH = 400;
    private static final int THUMB_HEIGHT = 400;

    private PickerActivity activity;
    private List<VideoItem> items;

    public PickerAdapter(PickerActivity activity) {
        this.activity = activity;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        if(viewType == TYPE_HEADER){
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.picker_header, parent, false);
            return new HeaderHolder(v);
        }
        else if(viewType == TYPE_ITEM){
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.picker_thumb, parent, false);
            return new VideoThumbHolder(v);
        }
        throw new RuntimeException("there is no type that matches the type " + viewType);
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, final int position) {

        if(holder instanceof HeaderHolder){
            if(position == 0){
                ((HeaderHolder) holder).titleText.setText("Latest");
            }else if(position != 0 && position%3 == 0){
                ((HeaderHolder) holder).titleText.setText("Previous");
            }else{
                ((HeaderHolder) holder).titleText.setText("");
            }
        }
        else if(holder instanceof VideoThumbHolder){
            final int realPosition = position - 3;
            ((VideoThumbHolder) holder).setItem(items.get(realPosition));

            if(items.get(realPosition).isCheck()){
                ((VideoThumbHolder) holder).thumbCheckImg.setVisibility(ImageView.VISIBLE);
            }else if(!items.get(realPosition).isCheck()){
                ((VideoThumbHolder) holder).thumbCheckImg.setVisibility(ImageView.INVISIBLE);
            }

            long minute, second;
            minute = (items.get(realPosition).getDuration()/1000)%3600/60;
            second = (items.get(realPosition).getDuration()/1000)%3600%60;

            if(second >= 10){
                ((VideoThumbHolder) holder).thumbDurationText.setText(minute + ":" + second);
            }else{
                ((VideoThumbHolder) holder).thumbDurationText.setText(minute + ":0" + second);
            }

            if(!items.get(realPosition).getVideoPath().equals(PickerLoader.BLANK_AREA)){
                ((VideoThumbHolder) holder).thumbCardView.setVisibility(CardView.VISIBLE);
                //((VideoThumbHolder) holder).thumbCardView.setCardElevation(CARD_ELEVATION);
                if(items.get(realPosition).getThumb() != null){
                    ByteArrayOutputStream stream = new ByteArrayOutputStream();
                    items.get(realPosition).getThumb().compress(Bitmap.CompressFormat.JPEG, 50, stream);
                    Glide.with(activity)
                            .load(stream.toByteArray()).crossFade()
                            .override(THUMB_WIDTH, THUMB_HEIGHT).centerCrop().into(((VideoThumbHolder) holder).thumbBackImg);
                }else{
                    Glide.with(activity).load(items.get(realPosition).getThumbPath()).crossFade().override(THUMB_WIDTH, THUMB_HEIGHT).centerCrop()
                            .into(((VideoThumbHolder) holder).thumbBackImg);
                }

                ((VideoThumbHolder) holder).thumbBackImg.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if(items.get(realPosition).isCheck()){
                            //String newFile = moveFile(items.get(realPosition).getVideoPath());

                            //Log.d(TAG, newFile);
                            Intent intent = new Intent(activity, EffectActivity.class);
                            intent.putExtra(S.KEY_EFFECT_DURATION, items.get(realPosition).getDuration());
                            intent.putExtra(S.KEY_EFFECT_FRAME_RATE, (long)items.get(realPosition).getFrameRate());
                            intent.putExtra(S.KEY_EFFECT_FROM_WHAT, false);
                            intent.putExtra(S.KEY_INPUT_PATH, items.get(realPosition).getVideoPath());
                            activity.startActivity(intent);
                        }else{
                            Intent intent = new Intent(activity, PreviewActivity.class);
                            intent.putExtra(S.KEY_INPUT_PATH, items.get(realPosition).getVideoPath());
                            intent.putExtra(S.KEY_INPUT_DURATION, items.get(realPosition).getDuration());
                            intent.putExtra(S.KEY_INPUT_WIDTH, items.get(realPosition).getWidth());
                            intent.putExtra(S.KEY_INPUT_HEIGHT, items.get(realPosition).getHeight());
                            intent.putExtra(S.KEY_INPUT_FRAME_RATE, items.get(realPosition).getFrameRate());
                            intent.putExtra(S.KEY_INPUT_ORIENTATION, items.get(realPosition).getOrientation());
                            activity.startActivity(intent);
                        }
                    }
                });

            }else{
                ((VideoThumbHolder) holder).thumbCardView.setVisibility(CardView.INVISIBLE);
            }

        }
    }


    @Override
    public int getItemViewType(int position) {
        if(isPositionHeader(position)){
            return TYPE_HEADER;
        }else if(isPositionMidHeader(position)){
            return TYPE_HEADER;
        }else{
            return TYPE_ITEM;
        }
    }

    private boolean isPositionHeader(int position){
        return position == 0 || position == 1 || position == 2;
    }

    private boolean isPositionMidHeader(int position){
        if(items.get(position-3).getVideoPath().equals(PickerLoader.TITLE_AREA)){
            return true;
        }else{
            return false;
        }
    }

    @Override
    public int getItemCount() {
        return (items != null)? items.size()+3 : 0;
    }

    @Override
    public Loader<List<VideoItem>> onCreateLoader(int id, Bundle args) {
        return new PickerLoader(activity);
    }

    @Override
    public void onLoadFinished(Loader<List<VideoItem>> loader, List<VideoItem> data) {
        this.items = new ArrayList<VideoItem>(data);
        notifyDataSetChanged();
    }

    @Override
    public void onLoaderReset(Loader<List<VideoItem>> loader) {
        items.clear();
        notifyDataSetChanged();
    }

}
