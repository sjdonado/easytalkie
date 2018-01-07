package com.example.juan.juanwalkie;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
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
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.github.nkzawa.emitter.Emitter;
import com.github.nkzawa.socketio.client.IO;
import com.github.nkzawa.socketio.client.Socket;
import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;

import org.apache.commons.io.FileUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;

public class PublicRoom extends AppCompatActivity{

    private MediaPlayer mPlayer;
    private MediaPlayer beep_sound;
    private MediaPlayer start_recording;
    private MediaPlayer error_sound;
    private MediaRecorder mRecorder;
    private String mFileName;
    private TextView text_status;
    private GradientDrawable color_status;
    private NotificationManager notificationManager;
    private Vibrator vibrator;
    private boolean recording = false;
    private ImageView imgUserPic;
    private Socket mSocket;
    private String thisUserPicUrl;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_public_room);

        try {
            IO.Options opts = new IO.Options();
            opts.query = "name=" + getIntent().getStringExtra("NAME");
            opts.query += "&picture=" + thisUserPicUrl;
            mSocket = IO.socket("https://juanwalkie.herokuapp.com", opts);
        } catch (URISyntaxException e) {
            Log.w("ERROR CONNECT SOCKET", "onCreate: mSocket", e);
        }

        setTitle("Public room");

        mSocket.connect();

        beep_sound = MediaPlayer.create(this, R.raw.radio_beep);
        start_recording = MediaPlayer.create(this, R.raw.start_recording);
        error_sound = MediaPlayer.create(this, R.raw.error_sound);

        mFileName = getCacheDir().getAbsolutePath();
        mFileName += "/juanwalkie.3gp";

        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        text_status = findViewById(R.id.text_status);
        final TextView text_users = findViewById(R.id.text_users);
        final TextView text_log = findViewById(R.id.text_log);

        View circle_status = findViewById(R.id.circle_status);
        color_status = (GradientDrawable)circle_status.getBackground();

        View record_audio = findViewById(R.id.record_audio);
        final GradientDrawable bgShape = (GradientDrawable)record_audio.getBackground();

        record_audio.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                switch(motionEvent.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        bgShape.setColor(getResources().getColor(R.color.colorRecord));
                        vibrator.vibrate(50);
                        startRecording();
                        return true;
                    case MotionEvent.ACTION_UP:
                        stopRecording();
                        sendAudio();
                        bgShape.setColor(getResources().getColor(R.color.colorPrimary));
                        vibrator.vibrate(50);
                        return true;
                    default:
                        return false;
                }
            }
        });

        findViewById(R.id.sign_out).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mSocket.disconnect();
                returnMainActivity();
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
                        try {
                            total_users = data.getString("total_users");
                            new_user = data.getString("new_user");
                        } catch (JSONException e) {
                            return;
                        }
                        Log.i("TOTAL_USERS", total_users + "");
                        text_users.setText("Connected users: " + total_users);
                        if(!new_user.equals("nil")) text_log.setText(new_user + " is joined");
                        text_status.setText("Online");
                        color_status.setColor(getResources().getColor(R.color.online));
                    }
                });
            }
        });

        color_status.setColor(getResources().getColor(R.color.offline));

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
                        byte[] decodedBytes;
                        try {
                            audioBase64 = data.getString("audio");
                            Log.i("NEW_AUDIO", audioBase64);
                            user_name = data.getString("name");
                            user_pic = data.getString("picture");
                            decodedBytes = Base64.decode(audioBase64, 0);
                            try {
                                writeToFile(decodedBytes, mFileName);
                                text_log.setText(user_name + " is talking");
                                setTalkerImg(user_pic);
                                startPlaying(mFileName, text_log);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        } catch (JSONException e) {
                            Log.w("ERROR", "run: getAUDIO", e);
                            return;
                        }
                    }
                });
            }
        });

        setNotification();
        thisUserPicUrl = getIntent().getStringExtra("PICTURE");
        viewInjection();
    }

    private void viewInjection(){
        imgUserPic = findViewById(R.id.imgUserPic);
    }

    private void startRecording(){
        setTalkerImg(thisUserPicUrl);
        start_recording.start();
        mRecorder = new MediaRecorder();
        mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        mRecorder.setOutputFile(mFileName);
        mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        try {
            mRecorder.prepare();
        } catch (IOException e) {
            Log.e("Media recoder", "prepare() failed");
        }

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run(){
                mRecorder.start();
            }
        }, 4 * 100);
    }

    private void stopRecording() {
        imgUserPic.setVisibility(View.INVISIBLE);
        mRecorder.stop();
        mRecorder.release();
        mRecorder = null;
    }

    private void startPlaying(String mFileName, final TextView text_log) {
        beep_sound.start();
        mPlayer = new MediaPlayer();
        try {
            mPlayer.setDataSource(mFileName);
            mPlayer.prepare();
            mPlayer.setVolume(100, 100);
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run(){
                    mPlayer.start();
                }
            }, 8 * 100);
        } catch (IOException e) {
            Log.e("PLAYING RECORD", "prepare() failed");
        }
        mPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            public void onCompletion(MediaPlayer mp) {
                text_log.setText("Hold down to talk");
                imgUserPic.setVisibility(View.INVISIBLE);
            }
        });
    }

    private void sendAudio (){
        File file = new File(mFileName);
        try {
            byte[] FileBytes = FileUtils.readFileToByteArray(file);
            byte[] encodedBytes = Base64.encode(FileBytes, 0);
            String encodedString = new String(encodedBytes);
            mSocket.emit("USER_AUDIO", encodedString);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void writeToFile(byte[] data, String fileName) throws IOException{
        FileOutputStream out = new FileOutputStream(fileName);
        out.write(data);
        out.close();
    }

    private void returnMainActivity(){
        MainActivity.signOut();
        Intent intent = new Intent(getBaseContext(), MainActivity.class);
        startActivity(intent);
    }

    private void setNotification(){
        Intent resultIntent = new Intent(this, PublicRoom.class);

        PendingIntent resultPendingIntent = PendingIntent.getActivity(this, 0, resultIntent, 0);
        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(this, "com.juan.status_notification")
                        .setSmallIcon(R.drawable.ic_walkie_notification)
                        .setContentTitle(getIntent().getStringExtra("NAME").split(" ")[0] + ", you're online")
                        .setContentText("Tap to more options.")
                        .setOngoing(true)
                        .setContentIntent(resultPendingIntent);
        notificationManager.notify(001, mBuilder.build());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        notificationManager.cancel(001);
        mSocket.disconnect();
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
