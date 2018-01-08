package com.example.juan.juanwalkie;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Vibrator;
import android.support.v4.app.NotificationCompat;
import android.support.v4.graphics.drawable.RoundedBitmapDrawable;
import android.support.v4.graphics.drawable.RoundedBitmapDrawableFactory;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.text.InputType;
import android.util.Base64;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.github.nkzawa.emitter.Emitter;
import com.github.nkzawa.socketio.client.IO;
import com.github.nkzawa.socketio.client.Socket;
import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;

import org.apache.commons.io.FileUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Date;

public class PublicChannel extends AppCompatActivity{

    private final int notificationID = 5055;
    private String userChannelId;

    private MediaPlayer mPlayer;
    private MediaPlayer beep_sound;
    private MediaPlayer stop_recording;
    private MediaPlayer error_sound;
    private MediaRecorder mRecorder;
    private String mInputFile;
    private String mOutputFile;
    private TextView text_status;
    private TextView text_users;
    private TextView text_log;
    private View circle_status;
    private View record_audio;
    private GradientDrawable bgShape;
    private GradientDrawable color_status;
    private NotificationManager notificationManager;
    private Vibrator vibrator;
    private Long recordCurrentTime;
    private ArrayList<InputFile> inputAudioQueue;
    private Toolbar toolbar;
    private AlertDialog signOffDialog;
    private AlertDialog addChannelDialog;
    private AlertDialog changeChannelDialog;
    private AlertDialog.Builder builder;
    private EditText addChannelInput;
    private EditText chChannelInput;

    private ImageView imgUserPic;
    private Socket mSocket;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_public_channel);

        userChannelId = "jja296y2ewd949ld803qvwqblm4y46t5s";
        setTitle("Public channel");

        toolbar = findViewById(R.id.toolBar);
        setSupportActionBar(toolbar);

        addChannelInput = new EditText(this);
        chChannelInput = new EditText(this);
        setModals();

        try {
            IO.Options opts = new IO.Options();
            opts.query = "name=" + getIntent().getStringExtra("NAME");
            opts.query += "&picture=" + getIntent().getStringExtra("PICTURE");
            opts.query += "&id=" + getIntent().getStringExtra("ID");
            opts.query += "&channelId=" + userChannelId;
            mSocket = IO.socket("https://juanwalkie.herokuapp.com", opts);
        } catch (URISyntaxException e) {
            Log.w("ERROR CONNECT SOCKET", "onCreate: mSocket", e);
        }

        mSocket.connect();

        beep_sound = MediaPlayer.create(this, R.raw.radio_beep);
        stop_recording = MediaPlayer.create(this, R.raw.stop_recording);
        error_sound = MediaPlayer.create(this, R.raw.error_sound);

        mInputFile = getCacheDir().getAbsolutePath() + "/input.3gp";
        mOutputFile = getCacheDir().getAbsolutePath() + "/output.3gp";

        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        inputAudioQueue = new ArrayList();

        viewInjection();
        setNotification();

        record_audio.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                switch(motionEvent.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        bgShape.setColor(getResources().getColor(R.color.colorRecord));
                        vibrator.vibrate(50);
                        startRecording(text_log);
                        recordCurrentTime = new Date().getTime();
                        return true;
                    case MotionEvent.ACTION_UP:
                        if(new Date().getTime() - recordCurrentTime >= 1000){
                            stopRecording();
                            sendAudio();
                        }else{
                            mRecorder.release();
                            mRecorder = null;
                        }
                        stop_recording.start();
                        imgUserPic.setVisibility(View.INVISIBLE);
                        bgShape.setColor(getResources().getColor(R.color.colorPrimary));
                        text_log.setText("Hold down to talk");
                        vibrator.vibrate(50);
                        return true;
                    default:
                        return false;
                }
            }
        });

        mSocket.on("USERS", new Emitter.Listener() {
            @Override
            public void call(final Object... args) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        JSONObject data = (JSONObject) args[0];
                        String total_users;
                        String new_user;
                        String channelId;
                        try {
                            total_users = data.getString("total_users");
                            new_user = data.getString("new_user");
                            channelId = data.getString("channelId");
                        } catch (JSONException e) {
                            return;
                        }
                        if(channelId.equals(userChannelId)){
                            Log.i("TOTAL_USERS", total_users + "");
                            text_users.setText("Connected users: " + total_users);
                            if(!new_user.equals("nil")) text_log.setText(new_user + " has joined");
                            text_status.setText("Online");
                            color_status.setColor(getResources().getColor(R.color.online));
                        }
                    }
                });
            }
        });

        mSocket.on("NEW_AUDIO", new Emitter.Listener() {
            @Override
            public void call(final Object... args) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        JSONObject data = (JSONObject) args[0];
                        String user_name;
                        String user_pic;
                        String audioBase64;
                        try {
                            audioBase64 = data.getString("audio");
                            Log.i("NEW_AUDIO", audioBase64);
                            user_name = data.getString("name");
                            user_pic = data.getString("picture");
                            startPlaying(audioBase64, text_log, user_pic, user_name);
                        } catch (JSONException e) {
                            Log.w("ERROR", "run: getAUDIO", e);
                            return;
                        }
                    }
                });
            }
        });
    }

    private void viewInjection(){
        imgUserPic = findViewById(R.id.imgUserPic);
        text_status = findViewById(R.id.text_status);
        text_users = findViewById(R.id.text_users);
        text_log = findViewById(R.id.text_log);

        circle_status = findViewById(R.id.circle_status);
        color_status = (GradientDrawable)circle_status.getBackground();

        record_audio = findViewById(R.id.record_audio);
        bgShape = (GradientDrawable)record_audio.getBackground();

        color_status.setColor(getResources().getColor(R.color.offline));

    }

    private void startRecording(TextView text_log){
        setTalkerImg(getIntent().getStringExtra("PICTURE"));
        text_log.setText("You're talking");
        mRecorder = new MediaRecorder();
        mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        mRecorder.setOutputFile(mOutputFile);
        mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        try {
            mRecorder.prepare();
        } catch (IOException e) {
            Log.e("Media recoder", "prepare() failed");
        }

        mRecorder.start();
    }

    private void stopRecording() {
        mRecorder.stop();
        mRecorder.release();
        mRecorder = null;
    }

    private class InputFile {
        public String audioBase64;
        public TextView text_log;
        public String user_pic;
        public String user_name;

        public InputFile(String audioBase64, TextView text_log, String user_pic, String user_name){
            this.audioBase64 = audioBase64;
            this.text_log = text_log;
            this.user_pic = user_pic;
            this.user_name = user_name;
        }
    }

    private void initStartPlaying(String audioBase64, TextView text_log, String user_pic, String user_name){
        writeToFile(audioBase64, mInputFile);
        text_log.setText(user_name + " is talking");
        setTalkerImg(user_pic);
    }

    private void startPlaying(String audioBase64, final TextView text_log, String user_pic, String user_name) {
        if(mPlayer != null && mPlayer.isPlaying()){
            inputAudioQueue.add(new InputFile(audioBase64, text_log, user_pic, user_name));
        }else{
            initStartPlaying(audioBase64, text_log, user_pic, user_name);
            beep_sound.start();
            mPlayer = new MediaPlayer();
            try {
                mPlayer.setDataSource(mInputFile);
                mPlayer.prepare();
//            mPlayer.setVolume(100, 100);
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run(){
                        mPlayer.start();
                    }
                }, 800);
            } catch (IOException e) {
                Log.e("PLAYING RECORD", "prepare() failed");
            }
            mPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                public void onCompletion(MediaPlayer mp) {
                    text_log.setText("Hold down to talk");
                    mPlayer.release();
                    mPlayer = null;
                    imgUserPic.setVisibility(View.INVISIBLE);
                    if(!inputAudioQueue.isEmpty()){
                        InputFile inputFile = inputAudioQueue.get(0);
                        startPlaying(inputFile.audioBase64, inputFile.text_log, inputFile.user_pic, inputFile.user_name);
                        inputAudioQueue.remove(inputFile);
                    }
                }
            });
        }
    }

    private void sendAudio (){
        File file = new File(mOutputFile);
        try {
            byte[] FileBytes = FileUtils.readFileToByteArray(file);
            byte[] encodedBytes = Base64.encode(FileBytes, 0);
            String encodedString = new String(encodedBytes);
            mSocket.emit("USER_AUDIO", encodedString);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void writeToFile(String audioBase64, String fileName){
        byte[] data = Base64.decode(audioBase64, 0);
        FileOutputStream out;
        try {
            out = new FileOutputStream(fileName);
            out.write(data);
            out.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void returnMainActivity(){
        MainActivity.signOut();
        Intent intent = new Intent(getBaseContext(), MainActivity.class);
        startActivity(intent);
    }

    private void setNotification(){
        Intent resultIntent = new Intent(this, PublicChannel.class);

        PendingIntent resultPendingIntent = PendingIntent.getActivity(this, 0, resultIntent, 0);
        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(this, "com.juan.status_notification")
                        .setSmallIcon(R.drawable.ic_walkie_notification)
                        .setContentTitle(getIntent().getStringExtra("NAME").split(" ")[0] + ", you're online")
                        .setContentText("Tap to more options.")
                        .setOngoing(true)
                        .setContentIntent(resultPendingIntent);
        notificationManager.notify(notificationID, mBuilder.build());
    }

    private void setTalkerImg(String url){
        Picasso.with(this).load(url)
                .into(imgUserPic, new Callback() {
                    @Override
                    public void onSuccess() {
                        Bitmap imageBitmap = ((BitmapDrawable) imgUserPic.getDrawable()).getBitmap();
                        RoundedBitmapDrawable imageDrawable = RoundedBitmapDrawableFactory.create(getResources(), imageBitmap);
                        imageDrawable.setCircular(true);
                        imageDrawable.setCornerRadius(Math.max(imageBitmap.getWidth(), imageBitmap.getHeight()) / 2.0f);
                        imgUserPic.setImageDrawable(imageDrawable);
                        imgUserPic.setVisibility(View.VISIBLE);
                    }
                    @Override
                    public void onError() {
                        imgUserPic.setVisibility(View.INVISIBLE);
                    }
                });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        notificationManager.cancel(notificationID);
        mSocket.disconnect();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.sign_off_action:
                signOffDialog.show();
                return true;

            case R.id.add_channel_action:
                addChannelInput.setText("");
                addChannelDialog.show();
                return true;

            case R.id.change_channel_action:
                chChannelInput.setText("");
                changeChannelDialog.show();
                return true;

            case R.id.about_us_action:
                Log.i("A","about us");
                return true;

            default:
                // If we got here, the user's action was not recognized.
                // Invoke the superclass to handle it.
                return super.onOptionsItemSelected(item);
        }
    }

    private void setModals(){
        createSignOffModal();
        createAddChannelModal();
        createChangeChannelModal();
    }

    private void createSignOffModal(){
        builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.dialog_signoff_description)
                .setTitle(R.string.dialog_signoff_title);

        builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                mSocket.disconnect();
                returnMainActivity();
            }
        });
        builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                // User cancelled the dialog
            }
        });

        signOffDialog = builder.create();
    }

    private void createAddChannelModal(){
        builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.dialog_addchannel_description)
                .setTitle(R.string.dialog_addchannel_title);

        // Specify the type of input expected; this, for example, sets the input as a password, and will mask the text
        addChannelInput.setInputType(InputType.TYPE_CLASS_TEXT);
        builder.setView(addChannelInput);

        builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                String channelName = addChannelInput.getText().toString();
                Log.i("addc",channelName);
                if(!channelName.isEmpty()){

                }else{
                    Toast.makeText(getApplicationContext(), "You need to set a name",Toast.LENGTH_SHORT).show();
                }

            }
        });
        builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                // User cancelled the dialog

            }
        });

        addChannelDialog = builder.create();
    }

    private void createChangeChannelModal(){
        builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.dialog_changechannel_description)
                .setTitle(R.string.dialog_changechannel_title);

        // Specify the type of input expected; this, for example, sets the input as a password, and will mask the text
        chChannelInput.setInputType(InputType.TYPE_CLASS_TEXT);
        builder.setView(chChannelInput);

        builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                String channelToken = chChannelInput.getText().toString();
                Log.i("addc",channelToken);
                if(!channelToken.isEmpty()){

                    //handle the operation with API, BROKO

                }else{
                    Toast.makeText(getApplicationContext(), "You need to set the channel's id",Toast.LENGTH_SHORT).show();
                }

            }
        });
        builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                // User cancelled the dialog

            }
        });

        changeChannelDialog = builder.create();
    }

    /*
    private void isConnect(){
        new Thread(new Runnable() {
            public void run() {
                ConnectivityManager cm = (ConnectivityManager)getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
                NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
                boolean isConnected = activeNetwork != null && activeNetwork.isConnectedOrConnecting();
                if(isConnected){
                    if(!mSocket.connected()){
                        mSocket.connect();
                        text_status.setText("online");
                        color_status.setColor(getResources().getColor(R.color.online));
                    }
                }else{
                    mSocket.disconnect();
                    text_status.setText("offline");
                    color_status.setColor(getResources().getColor(R.color.offline));
                }
            }
        }).start();
    }
    */
}
