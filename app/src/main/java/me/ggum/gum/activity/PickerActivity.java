package me.ggum.gum.activity;

import android.content.Intent;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;

import me.ggum.gum.R;
import me.ggum.gum.adapter.PickerAdapter;
import me.ggum.gum.string.S;
import me.ggum.gum.utils.FileUtil;

public class PickerActivity extends AppCompatActivity {

    private FloatingActionButton cameraFab;
    private RecyclerView recyclerView;
    private PickerAdapter adapter;


    private View.OnClickListener cameraFabListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            Intent intent = new Intent(getApplicationContext(), CameraActivity.class);
            intent.putExtra(S.KEY_TEMP_PATH, FileUtil.generateTempOutputPath());
            startActivity(intent);
            finish();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_picker);
        init();
    }

    private void init(){
        cameraFab = (FloatingActionButton) findViewById(R.id.picker_camera_action_btn);
        cameraFab.setOnClickListener(cameraFabListener);
        recyclerView = (RecyclerView) findViewById(R.id.picker_recycler_view);
        adapter = new PickerAdapter(this);
        recyclerView.setHasFixedSize(false);

        getLoaderManager().initLoader(0, null, adapter);
        recyclerView.setLayoutManager(new GridLayoutManager(null, 3, LinearLayoutManager.VERTICAL, false));

        recyclerView.setAdapter(adapter);
    }
}
