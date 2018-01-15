package com.example.juan.juanwalkie;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.support.v4.graphics.drawable.RoundedBitmapDrawable;
import android.support.v4.graphics.drawable.RoundedBitmapDrawableFactory;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;

import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;

public class AboutUs extends AppCompatActivity {

    private ImageView imgJuan, imgBrian;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setFullScreen();
        this.setContentView(R.layout.activity_about_us);
        viewInjection();
        setDeveloperPicture(getProfilePicUri(R.drawable.juan_profile_pic),imgJuan);
        setDeveloperPicture(getProfilePicUri(R.drawable.brian_profile_pic),imgBrian);
    }

    //recreate full screen activity
    private void setFullScreen(){
        getSupportActionBar().hide();
        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }

    public void returnButton(View view){finish();}

    //Inject all the views and a
    private void viewInjection(){
        this.imgBrian = findViewById(R.id.imgBrian);
        this.imgJuan = findViewById(R.id.imgJuan);
    }

    //get local URI of developer's profile picture
    private Uri getProfilePicUri(int drawableId){
        return Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE +
                "://" + getResources().getResourcePackageName(drawableId)
                + '/' + getResources().getResourceTypeName(drawableId) + '/' +
                getResources().getResourceEntryName(drawableId) );
    }

    // set the picuter on a circular way
    private void setDeveloperPicture(Uri uri, ImageView imgView){
        final ImageView imageView = imgView;
        Picasso.with(this).load(uri)
                .into(imgView, new Callback() {
                    @Override
                    public void onSuccess() {
                        Bitmap imageBitmap = ((BitmapDrawable) imageView.getDrawable()).getBitmap();
                        RoundedBitmapDrawable imageDrawable = RoundedBitmapDrawableFactory.create(getResources(), imageBitmap);
                        imageDrawable.setCircular(true);
                        imageDrawable.setCornerRadius(Math.max(imageBitmap.getWidth(), imageBitmap.getHeight()) / 2.0f);
                        imageView.setImageDrawable(imageDrawable);
                        imageView.setVisibility(View.VISIBLE);
                    }
                    @Override
                    public void onError() {

                    }
                });
    }
}
