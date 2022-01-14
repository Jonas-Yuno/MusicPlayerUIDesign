package com.example.musicplayeruidesign;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.AppSettingsDialog;
import pub.devrel.easypermissions.EasyPermissions;

public class MainActivity extends AppCompatActivity implements EasyPermissions.PermissionCallbacks {

    private static final String TAG = "MainActivity";
//    private static final String[] LOCATION_AND_CONTACTS =
//            {Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.READ_CONTACTS};
//
//    private static final int RC_CAMERA_PERM = 123;
//    private static final int RC_LOCATION_CONTACTS = 124;
//    private static final int RC_CAMERA_AND_LOCATION = 456;
    private static final int RC_WRITE_AND_READ_EXTERNAL_STORAGE = 300;//识别码

    //private ImageView buttonRepeat;
    private ImageButton imagePlayPause, imageButtonNext, imageButtonPrevious, buttonRepeat, buttonShuffle;
    private TextView textCurrentTime, textTotalDuaration, textTitle, textArtist, textNowPausing;
    private SeekBar playerSeekBar;
    private MediaPlayer mediaPlayer;
    private List<Music> musicList;
    private Handler handler = new Handler();
    private int playPosition = 0;//播放的位置
    private boolean isRepeated = false;//循环
    private boolean isRandom = false;
    private int randomPosition = -1;//随机位置 数组存储
    private ArrayList<Integer> randomPositions;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getViews();
        
        requireWriteAndReadExternalStoragePermissions();

    }

    private void getViews() {
        imagePlayPause = findViewById(R.id.buttonPlay);
        imageButtonNext = findViewById(R.id.imageButtonNext);
        imageButtonPrevious = findViewById(R.id.imageButtonPrevious);
        buttonRepeat = findViewById(R.id.buttonRepeat);
        buttonShuffle = findViewById(R.id.buttonShuffle);
        textCurrentTime = findViewById(R.id.textCurrentTime);
        textTotalDuaration = findViewById(R.id.textTotalDuration);
        textNowPausing = findViewById(R.id.textNowPausing);
        textTitle = findViewById(R.id.textTitle);
        textArtist = findViewById(R.id.textArtist);
        playerSeekBar = findViewById(R.id.playerSeekBar);
    }

    private void init() {
        mediaPlayer = new MediaPlayer();
        
        FileManager fileManager = FileManager.getInstance(this);
        musicList = fileManager.getMusics(); //歌曲列表

        setListeners();

        playerSeekBar.setMax(1000);

        prepareMediaPlayer(playPosition);//准备
    }

    @AfterPermissionGranted(RC_WRITE_AND_READ_EXTERNAL_STORAGE) //This is optional
    private void requireWriteAndReadExternalStoragePermissions() {
        String[] perms = {Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE};
        if (EasyPermissions.hasPermissions(this, perms)) {
            // Already have permission, do the thing
            isManager();//再次检测是否安卓11以上版本，是测获取额外所有文件的管理权限
        } else {
            // Do not have permissions, request them now
            EasyPermissions.requestPermissions(this, getString(R.string.permissions),
                    RC_WRITE_AND_READ_EXTERNAL_STORAGE, perms);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
    }

    @Override
    public void onPermissionsGranted(int requestCode, List<String> list) {
       init();
    }

    @Override
    public void onPermissionsDenied(int requestCode, List<String> perms) {
        Toast.makeText(this, getString(R.string.permissions_error), Toast.LENGTH_SHORT).show();
        Log.d(TAG, "onPermissionsDenied:" + requestCode + ":" + perms.size());
        // (Optional) Check whether the user denied any permissions and checked "NEVER ASK AGAIN."
        // This will display a dialog directing them to enable the permission in app settings.
        if (EasyPermissions.somePermissionPermanentlyDenied(this, perms)) {
            new AppSettingsDialog.Builder(this)
                    .setPositiveButton(R.string.authorize_msg)
                    .setTitle(R.string.authorize_title_msg)
                    .setRationale(R.string.modify_permission_msg)
                    .setThemeResId(R.style.DialogStyle)
                    .build()
                    .show();
        }
    }

    public boolean gtSdk30() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R;
    }

    private void isManager() {
        if (gtSdk30()) {
            if (Environment.isExternalStorageManager()) init();
            else getManager();
        } else init();
    }

    private void getManager() {
        AlertDialog alertDialog;
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.DialogStyle);
        builder.setPositiveButton(getString(R.string.authorize_msg), null);
        builder.setTitle(getString(R.string.authorize_title_msg));
        builder.setMessage(getString(R.string.file_manger_msg));
        builder.setCancelable(false);
        alertDialog = builder.create();
        alertDialog.show();
        alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            alertDialog.dismiss();
            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            intent.setData(Uri.parse("package:" + getPackageName()));
            Log.i(TAG,getPackageName());
            startActivityForResult(intent, 0x99);
        });
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setListeners() {
        imagePlayPause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mediaPlayer.isPlaying()) {
                    handler.removeCallbacks(update);
                    mediaPlayer.pause();
                    textNowPausing.setText(R.string.now_pausing);
                    imagePlayPause.setImageResource(R.drawable.ic_play);
                } else {
                    mediaPlayer.start();
                    textNowPausing.setText(R.string.now_playing);
                    imagePlayPause.setImageResource(R.drawable.ic_pause);
                    updateSeekBar();
                }
            }
        });

        imageButtonNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mediaPlayer.reset();
                if (playPosition == musicList.size() - 1) { //循环列表
                    playPosition = 0;
                } else {
                    playPosition++;
                }
                if (isRandom) {
                    playOtherMusic(randomPositions.get(playPosition));
                } else {
                    playOtherMusic(playPosition);
                }

            }
        });

        imageButtonPrevious.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mediaPlayer.reset();
                if (playPosition == 0) {
                    playPosition = musicList.size() - 1;
                } else {
                    playPosition--;
                }
                if (isRandom) {
                    playOtherMusic(randomPositions.get(playPosition));
                } else {
                    playOtherMusic(playPosition);
                }
            }
        });

        buttonRepeat.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isRepeated) {
                    buttonRepeat.setImageResource(R.drawable.ic_repeat_one);
                    Toast.makeText(MainActivity.this, "开启单曲循环", Toast.LENGTH_SHORT).show();
                    isRepeated = true;
                } else {
                    buttonRepeat.setImageResource(R.drawable.ic_repeat);
                    isRepeated = false;
                    Toast.makeText(MainActivity.this, "关闭单曲循环", Toast.LENGTH_SHORT).show();
                }
            }
        });

        buttonShuffle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isRandom) {
                    randomPositions = getDiffNO(musicList.size());
                    buttonShuffle.setImageResource(R.drawable.ic_shuffle);
                    Toast.makeText(MainActivity.this, "随机播放", Toast.LENGTH_SHORT).show();
                    isRandom = true;
                } else {
                    playPosition = randomPositions.get(playPosition);
//                    randomPosition = -1;
//                    randomPositions = null;
                    buttonShuffle.setImageResource(R.drawable.ic_play_sequence);
                    Toast.makeText(MainActivity.this, "列表播放", Toast.LENGTH_SHORT).show();
                    isRandom = false;
                }
            }
        });

        playerSeekBar.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    handler.removeCallbacks(update);
                }
                if (event.getAction() == MotionEvent.ACTION_MOVE) {
                    SeekBar seekBar = (SeekBar) v;
                    int playPosition = (mediaPlayer.getDuration() / 1000) * seekBar.getProgress();
                    textCurrentTime.setText(milliSecondsToTimer(playPosition));
                }
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    SeekBar seekBar = (SeekBar) v;
                    int playPosition = (mediaPlayer.getDuration() / 1000) * seekBar.getProgress();
                    mediaPlayer.seekTo(playPosition);
                    textCurrentTime.setText(milliSecondsToTimer(playPosition));
                    updateSeekBar();
                }
                return false;
            }
        });

        mediaPlayer.setOnBufferingUpdateListener(new MediaPlayer.OnBufferingUpdateListener() {
            @Override
            public void onBufferingUpdate(MediaPlayer mp, int percent) {
                playerSeekBar.setSecondaryProgress(percent * 10);
            }
        });

        mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                playerSeekBar.setProgress(0);
                imagePlayPause.setImageResource(R.drawable.ic_play);
                textCurrentTime.setText(R.string.zero);
                textTotalDuaration.setText(R.string.zero);
                mediaPlayer.reset();
                if (isRepeated) {
                    if (isRandom) {
                        playOtherMusic(randomPositions.get(playPosition));//循环随机数组中的歌曲
                    } else {
                        playOtherMusic(playPosition);
                    }
                } else {
                    if (playPosition == musicList.size() - 1) { //循环列表
                        playPosition = 0;
                    } else {
                        playPosition++;
                    }
                    if (isRandom) {
                        playOtherMusic(randomPositions.get(playPosition));
                    } else {
                        playOtherMusic(playPosition);
                    }
                }

            }
        });

        mediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                playerSeekBar.setProgress(0);
                imagePlayPause.setImageResource(R.drawable.ic_play);
                textCurrentTime.setText(R.string.zero);
                textTotalDuaration.setText(R.string.zero);
                mediaPlayer.reset();
                prepareMediaPlayer(playPosition);
                return false;
            }
        });
    }

//    private int nextPosition(int currentPositon) {
//        if (currentPositon == musicList.size() - 1) {
//            currentPositon = 0;
//        } else {
//            currentPositon++;
//        }
//        if (isRandom) {
//            return randomPosition = currentPositon;
//        } else {
//            return playPosition = currentPositon;
//        }
//    }

    private void prepareMediaPlayer(int playPosition) {
        try {
//            mediaPlayer.setDataSource("http://192.168.1.5:8088/upload/1.mp3"); //URL of music file
//            mediaPlayer.setDataSource("https://cos.ap-guangzhou.myqcloud.com/momentbg-1255653016/1727965354/e9babf2f-63fe-4379-8555-10d8708feffa.zip");
//            String musicPath1 = Environment.getExternalStorageDirectory().getAbsolutePath() + "/Music/1.mp3";
//
//            String externalStorageDir = Environment.getExternalStorageDirectory().toString();
//            String musicPath = externalStorageDir + File.separator + "Music" + File.separator + "1.mp3";
            Music music = musicList.get(playPosition);
            mediaPlayer.setDataSource(music.getPath());
//            mediaPlayer.prepareAsync();// 通过异步的方式装载媒体资源
//            mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
//                @Override
//                public void onPrepared(MediaPlayer mp) {
//                    mediaPlayer.start();// 装载完毕回调
//                }
//            });
            mediaPlayer.prepare();
            textNowPausing.setText(R.string.now_pausing);
            textTitle.setText(music.getName());
            textArtist.setText(music.getArtist());
            textTotalDuaration.setText(milliSecondsToTimer(mediaPlayer.getDuration()));
        } catch (Exception e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
            System.out.println(e.getMessage());
        }
    }

    private Runnable update = new Runnable() {
        @Override
        public void run() {
            updateSeekBar();
            long currentDuration = mediaPlayer.getCurrentPosition();
            textCurrentTime.setText(milliSecondsToTimer(currentDuration));
        }
    };

    private void updateSeekBar() {
        if (mediaPlayer.isPlaying()) {
            playerSeekBar.setProgress((int) (((float) mediaPlayer.getCurrentPosition() / mediaPlayer.getDuration()) * 1000));
            handler.postDelayed(update, 1000);
        }
    }

    private void playOtherMusic(int playPosition) {
        prepareMediaPlayer(playPosition);
        mediaPlayer.start();
        textNowPausing.setText(R.string.now_playing);
        imagePlayPause.setImageResource(R.drawable.ic_pause);
        updateSeekBar();
    }

    private boolean hasExteralPermissions() {
        String[] perms = {Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE};
        return EasyPermissions.hasPermissions(this, perms);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == AppSettingsDialog.DEFAULT_SETTINGS_REQ_CODE) {
            // Do something after user returned from app settings screen
            if (hasExteralPermissions() ? true : false) {
                init();
            } else {
                Toast.makeText(this, "权限获取失败，即将关闭应用", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
        if (requestCode == 0x99){
            if (hasExteralPermissions() ? true : false) {
                init();
            } else {
                finish();
                Toast.makeText(this, "权限获取失败，即将关闭应用", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private String milliSecondsToTimer(long milliSeconds) {
        String secondsString;

        int hours = (int) (milliSeconds / (1000 * 60 * 60));
        String timerString = "";
        int minutes = (int) (milliSeconds % (1000 * 60 * 60)) / (1000 * 60);
        int seconds = (int) ((milliSeconds % (1000 * 60 * 60)) % (1000 * 60) / 1000);

        if (hours > 0) {
            timerString = hours + ":";
        }

        if (seconds < 10) {
            secondsString = "0" + seconds;
        } else {
            secondsString = "" + seconds;
        }

        timerString = timerString + minutes + ":" + secondsString;
        return timerString;
    }

    private ArrayList<Integer> getDiffNO(int n) { //// 生成 [0-n) 个不重复的随机正数
        // list 用来保存这些随机数
        ArrayList<Integer> list = new ArrayList<Integer>();
        Random rand = new Random();
        boolean[] bool = new boolean[n];
        int num = 0;
        for (int i = 0; i < n; i++) {
            do {
                // 如果产生的数相同继续循环
                num = rand.nextInt(n);
            } while (bool[num]);
            bool[num] = true;
            list.add(num);
        }
        return list;
    }

    @Override
    protected void onDestroy() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }
        super.onDestroy();
    }

}