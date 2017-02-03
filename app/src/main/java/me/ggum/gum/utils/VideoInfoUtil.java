package me.ggum.gum.utils;

import java.util.Calendar;

/**
 * Created by sb on 2017. 1. 10..
 */

public class VideoInfoUtil {
    public static int getYearToMillis(long millisDate){

        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(millisDate);
        return calendar.get(Calendar.YEAR);

    }

    public static int getMonthToMillis(long millisDate){

        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(millisDate);
        return calendar.get(Calendar.MONTH);

    }

    public static int getDayToMillis(long millisDate){

        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(millisDate);
        return calendar.get(Calendar.DAY_OF_MONTH);

    }

    public static int getVideoWidth(String resolution){

        if(!resolution.equals("") && resolution != null){
            String[] resolArray = resolution.split("x");

            return Integer.parseInt(resolArray[0]);
        }else{
            return -1;
        }
    }

    public static int getVideoHeight(String resolution){

        if(!resolution.equals("") && resolution != null){
            String[] resolArray = resolution.split("x");

            return Integer.parseInt(resolArray[1]);
        }else{
            return -1;
        }
    }
}
