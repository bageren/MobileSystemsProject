package com.bakerapps.mobilesystemsproject;

import android.*;
import android.Manifest;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class MapsActivity extends AppCompatActivity implements OnMapReadyCallback, GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        LocationListener {

    // lOCATION Permissions
    private static final int REQUEST_LOCATIONS = 1;
    private static String[] PERMISSION_LOCATIONS = {Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION};
    public static final String ACTION_ACTIVITY_RECOGNIZED = "com.bakerapps.mobilesystemsproject.action.ACTIVITY_RECOGNIZED";
    public static final String EXTRA_IN_VEHICLE = "com.bakerapps.activityrecognition.extra.IN_VEHICLE";

    private static final String REQUESTING_LOCATION_UPDATES_KEY = "requestingLocationUpdatesBoolean";
    private static final String TOTAL_DISTANCE_KEY = "totalDistanceDouble";

    private GoogleMap mMap;
    private GoogleApiClient mGoogleApiClient;
    private LocationRequest mLocationRequest;
    private Marker mCurrentLocationMarker;
    private boolean mRequestingLocationUpdates = false;
    private SharedPreferences prefs;
    private FusedLocationProviderClient fusedLocationClient;
    private Location previousLocation;
    private DatabaseReference myDatabase;
    private String userName;
    private double totalDistance = 0;

    private DrawerLayout mDrawerLayout;
    private ActionBarDrawerToggle mDrawerToggle;
    private ListView mDrawerList;

    private BroadcastReceiver activityReceiver;
    private PendingIntent pi;
    private boolean inVehicle = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        prefs = getSharedPreferences("BikeLife_Preferences", MODE_PRIVATE);
        if (prefs.getString("userName", null) == null) {
            Intent intent = new Intent(this, WelcomeActivity.class);
            startActivity(intent);
        } else {
            Log.i("NAME", prefs.getString("userName", null));
        }

        myDatabase = FirebaseDatabase.getInstance().getReference();
        userName = prefs.getString("userName", null);

        if(savedInstanceState != null){
            if(savedInstanceState.keySet().contains(REQUESTING_LOCATION_UPDATES_KEY)){
                mRequestingLocationUpdates = savedInstanceState.getBoolean(REQUESTING_LOCATION_UPDATES_KEY);
                totalDistance = savedInstanceState.getDouble(TOTAL_DISTANCE_KEY);
            }
        }

        mDrawerList = (ListView) findViewById(R.id.left_drawer);
        mDrawerList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                selectItem(i);
            }

            private void selectItem(int position) {
                switch (position) {
                    case 1:
                        Intent leaderboardIntent = new Intent(MapsActivity.this, LeaderboardActivity.class);
                        startActivity(leaderboardIntent);
                }
            }
        });
        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawerLayout);
        mDrawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout, R.string.drawer_open, R.string.drawer_close) {
            public void onDrawerClosed(View view) {
                super.onDrawerClosed(view);
                getSupportActionBar().setTitle("Map");
                invalidateOptionsMenu();
            }

            public void onDrawerOpened(View view) {
                super.onDrawerOpened(view);
                getSupportActionBar().setTitle("Navigate");
                invalidateOptionsMenu();
            }
        };

        mDrawerLayout.addDrawerListener(mDrawerToggle);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);

        activityReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                inVehicle = intent.getBooleanExtra(EXTRA_IN_VEHICLE, false);
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction( ACTION_ACTIVITY_RECOGNIZED );

        registerReceiver(activityReceiver, filter);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        mLocationRequest = LocationRequest.create();
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        mLocationRequest.setInterval(10000);
        mLocationRequest.setFastestInterval(5000);

        buildGoogleApiClient();
        mGoogleApiClient.connect();

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
    }







    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        setDestination();
//        try {
//            fusedLocationClient.getLastLocation().addOnSuccessListener(new OnSuccessListener<Location>() {
//                @Override
//                public void onSuccess(Location location) {
//                    if (location != null) {
//                        LatLng userPos = new LatLng(location.getLatitude(), location.getLongitude());
//                    } else {
//                        Toast.makeText(MapsActivity.this, "No location available", Toast.LENGTH_SHORT).show();
//
//                    }
//                }
//            });
//        } catch (SecurityException se) {
//            Toast.makeText(MapsActivity.this, "Permission required", Toast.LENGTH_SHORT).show();
//        }
//
//        // Add a marker in Sydney and move the camera
//        LatLng sydney = new LatLng(-34, 151);
//        mMap.addMarker(new MarkerOptions().position(sydney).title("Marker in Sydney"));
//        mMap.moveCamera(CameraUpdateFactory.newLatLng(sydney));
    }



    // SET DESTINATION
    public void setDestination() {


        LatLng desUni = new LatLng(55.370675, 10.428067);
        mCurrentLocationMarker = mMap.addMarker(new MarkerOptions()
                .position(desUni)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
                .title("SDU"));

        LatLng desBilka = new LatLng(55.378227, 10.431294);
        mCurrentLocationMarker = mMap.addMarker(new MarkerOptions()
                .position(desBilka)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
                .title("Bilka"));


        LatLng desIkea = new LatLng(55.380549, 10.429609);
        mCurrentLocationMarker = mMap.addMarker(new MarkerOptions()
                .position(desIkea)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
                .title("IKEA"));


        LatLng desElgiganten = new LatLng(55.381910, 10.424708);
        mCurrentLocationMarker = mMap.addMarker(new MarkerOptions()
                .position(desElgiganten)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
                .title("Elgiganten"));

        LatLng desRC = new LatLng(55.383743, 10.426433);
        mCurrentLocationMarker = mMap.addMarker(new MarkerOptions()
                .position(desRC)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
                .title("RosengårdCentret"));


        LatLng desTEK = new LatLng(55.367259, 10.432076);
        mCurrentLocationMarker = mMap.addMarker(new MarkerOptions()
                .position(desTEK)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
                .title("Det Tekniske Fakultet"));


        LatLng desOCC = new LatLng(55.371429, 10.449715);
        mCurrentLocationMarker = mMap.addMarker(new MarkerOptions()
                .position(desOCC)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
                .title("Odensec Congres Center"));

    }




    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        // Sync the toggle state after onRestoreInstanceState has occurred.
        mDrawerToggle.syncState();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mDrawerToggle.onConfigurationChanged(newConfig);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Pass the event to ActionBarDrawerToggle, if it returns
        // true, then it has handled the app icon touch event
        if (mDrawerToggle.onOptionsItemSelected(item)) {
            return true;
        }
        // Handle your other action bar items...

        return super.onOptionsItemSelected(item);
    }

    protected synchronized void buildGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .addApi(ActivityRecognition.API)
                .build();
    }

    protected void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    MapsActivity.this,
                    PERMISSION_LOCATIONS,
                    REQUEST_LOCATIONS
            );
        } else{
            LocationServices.FusedLocationApi.requestLocationUpdates(
                    mGoogleApiClient, mLocationRequest, this);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case REQUEST_LOCATIONS: {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    // permission was granted, yay!
                    startLocationUpdates();

                } else {

                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                }
                return;
            }

            // other 'switch' lines to check for other
            // permissions this app might request
        }
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        if(!mRequestingLocationUpdates){
            startLocationUpdates();
        }
        Intent i = new Intent(this,ActivityRecognitionService.class);
        pi = PendingIntent.getService(this, 0, i, PendingIntent.FLAG_UPDATE_CURRENT);
        ActivityRecognition.ActivityRecognitionApi.requestActivityUpdates( mGoogleApiClient, 10000, pi);
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    @Override
    public void onLocationChanged(final Location location) {
        Log.i("LOCATION", "CHANGED");
        if(previousLocation == null) previousLocation = location;
        double latitude = location.getLatitude();
        double longitude = location.getLongitude();

        LatLng userPos = new LatLng(latitude,longitude);

        if(mCurrentLocationMarker!=null){
            mCurrentLocationMarker.setPosition(userPos);
        } else {
            mCurrentLocationMarker = mMap.addMarker(new MarkerOptions()
                    .position(userPos)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                    .title("Current location"));
        }
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(userPos, 15));

        /* Calculate distance approximately every 15 seconds */
        if((location.getElapsedRealtimeNanos()-previousLocation.getElapsedRealtimeNanos()) / 1000000000 > 15){
            if(!inVehicle){
                final double distanceTravelled = previousLocation.distanceTo(location);
                totalDistance += distanceTravelled;
                previousLocation = location;
                final DatabaseReference userReference = myDatabase.child("users").child(userName.toLowerCase());

                if(totalDistance > 1000) {
                    userReference.addListenerForSingleValueEvent(new ValueEventListener() {

                        @Override
                        public void onDataChange(DataSnapshot dataSnapshot) {
                            Long previousScore = (Long) dataSnapshot.child("score").getValue();
                            Long newScore = previousScore + 1;
                            userReference.child("score").setValue(newScore);
                            totalDistance = totalDistance - 1000;
                        }

                        @Override
                        public void onCancelled(DatabaseError databaseError) {

                        }
                    });
                }

            if(totalDistance == 5000) {
                userReference.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        Long previousScore = (Long) dataSnapshot.child("score").getValue();
                        Long newScore = previousScore+10;
                        userReference.child("score").setValue(newScore);

                        //Må kun udføres en gang dagligt!!

                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {

                    }
                });
            }
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
//        if(mRequestingLocationUpdates){
//            stopLocationUpdates();
//        }
    }

    protected void stopLocationUpdates() {
        LocationServices.FusedLocationApi.removeLocationUpdates(
                mGoogleApiClient, this);
    }

    @Override
    public void onResume() {
        super.onResume();
//        if (mGoogleApiClient.isConnected() && !mRequestingLocationUpdates) {
//            startLocationUpdates();
//        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopLocationUpdates();
        mGoogleApiClient.disconnect();
        unregisterReceiver(activityReceiver);
        ActivityRecognition.ActivityRecognitionApi.removeActivityUpdates(mGoogleApiClient, pi);
        pi.cancel();
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState){
        savedInstanceState.putBoolean(REQUESTING_LOCATION_UPDATES_KEY, mRequestingLocationUpdates);
        savedInstanceState.putDouble(TOTAL_DISTANCE_KEY, totalDistance);
        super.onSaveInstanceState(savedInstanceState);
    }
}
