package com.example.juan.juanwalkie;

import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.facebook.AccessToken;
import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.GraphRequest;
import com.facebook.GraphResponse;
import com.facebook.login.LoginManager;
import com.facebook.login.LoginResult;
import com.facebook.login.widget.LoginButton;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;

import com.facebook.FacebookSdk;
import com.facebook.appevents.AppEventsLogger;

import org.json.JSONException;
import org.json.JSONObject;


public class MainActivity extends AppCompatActivity {

    private static final int MY_PERMISSIONS_REQUEST_RECORD_AUDIO = 200;
    private static final int RC_SIGN_IN = 234;
    private static boolean global_permissions = false;
    public static GoogleSignInClient mGoogleSignInClient;

    private LoginButton fbButton;
    private CallbackManager callbackManager;

    private final String FACEBOOK_USER_PIC_URL = "https://graph.facebook.com/userid/picture?type=large";

    @Override
    protected void onStart() {
        getSupportActionBar().hide();
        updateUI();
        super.onStart();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        requestPermissions();

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .build();

        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        findViewById(R.id.sign_in_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(global_permissions){
                    signIn();
                }else{
                    Snackbar.make(view, "Accept permissions to continue", Snackbar.LENGTH_LONG).show();
                }
            }
        });

        FacebookSdk.sdkInitialize(getApplicationContext());
        AppEventsLogger.activateApp(this);

        viewInjection();

        callbackManager = CallbackManager.Factory.create();

        // Callback registration
        fbButton.registerCallback(callbackManager, new FacebookCallback<LoginResult>() {
            @Override
            public void onSuccess(LoginResult loginResult) {
                handleFacebookLogin(AccessToken.getCurrentAccessToken());
            }

            @Override
            public void onCancel() {
                // App code
            }

            @Override
            public void onError(FacebookException e) {
                Toast.makeText(getApplicationContext(), "signInResult:failed code=" +
                        e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void viewInjection(){
        fbButton = (LoginButton) findViewById(R.id.facebook_button);
        fbButton.setReadPermissions("email", "public_profile");
    }
/*
    private void handleFacebookLoginProgress(final Context context,final Runnable runnable) {
        final ProgressDialog ringProgressDialog = ProgressDialog.show(context, "Title ...", "Info ...", true);
        //you usually don't want the user to stop the current process, and this will make sure of that
        ringProgressDialog.setCancelable(false);
        Thread th = new Thread(new Runnable() {
            @Override
            public void run() {
                runnable.run();
                //after he logic is done, close the progress dialog
                Log.i("s","ss");
                handleFacebookLogin(AccessToken.getCurrentAccessToken());
                Log.i("s","ss1");
                ringProgressDialog.dismiss();
            }
        });
        th.start();
    }
    */

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Result returned from launching the Intent from GoogleSignInClient.getSignInIntent(...);
        if (requestCode == RC_SIGN_IN) {
            // The Task returned from this call is always completed, no need to attach
            // a listener.
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            handleSignInResult(task);
        }

        callbackManager.onActivityResult(requestCode, resultCode, data);
    }

    private void handleSignInResult(Task<GoogleSignInAccount> completedTask) {
        try {
            GoogleSignInAccount account = completedTask.getResult(ApiException.class);
            handleGoogleLogin(account);
        } catch (ApiException e) {
            Toast.makeText(this, "signInResult:failed code=" + e.getStatusCode(), Toast.LENGTH_LONG).show();
            handleGoogleLogin(null);
        }
    }

    private void signIn() {
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, RC_SIGN_IN);
    }

    private void updateUI(){
        if(GoogleSignIn.getLastSignedInAccount(this)!=null){
            handleGoogleLogin(GoogleSignIn.getLastSignedInAccount(this));
        }else if (AccessToken.getCurrentAccessToken()!=null){
            handleFacebookLogin(AccessToken.getCurrentAccessToken());
        }
    }

    private void handleGoogleLogin(GoogleSignInAccount account){
        if(account != null){
            Intent intent = new Intent(getBaseContext(), Channel.class);
            intent.putExtra("ID", account.getId());
            intent.putExtra("NAME", account.getDisplayName());
            String urlPhoto = (account.getPhotoUrl()!=null) ?
                    account.getPhotoUrl().toString().replace("96","140") :
                    getLocalDrawableUri(R.drawable.ic_account_circle_green_20dp).toString() ;
            intent.putExtra("PICTURE", urlPhoto);
            startActivity(intent);
        }
    }

    private void handleFacebookLogin(final AccessToken token){
        if(token!=null){
            GraphRequest request = GraphRequest.newMeRequest(
                    token,
                    new GraphRequest.GraphJSONObjectCallback() {
                        @Override
                        public void onCompleted(
                                JSONObject object,
                                GraphResponse response) {
                            try {

                                String name = response.getJSONObject().getString("first_name")
                                        +" "+ response.getJSONObject().getString("last_name");
                                Intent intent = new Intent(getBaseContext(), Channel.class);
                                intent.putExtra("ID", token.getUserId());
                                intent.putExtra("NAME", name);
                                intent.putExtra("PICTURE",FACEBOOK_USER_PIC_URL.replace(
                                        "userid",token.getUserId()));
                                startActivity(intent);
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }

                        }
                    });
            Bundle parameters = new Bundle();
            parameters.putString("fields", "first_name,last_name");
            request.setParameters(parameters);
            request.executeAsync();
        }

    }

    private void requestPermissions() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            global_permissions = true;
        } else {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.RECORD_AUDIO}, MY_PERMISSIONS_REQUEST_RECORD_AUDIO);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_RECORD_AUDIO: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    requestPermissions();
                }
                return;
            }
        }
    }

    public static void signOut() {
        if(AccessToken.getCurrentAccessToken()!=null){
            LoginManager.getInstance().logOut();
        }else{
            mGoogleSignInClient.signOut();
        }
    }

    //get an URI of local drawable
    private Uri getLocalDrawableUri(int drawableId){
        return Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE +
                "://" + getResources().getResourcePackageName(drawableId)
                + '/' + getResources().getResourceTypeName(drawableId) + '/' +
                getResources().getResourceEntryName(drawableId) );
    }
}
