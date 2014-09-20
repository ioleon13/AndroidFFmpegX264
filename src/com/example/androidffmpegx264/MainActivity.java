package com.example.androidffmpegx264;

import android.os.Bundle;
import android.app.Activity;
import android.view.Menu;
import android.widget.TextView;

public class MainActivity extends Activity {
    private String TAG="MainActivity";
    
    static {
        System.loadLibrary("ffmpeg-x264");
    }
    
    private TextView mVersion;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        mVersion = (TextView)findViewById(R.id.version_num);
        FFmpegTest ffmpeg = new FFmpegTest();
        mVersion.setText(String.valueOf(ffmpeg.stringFromJNI()));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

}
