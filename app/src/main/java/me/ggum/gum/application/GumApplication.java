package me.ggum.gum.application;

import android.app.Application;
import android.content.ContentResolver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.media.ThumbnailUtils;
import android.provider.MediaStore;

import java.util.ArrayList;

import me.ggum.gum.data.VideoItem;

/**
 * Created by sb on 2017. 1. 10..
 */

public class GumApplication extends Application{

    public static ArrayList<VideoItem> items;

    @Override
    public void onCreate() {
        items = new ArrayList<VideoItem>();

        super.onCreate();

    }

    @Override
    public void onTerminate() {
        super.onTerminate();
    }


}
