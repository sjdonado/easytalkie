package com.example.juan.juanwalkie;

import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;

public class MainActivity extends AppCompatActivity {

    private static final int MY_PERMISSIONS_REQUEST_RECORD_AUDIO = 200;
    private static final int RC_SIGN_IN = 234;
    private static boolean global_permissions = false;
    public static GoogleSignInClient mGoogleSignInClient;

    @Override
    protected void onStart() {
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        updateUI(account);
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
    }

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
    }

    private void handleSignInResult(Task<GoogleSignInAccount> completedTask) {
        try {
            GoogleSignInAccount account = completedTask.getResult(ApiException.class);
            updateUI(account);
        } catch (ApiException e) {
            Toast.makeText(this, "signInResult:failed code=" + e.getStatusCode(), Toast.LENGTH_LONG).show();
            updateUI(null);
        }
    }

    private void signIn() {
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, RC_SIGN_IN);
    }

    private void updateUI(GoogleSignInAccount account){
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
        mGoogleSignInClient.signOut();
    }

    //get local URI of local drawable
    private Uri getLocalDrawableUri(int drawableId){
        return Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE +
                "://" + getResources().getResourcePackageName(drawableId)
                + '/' + getResources().getResourceTypeName(drawableId) + '/' +
                getResources().getResourceEntryName(drawableId) );
    }
}
