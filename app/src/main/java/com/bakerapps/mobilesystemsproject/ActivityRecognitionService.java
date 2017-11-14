package com.bakerapps.mobilesystemsproject;

import android.app.IntentService;
import android.content.Intent;
import android.content.Context;

import com.google.android.gms.location.ActivityRecognitionResult;
import com.google.android.gms.location.DetectedActivity;

import java.util.Calendar;

/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 * <p>
 * TODO: Customize class - update intent actions, extra parameters and static
 * helper methods.
 */
public class ActivityRecognitionService extends IntentService {

    private Intent activityIntent;

    public ActivityRecognitionService() {
        super("ActivityRecognitionService");
        activityIntent = new Intent();
        activityIntent.setAction(MapsActivity.ACTION_ACTIVITY_RECOGNIZED);

    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            if(ActivityRecognitionResult.hasResult(intent)) {
                ActivityRecognitionResult result = ActivityRecognitionResult.extractResult(intent);
                handleDetectedActivities(result.getMostProbableActivity());
            }
        }
    }
    private void handleDetectedActivities(DetectedActivity probableActivity) {
        switch( probableActivity.getType() ) {
            case DetectedActivity.IN_VEHICLE: {
                activityIntent.putExtra(MapsActivity.EXTRA_IN_VEHICLE, true);
                break;
            }
            case DetectedActivity.ON_BICYCLE: case DetectedActivity.ON_FOOT:  case DetectedActivity.RUNNING: case DetectedActivity.STILL:
            case DetectedActivity.TILTING: case DetectedActivity.WALKING: case DetectedActivity.UNKNOWN: {
                activityIntent.putExtra(MapsActivity.EXTRA_IN_VEHICLE, false);
                break;
            }
        }
        sendBroadcast(activityIntent);
    }
}
