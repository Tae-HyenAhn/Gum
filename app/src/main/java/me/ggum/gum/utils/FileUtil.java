package me.ggum.gum.utils;

import android.os.Environment;

import java.io.File;

/**
 * Created by sb on 2017. 1. 4..
 */

public class FileUtil {
    private static final String V_TITLE = "ggum_";
    private static final String FOLDER_NAME = "Ggum";
    private static final String TEMP_FOLDER = "TEMP";
    private static final String TEMP_FILE_NAME = "ggum_temp.mp4";

    private static boolean generateSaveFolder(String folderPath){
        if( Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)){
            String dirPath = folderPath;
            File f = new File(dirPath);
            if(!f.exists()){
                if(f.mkdirs()){
                    return true;
                }else{
                    return false;
                }
            }
            return true;
        }else{
            return false;
        }
    }

    public static String generateOutputVideoPath(){
        String outPath = "";
        String folderPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/DCIM/" + FOLDER_NAME;
        if(generateSaveFolder(folderPath)){
            outPath = folderPath+"/"+generateOutputVideoName();
            return outPath;
        }else{
            return "";
        }
    }

    public static String generateTempOutputPath(){
        String outPath = "";
        String tempPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/DCIM/" + FOLDER_NAME+ "/" + TEMP_FOLDER;
        if(generateSaveFolder(tempPath)){
            outPath = tempPath + "/" + TEMP_FILE_NAME;
            return outPath;
        }else{
            return "";
        }
    }

    private static String generateOutputVideoName(){
        long time = System.currentTimeMillis();

        String vName = V_TITLE + Long.toString(time) + ".mp4";

        return vName;

    }
}
