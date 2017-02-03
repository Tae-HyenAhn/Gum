package me.ggum.gum.encoder_export;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;

import java.nio.ByteBuffer;

/**
 * Created by sb on 2017. 1. 25..
 */

public class ExportMuxerWrapper {
    private static final String TAG = "ExportMuxerWrapper";

    private final MediaMuxer muxer;
    private final int totalTracks;
    private int trackCounter;

    public ExportMuxerWrapper(MediaMuxer muxer, int totalTracks) {
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
        if(muxer != null){
            try{
                muxer.writeSampleData(trackIndex, byteBuffer, bufferInfo);
            }catch(IllegalStateException e){
                e.printStackTrace();
            }finally {
                Log.d(TAG, "Illegal Finally muxer");
            }
        }

    }

    public void release(){
        if(muxer != null){
            trackCounter--;
            Log.d(TAG, "TRACKCOUNTER: "+trackCounter);
            try{
                if(isEnded()){
                    muxer.stop();
                    notifyAll();
                }else{
                    while(!isEnded()){
                        try{
                            wait();
                        }catch (InterruptedException e){
                            e.printStackTrace();
                        }
                    }
                }
                Log.d(TAG, "MUXER STOP");
                muxer.stop();
            }catch(Exception e){
            }
            muxer.release();
        }
    }

    private boolean isStarted(){
        return trackCounter == totalTracks;
    }

    private boolean isEnded() { return trackCounter == 0; }
}
