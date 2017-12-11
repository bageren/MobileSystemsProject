package com.bakerapps.mobilesystemsproject;

import android.content.Context;
import android.content.SharedPreferences;
import android.renderscript.Sampler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Calendar;

public class ProfileActivity extends DrawerActivity {

    private SharedPreferences prefs;
    private DatabaseReference myDatabase;

    public static final String FIVE_KM_COMPLETION_DAY = "fiveKmCompletionDay";

    private TextView txtUserName;
    private TextView txtCompleted;
    private TextView txtPoints;
    private TextView txtDistance;
    private ProgressBar progressOneKm;
    private ProgressBar progressFiveKm;

    private String userName;
    private Double userDistance;

    private DatabaseReference userReference;

    private ValueEventListener listener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //setContentView(R.layout.activity_profile);
        LayoutInflater inflater = (LayoutInflater) this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View contentView = inflater.inflate(R.layout.activity_profile, null, false);
        mDrawerLayout.addView(contentView, 0);

        txtUserName = (TextView) findViewById(R.id.txtUsernameProfile);
        txtDistance = (TextView) findViewById(R.id.txtDistance);
        txtPoints = (TextView) findViewById(R.id.txtPoints);
        txtCompleted = (TextView) findViewById(R.id.txtCompleted);

        progressOneKm = (ProgressBar) findViewById(R.id.oneKmProgressBar);
        progressFiveKm = (ProgressBar) findViewById(R.id.fiveKmProgressBar);

        prefs = getSharedPreferences("BikeLife_Preferences", MODE_PRIVATE);


        myDatabase = FirebaseDatabase.getInstance().getReference();
        userName = prefs.getString("userName", "Unknown");
        userReference = myDatabase.child("users").child(userName.toLowerCase());
        txtUserName.setText(userName);

        listener = userReference.addValueEventListener(new ValueEventListener(){

            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                Long userScore = (Long) dataSnapshot.child("score").getValue();
                userDistance = Double.valueOf((String) dataSnapshot.child("totalDistance").getValue());

                txtPoints.setText(String.valueOf(userScore));
                txtDistance.setText(String.valueOf(round(userDistance, 2)));
                int increment = Math.abs((userDistance.intValue() %1000) - progressOneKm.getProgress());

                if((progressOneKm.getProgress() + increment) > progressOneKm.getMax()){
                    //give point
                    Long previousScore = (Long) dataSnapshot.child("score").getValue();
                    Long newScore = previousScore + 1;
                    userReference.child("score").setValue(newScore);
                    progressOneKm.setProgress(userDistance.intValue() % 1000);
                } else {
                    progressOneKm.incrementProgressBy(increment);
                }
                Calendar currentDate = Calendar.getInstance();
                if(prefs.getInt(FIVE_KM_COMPLETION_DAY, 0) != currentDate.get(Calendar.DAY_OF_YEAR)){
                    if(progressFiveKm.getVisibility() == View.GONE && txtCompleted.getVisibility() == View.VISIBLE){
                        progressFiveKm.setVisibility(View.VISIBLE);
                        txtCompleted.setVisibility(View.GONE);
                    }
                    increment = Math.abs((userDistance.intValue() % 5000) - progressFiveKm.getProgress());
                    if((progressFiveKm.getProgress() + increment) > progressFiveKm.getMax()){
                        //give point
                        Long previousScore = (Long) dataSnapshot.child("score").getValue();
                        Long newScore = previousScore + 10;
                        userReference.child("score").setValue(newScore);
                        prefs.edit().putInt(FIVE_KM_COMPLETION_DAY, currentDate.get(Calendar.DAY_OF_YEAR)).apply();
                        progressFiveKm.setProgress(userDistance.intValue() % 5000);
                    } else {
                        progressFiveKm.incrementProgressBy(increment);
                    }
                }
                else {
                    if(progressFiveKm.getVisibility() == View.VISIBLE && txtCompleted.getVisibility() == View.GONE){
                        progressFiveKm.setVisibility(View.GONE);
                        txtCompleted.setVisibility(View.VISIBLE);
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }

    public double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();
        return new BigDecimal(value).setScale(places, RoundingMode.HALF_UP).doubleValue();
    }

    @Override
    protected void onStop() {
        userReference.removeEventListener(listener);
        super.onStop();
    }


}
