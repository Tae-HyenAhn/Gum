package me.ggum.gum.loader;

import android.content.AsyncTaskLoader;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import me.ggum.gum.data.VideoItem;
import wseemann.media.FFmpegMediaMetadataRetriever;

/**
 * Created by sb on 2017. 1. 10..
 */

public class PickerLoader extends AsyncTaskLoader<List<VideoItem>>{

    private final static String TAG = "PickerLoader";

    private List<VideoItem> items;
    private ContentResolver contentResolver;
    private Context context;
    public static final String BLANK_AREA = "blank";
    public static final String TITLE_AREA = "title_area";

    public PickerLoader(Context context) {
        super(context);
        contentResolver = context.getContentResolver();
        this.context = context;
    }

    @Override
    public List<VideoItem> loadInBackground() {
        String[] projection = { MediaStore.Video.VideoColumns.DATA };
        String sortOrderDESC = MediaStore.Video.VideoColumns.DATE_TAKEN + " COLLATE LOCALIZED DESC";

        Cursor videoCursor = contentResolver.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null, sortOrderDESC);

        FFmpegMediaMetadataRetriever fmmr = null;

        ArrayList<VideoItem> result = new ArrayList<VideoItem>(videoCursor.getCount());
        int dataColumnIndex = videoCursor.getColumnIndex(projection[0]);

        String videoPath; long duration; int width; int height; String dateTaken=null;
        double frameRate; int orientation; Bitmap thumb; String thumbPath; boolean check = false;
        fmmr = new FFmpegMediaMetadataRetriever();

        int latestCount = 0;
        boolean attachedSubTitle = false;
        if(videoCursor.moveToFirst()){
            do{

                videoPath = videoCursor.getString(dataColumnIndex);
                try{
                    fmmr.setDataSource(videoPath);

                    duration = Long.parseLong(fmmr.extractMetadata(FFmpegMediaMetadataRetriever.METADATA_KEY_DURATION));
                    if(duration < 3000){
                        continue;
                    }
                    width = Integer.parseInt(fmmr.extractMetadata(FFmpegMediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
                    height = Integer.parseInt(fmmr.extractMetadata(FFmpegMediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));

                    String tempDateAll = fmmr.extractMetadata(FFmpegMediaMetadataRetriever.METADATA_KEY_CREATION_TIME);
                    String tempDate[];
                    if(tempDateAll == null){
                       continue;
                    }else{
                        tempDate = tempDateAll.split(" ");
                    }
                    dateTaken = tempDate[0];
                    frameRate = Double.parseDouble(fmmr.extractMetadata(FFmpegMediaMetadataRetriever.METADATA_KEY_FRAMERATE));
                    orientation = Integer.parseInt(fmmr.extractMetadata(FFmpegMediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION));
                    thumbPath = getThumbnailPath(context, videoPath);
                    if(thumbPath == null || thumbPath.equals("")){

                        thumb = createThumbnailFromPath(videoPath);
                    }else{
                        thumb = null;
                    }

                    check = videoPath.contains("Ggum");
                    Log.d(TAG, "PATH: "+videoPath+", GGUM? "+check);

                    if(!attachedSubTitle){
                        if(result.size() > 0){
                            if(dateTaken.equals(result.get(result.size()-1).getDateTaken())){
                                latestCount++;
                                Log.d(TAG, "EQU");
                            }else if(!dateTaken.equals(result.get(result.size()-1).getDateTaken())){
                                Log.d(TAG, "!EQU");
                                latestCount++;
                                if(latestCount == 1){
                                    result.add(new VideoItem(BLANK_AREA, 0, 0, 0, BLANK_AREA, 0.0, 0, null, false, BLANK_AREA));
                                    result.add(new VideoItem(BLANK_AREA, 0, 0, 0, BLANK_AREA, 0.0, 0, null, false, BLANK_AREA));
                                    result.add(new VideoItem(TITLE_AREA, 0, 0, 0, TITLE_AREA, 0.0, 0, null, false, TITLE_AREA));
                                    result.add(new VideoItem(TITLE_AREA, 0, 0, 0, TITLE_AREA, 0.0, 0, null, false, TITLE_AREA));
                                    result.add(new VideoItem(TITLE_AREA, 0, 0, 0, TITLE_AREA, 0.0, 0, null, false, TITLE_AREA));

                                    attachedSubTitle = true;
                                }else if(latestCount == 2){
                                    result.add(new VideoItem(BLANK_AREA, 0, 0, 0, BLANK_AREA, 0.0, 0, null, false, BLANK_AREA));
                                    result.add(new VideoItem(TITLE_AREA, 0, 0, 0, TITLE_AREA, 0.0, 0, null, false, TITLE_AREA));
                                    result.add(new VideoItem(TITLE_AREA, 0, 0, 0, TITLE_AREA, 0.0, 0, null, false, TITLE_AREA));
                                    result.add(new VideoItem(TITLE_AREA, 0, 0, 0, TITLE_AREA, 0.0, 0, null, false, TITLE_AREA));

                                    attachedSubTitle = true;
                                }else if(latestCount > 2){
                                    if(latestCount%3 == 1){
                                        result.add(new VideoItem(BLANK_AREA, 0, 0, 0, BLANK_AREA, 0.0, 0, null, false, BLANK_AREA));
                                        result.add(new VideoItem(BLANK_AREA, 0, 0, 0, BLANK_AREA, 0.0, 0, null, false, BLANK_AREA));
                                        result.add(new VideoItem(TITLE_AREA, 0, 0, 0, TITLE_AREA, 0.0, 0, null, false, TITLE_AREA));
                                        result.add(new VideoItem(TITLE_AREA, 0, 0, 0, TITLE_AREA, 0.0, 0, null, false, TITLE_AREA));
                                        result.add(new VideoItem(TITLE_AREA, 0, 0, 0, TITLE_AREA, 0.0, 0, null, false, TITLE_AREA));

                                        attachedSubTitle = true;

                                    }else if(latestCount%3 == 2){
                                        result.add(new VideoItem(BLANK_AREA, 0, 0, 0, BLANK_AREA, 0.0, 0, null, false, BLANK_AREA));
                                        result.add(new VideoItem(TITLE_AREA, 0, 0, 0, TITLE_AREA, 0.0, 0, null, false, TITLE_AREA));
                                        result.add(new VideoItem(TITLE_AREA, 0, 0, 0, TITLE_AREA, 0.0, 0, null, false, TITLE_AREA));
                                        result.add(new VideoItem(TITLE_AREA, 0, 0, 0, TITLE_AREA, 0.0, 0, null, false, TITLE_AREA));

                                        attachedSubTitle = true;
                                    }else if(latestCount%3 == 0){
                                        result.add(new VideoItem(TITLE_AREA, 0, 0, 0, TITLE_AREA, 0.0, 0, null, false, TITLE_AREA));
                                        result.add(new VideoItem(TITLE_AREA, 0, 0, 0, TITLE_AREA, 0.0, 0, null, false, TITLE_AREA));
                                        result.add(new VideoItem(TITLE_AREA, 0, 0, 0, TITLE_AREA, 0.0, 0, null, false, TITLE_AREA));

                                        attachedSubTitle = true;
                                    }
                                }

                            }
                        }
                    }

                    Log.d(TAG, "duration: "+duration + ", width: "+width+", height: "+height+", dateTaken: "+dateTaken+", frameRate: "+frameRate+
                            ", orien: "+ orientation+ ", thumb: "+", tpath: "+thumbPath);
                    result.add(new VideoItem(videoPath, duration, width, height, dateTaken, frameRate, orientation, thumb, check, thumbPath));
                } catch(IllegalArgumentException e){
                    e.printStackTrace();
                }


            }while(videoCursor.moveToNext());
        }
        for(int i=0; i<result.size(); i++){
            Log.d(TAG, result.get(i).getDateTaken());
        }
        fmmr.release();
        videoCursor.close();
        Log.d(TAG, "RESULT SIZE: "+result.size());
        return result;
    }

    private String getThumbnailPath(Context context, String videoPath){
        Uri video = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        String[] projection = new String[]{MediaStore.Video.Media._ID};
        String where = MediaStore.Video.Media.DATA+"=?";
        String[] whereArgs = new String[]{videoPath};
        Cursor media = context.getContentResolver().query(video, projection, where, whereArgs, null);

        if(!media.moveToFirst()){

            return null;
        }

        String videoId = media.getString(0);

        Uri thumbnail = MediaStore.Video.Thumbnails.EXTERNAL_CONTENT_URI;
        projection = new String[]{MediaStore.Video.Thumbnails.DATA};
        where = MediaStore.Video.Thumbnails.VIDEO_ID+"=?";
        whereArgs = new String[]{videoId};
        Cursor thumb = context.getContentResolver().query(thumbnail, projection, where, whereArgs, null);

        if(!thumb.moveToFirst()){
            return null;
        }

        String thumnailPath = thumb.getString(0);

        return thumnailPath;
    }

    public Bitmap createThumbnailFromPath(String filePath){
        return ThumbnailUtils.createVideoThumbnail(filePath, MediaStore.Images.Thumbnails.MINI_KIND);
    }

    @Override
    public void deliverResult(List<VideoItem> data) {
        if(isReset()){
            if(data != null){
                onReleaseResources(data);
            }
        }

        List<VideoItem> oldItems = this.items;
        this.items = data;

        if(isStarted()){
            super.deliverResult(data);
        }
        if(oldItems != null){
            onReleaseResources(data);
        }
    }

    @Override
    protected void onStartLoading() {
        if(items != null) deliverResult(items);
        else forceLoad();
    }

    @Override
    protected void onStopLoading() {

        cancelLoad();
    }


    @Override
    public void onCanceled(List<VideoItem> data) {
        super.onCanceled(data);
        onReleaseResources(items);
    }

    @Override
    protected void onReset() {
        super.onReset();
        onStopLoading();

        if(items != null){
            onReleaseResources(items);
            items = null;
        }
    }

    protected void onReleaseResources(List<VideoItem> items){

    }
}
