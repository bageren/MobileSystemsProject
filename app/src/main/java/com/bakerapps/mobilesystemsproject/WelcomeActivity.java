package com.bakerapps.mobilesystemsproject;

import android.content.SharedPreferences;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class WelcomeActivity extends AppCompatActivity {

    private DatabaseReference myDatabase;
    private SharedPreferences.Editor editor;
    private EditText username;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome);

        SharedPreferences prefs = getSharedPreferences("BikeLife_Preferences", MODE_PRIVATE);
        editor = prefs.edit();

        myDatabase = FirebaseDatabase.getInstance().getReference();

        username = (EditText) findViewById(R.id.txtUserName);
    }

    public void submitOnClick(View view){
        final String lowerCaseUserName = username.getText().toString().toLowerCase();
        final String displayUserName = username.getText().toString();

        myDatabase.child("users").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if(dataSnapshot.hasChild(lowerCaseUserName)){
                    //error
                    Toast errorToast = Toast.makeText(getApplicationContext(), "Username already exists, please try another", Toast.LENGTH_SHORT);
                    errorToast.show();
                } else{
                    myDatabase.child("users").child(lowerCaseUserName).child("displayName").setValue(displayUserName);
                    myDatabase.child("users").child(lowerCaseUserName).child("score").setValue(0);
                    Toast successToast = Toast.makeText(getApplicationContext(), "Registration completed", Toast.LENGTH_SHORT);
                    successToast.show();
                    editor.putString("userName", displayUserName);
                    editor.apply();
                    finish();
                }

            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });

    }
}
