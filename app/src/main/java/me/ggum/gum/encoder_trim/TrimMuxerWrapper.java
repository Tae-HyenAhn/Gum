package me.ggum.gum.encoder_trim;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;

import java.nio.ByteBuffer;

/**
 * Created by sb on 2017. 1. 24..
 */

public class TrimMuxerWrapper {
    private static final String TAG = "TrimMuxerWrapper";

    private final MediaMuxer muxer;
    private final int totalTracks;
    private int trackCounter;

    private OnMuxerListener listener;

    public void setOnMuxerListener(OnMuxerListener listener){
        this.listener = listener;
    }

    public TrimMuxerWrapper(MediaMuxer muxer, int totalTracks) {
        this.muxer = muxer;
        this.totalTracks = totalTracks;
    }

    synchronized public int addTrack(MediaFormat format){

        int trackIndex = muxer.addTrack(format);
        trackCounter++;
        if(isStarted()){
            muxer.start();
            notifyAll();
        }else{
            while(!isStarted()){
                try{
                    wait();
                }catch (InterruptedException e){
                    e.printStackTrace();
                }
            }
        }
        return trackIndex;
    }

    synchronized public void writeSampleData(int trackIndex, ByteBuffer byteBuffer, MediaCodec.BufferInfo bufferInfo){

        try{
            muxer.writeSampleData(trackIndex, byteBuffer, bufferInfo);
            Log.d(TAG, "WRITE: "+trackIndex);
        }catch(IllegalStateException e){
            e.printStackTrace();
        }finally {
            Log.d(TAG, "ILLEGAL MUXER");
        }

    }

    public void release(){
        if(muxer != null){
            trackCounter--;
            Log.d(TAG, "MX: TRACKCOUNTER: "+trackCounter);
            try{
                if(isEnded()){
                    Log.d(TAG, "MX: MUXER STOP");
                    muxer.stop();
                    muxer.release();
                    if(listener != null){
                        Log.d(TAG, "MX: MUXER LISTENER");
                        listener.onMuxerStop();
                    }
                    notifyAll();
                }else{
                    while(!isEnded()){
                        try{
                            Log.d(TAG, "MX: MUXER WAIT");
                            wait();
                        }catch (InterruptedException e){
                            e.printStackTrace();
                        }
                    }
                }
                Log.d(TAG, "MUXER STOP");
                //muxer.stop();
            }catch(Exception e){
            }
            //Log.d(TAG, "MX: MUXER RELEASE");


        }
    }

    private boolean isStarted(){
        return trackCounter == totalTracks;
    }

    private boolean isEnded() { return trackCounter == 0; }

    public interface OnMuxerListener{
        void onMuxerStop();
    }
}
