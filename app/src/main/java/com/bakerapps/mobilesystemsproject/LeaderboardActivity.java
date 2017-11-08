package com.bakerapps.mobilesystemsproject;

import android.content.SharedPreferences;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;

public class LeaderboardActivity extends AppCompatActivity {

    private TextView txtMyScore;
    private ListView listLeaderboard;
    private ArrayList<String> scores;
    private DatabaseReference myDatabase;
    private int userScore;
    private String userName;
    private ProgressBar loadingBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_leaderboard);


        SharedPreferences prefs = getSharedPreferences("BikeLife_Preferences", MODE_PRIVATE);

        myDatabase = FirebaseDatabase.getInstance().getReference();

        txtMyScore = (TextView) findViewById(R.id.myScore);
        listLeaderboard = (ListView) findViewById(R.id.leaderboard);
        loadingBar = (ProgressBar) findViewById(R.id.progressBar);

        userName = prefs.getString("userName", "Anonymous");
        scores = new ArrayList<>();




        myDatabase.child("users").child(userName.toLowerCase()).addValueEventListener(new ValueEventListener(){

            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                userScore = ((Long) dataSnapshot.child("score").getValue()).intValue();
                txtMyScore.setText(userName + ": " + userScore);
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });



        Query leaderboardRef = myDatabase.child("users").orderByValue().limitToLast(10);

        leaderboardRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                scores.clear();
                for(DataSnapshot user : dataSnapshot.getChildren()){
                    scores.add(user.child("displayName").getValue() + ": " + user.child("score").getValue());
                }
                Collections.reverse(scores);
                ArrayAdapter leaderboardAdapter = new ArrayAdapter(LeaderboardActivity.this, android.R.layout.simple_list_item_1, scores);
                listLeaderboard.setAdapter(leaderboardAdapter);
                loadingBar.setVisibility(View.GONE);
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });


    }

    public void goBack(View view){
        finish();
    }
}
