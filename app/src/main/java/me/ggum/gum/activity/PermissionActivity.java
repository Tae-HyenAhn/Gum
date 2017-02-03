package me.ggum.gum.activity;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.media.ThumbnailUtils;
import android.os.AsyncTask;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import com.gun0912.tedpermission.PermissionListener;
import com.gun0912.tedpermission.TedPermission;

import java.util.ArrayList;

import me.ggum.gum.R;
import me.ggum.gum.utils.FileUtil;
import me.ggum.gum.string.S;

public class PermissionActivity extends AppCompatActivity {



    private PermissionListener permissionListener = new PermissionListener() {
        @Override
        public void onPermissionGranted() {

            String tempPath = FileUtil.generateTempOutputPath();
            //Bitmap thumb = getCameraPickerThumbnail();

            Intent intent = new Intent(getApplicationContext(), CameraActivity.class);
            intent.putExtra(S.KEY_TEMP_PATH, tempPath);
            //intent.putExtra(S.KEY_THUMB_BITMAP, (Bitmap) thumb);
            startActivity(intent);
            finish();


        }

        @Override
        public void onPermissionDenied(ArrayList<String> deniedPermissions) {
            finish();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_permission);
        new TedPermission(this)
                .setPermissionListener(permissionListener)
                .setDeniedMessage(R.string.denied_message)
                .setPermissions(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                .check();
    }




}
