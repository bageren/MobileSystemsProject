package com.bakerapps.mobilesystemsproject;

import android.Manifest;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
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
import com.google.android.gms.maps.model.BitmapDescriptor;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;

public class MapsActivity extends DrawerActivity implements OnMapReadyCallback, GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        LocationListener {

    // lOCATION Permissions
    private static final int REQUEST_LOCATIONS = 1;
    private static String[] PERMISSION_LOCATIONS = {Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION};
    public static final String ACTION_ACTIVITY_RECOGNIZED = "com.bakerapps.mobilesystemsproject.action.ACTIVITY_RECOGNIZED";
    public static final String EXTRA_IN_VEHICLE = "com.bakerapps.activityrecognition.extra.IN_VEHICLE";
    public static final String LAST_VISIT = "lastVisit";
    public static final String VISITED_DESTS_LIST = "visitedDestsList";

    private static final String REQUESTING_LOCATION_UPDATES_KEY = "requestingLocationUpdatesBoolean";

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

    private BroadcastReceiver activityReceiver;
    private PendingIntent pi;
    private boolean inVehicle = false;

    private ArrayList<Marker> dest;
    private ArrayList<String> visitedDests = new ArrayList<>();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //setContentView(R.layout.activity_maps);


        LayoutInflater inflater = (LayoutInflater) this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View contentView = inflater.inflate(R.layout.activity_maps, null, false);
        mDrawerLayout.addView(contentView, 0);

        prefs = getSharedPreferences("BikeLife_Preferences", MODE_PRIVATE);
        if (prefs.getString("userName", null) == null) {
            Intent intent = new Intent(this, WelcomeActivity.class);
            startActivity(intent);
        } else {
            Log.i("NAME", prefs.getString("userName", null));
        }


        myDatabase = FirebaseDatabase.getInstance().getReference();
        userName = prefs.getString("userName", null);

        if (savedInstanceState != null) {
            if (savedInstanceState.keySet().contains(REQUESTING_LOCATION_UPDATES_KEY)) {
                mRequestingLocationUpdates = savedInstanceState.getBoolean(REQUESTING_LOCATION_UPDATES_KEY);
            }

            if (savedInstanceState.keySet().contains(VISITED_DESTS_LIST)) {
                visitedDests = savedInstanceState.getStringArrayList(VISITED_DESTS_LIST);
            }

        }

        activityReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                boolean isInVehicle = intent.getBooleanExtra(EXTRA_IN_VEHICLE, false);
                if (isInVehicle == true) {
                    if (inVehicle == false) {
                        Toast.makeText(getApplicationContext(), "We've detected that you're in a vehicle. While this is true, any movement will not be counted.", Toast.LENGTH_LONG).show();
                    }
                }
                inVehicle = isInVehicle;
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_ACTIVITY_RECOGNIZED);

        registerReceiver(activityReceiver, filter);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        mLocationRequest = LocationRequest.create();
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        mLocationRequest.setInterval(15000);
        mLocationRequest.setFastestInterval(10000);

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
        setDestinations();

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    MapsActivity.this,
                    PERMISSION_LOCATIONS,
                    REQUEST_LOCATIONS
            );
            return;
        }

        fusedLocationClient.getLastLocation().addOnSuccessListener(new OnSuccessListener<Location>() {
            @Override
            public void onSuccess(Location location) {

                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(location.getLatitude(),location.getLongitude()), 15));



            }
        });

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
    public void setDestinations() {


        Marker desUni = mMap.addMarker(new MarkerOptions()
                .position(new LatLng(55.370675, 10.428067))
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
                .title("SDU"));

        Marker desBilka = mMap.addMarker(new MarkerOptions()
                .position(new LatLng(55.378227, 10.431294))
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
                .title("Bilka"));

        Marker desIkea = mMap.addMarker(new MarkerOptions()
                .position(new LatLng(55.380549, 10.429609))
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
                .title("IKEA"));


        Marker desElgiganten = mMap.addMarker(new MarkerOptions()
                .position(new LatLng(55.381910, 10.424708))
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
                .title("Elgiganten"));

        Marker desRC = mMap.addMarker(new MarkerOptions()
                .position(new LatLng(55.383743, 10.426433))
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
                .title("Rosengårdcentret"));


        Marker desTEK = mMap.addMarker(new MarkerOptions()
                .position(new LatLng(55.367259, 10.432076))
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
                .title("Det Tekniske Fakultet"));


        Marker desOCC = mMap.addMarker(new MarkerOptions()
                .position(new LatLng(55.371429, 10.449715))
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
                .title("Odense Congress Center"));



        dest = new ArrayList<>();
        dest.add(desUni);
        dest.add(desBilka);
        dest.add(desIkea);
        dest.add(desElgiganten);
        dest.add(desRC);
        dest.add(desTEK);
        dest.add(desOCC);

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
        ActivityRecognition.ActivityRecognitionApi.requestActivityUpdates( mGoogleApiClient, 25000, pi);
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    @Override
    public void onLocationChanged(final Location location) {
        //Log.i("LOCATION", "CHANGED");
        if(previousLocation == null) previousLocation = location;
        double latitude = location.getLatitude();
        double longitude = location.getLongitude();

        LatLng userPos = new LatLng(latitude,longitude);

        if(mCurrentLocationMarker!=null){
            mCurrentLocationMarker.setPosition(userPos);
        } else {
            mCurrentLocationMarker = mMap.addMarker(new MarkerOptions()
                    .position(userPos)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
                    .title("Current location"));
        }
      //  mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(userPos, 15)); //Slet: Den bliver centreret til current location


        final DatabaseReference userReference = myDatabase.child("users").child(userName.toLowerCase());

        float[]results = new float[1];

        Calendar currentDate = Calendar.getInstance();

        if(prefs.getInt(LAST_VISIT,0) != currentDate.get(Calendar.DAY_OF_YEAR)){

            prefs.edit().putInt(LAST_VISIT, currentDate.get(Calendar.DAY_OF_YEAR)).apply();
            visitedDests.clear();
            for(Marker marker : dest){

                marker.setIcon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN));


            }

        }

        for(final Marker marker : dest){
            if(!visitedDests.contains(dest.toString())){

                location.distanceBetween(location.getLatitude(),location.getLongitude(),marker.getPosition().latitude, marker.getPosition().longitude,results);
                if(results[0]<=10) {

                    userReference.addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(DataSnapshot dataSnapshot) {

                            Long previousScore = (Long) dataSnapshot.child("score").getValue();
                            Long newScore = previousScore + 2;
                            userReference.child("score").setValue(newScore);

                            visitedDests.add(marker.getPosition().toString());
                            marker.setIcon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED));


                        }

                        @Override
                        public void onCancelled(DatabaseError databaseError) {
                        }
                    });

                }

            }

        }


        /* Calculate distance approximately every 30 seconds */
        if((location.getElapsedRealtimeNanos()-previousLocation.getElapsedRealtimeNanos()) / 1000000000 >= 30){

                final double distanceTravelled = previousLocation.distanceTo(location);

            previousLocation = location;
            if(!inVehicle) {

                if(distanceTravelled >= 5){
                    userReference.addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(DataSnapshot dataSnapshot) {
                            Double previousDistance = Double.valueOf((String) dataSnapshot.child("totalDistance").getValue());
                            userReference.child("totalDistance").setValue(String.valueOf(previousDistance + distanceTravelled));
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
        ActivityRecognition.ActivityRecognitionApi.removeActivityUpdates(mGoogleApiClient, pi);
        mGoogleApiClient.disconnect();
        unregisterReceiver(activityReceiver);
        pi.cancel();
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState){
        savedInstanceState.putBoolean(REQUESTING_LOCATION_UPDATES_KEY, mRequestingLocationUpdates);
        savedInstanceState.putStringArrayList(VISITED_DESTS_LIST,visitedDests);
        super.onSaveInstanceState(savedInstanceState);
    }
}
