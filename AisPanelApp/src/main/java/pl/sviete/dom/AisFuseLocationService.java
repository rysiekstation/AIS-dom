package pl.sviete.dom;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import android.text.Html;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;


public class AisFuseLocationService extends Service {
    private static final String TAG = "AisFuseLocationService";

    private static final int UPDATE_INTERVAL_IN_MILLISECONDS = 30000; // 30 sec
    private static final long FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS = UPDATE_INTERVAL_IN_MILLISECONDS / 2;
    private static final float LOCATION_DISTANCE = 10f;

    // fuse
    private FusedLocationProviderClient mFusedLocationClient;
    private LocationCallback mLocationCallback;
    private Location mCurrentLocation;
    private LocationRequest mLocationRequest;

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel notificationChannel = new NotificationChannel(
                    AisCoreUtils.AIS_LOCATION_CHANNEL_ID,
                    "AI-Speaker Location",
                    NotificationManager.IMPORTANCE_LOW
            );
            notificationChannel.setShowBadge(true);

            NotificationManager manager = getSystemService(NotificationManager.class);
            assert manager != null;
            manager.createNotificationChannel(notificationChannel);
        }
    }

    private Notification getNotification() {
        // Go to frame
        PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), 0,
                new Intent(getApplicationContext(), BrowserActivityNative.class), 0);


        // Report action button - button to report location now
        // TODO... maybe

        // Exit action
        Intent exitIntent = new Intent(getApplicationContext(), BrowserActivityNative.class);
        exitIntent.setAction("exit_location_service");
        exitIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent exitPendingIntent = PendingIntent.getActivity(getApplicationContext(), 0, exitIntent, 0);
        NotificationCompat.Action exitAction = new NotificationCompat.Action.Builder(R.drawable.ic_app_exit, "STOP", exitPendingIntent).build();

        CharSequence contentTitle = getString(R.string.gps_loc_detected) + ": " + AisCoreUtils.GPS_SERVICE_LOCATIONS_DETECTED;
        contentTitle = contentTitle + ", " + getString(R.string.gps_loc_sent) + ": " + AisCoreUtils.GPS_SERVICE_LOCATIONS_SENT;;
        CharSequence contentText = getString(R.string.app_name);
        if (mCurrentLocation != null) {
            contentText = Html.fromHtml(
                    "<b>[" + mCurrentLocation.getLatitude() + ", " + mCurrentLocation.getLongitude() + "]</b> "
                            + "<b>" + getString(R.string.txt_altitude) + "</b> " + AisCoreUtils.getDistanceDisplay(getApplicationContext(), mCurrentLocation.getAltitude(), false) + "  "
                            + "<b>" + getString(R.string.txt_accuracy) + "</b> " + AisCoreUtils.getDistanceDisplay(getApplicationContext(), mCurrentLocation.getAccuracy(), true) + "  "
                            + "<b>" + getString(R.string.txt_travel_duration) + "</b> " + AisCoreUtils.getDescriptiveDurationString((int) (System.currentTimeMillis() - Long.parseLong(AisCoreUtils.GPS_SERVICE_START_TIMESTAMP)) / 1000, this)
            );
        }

        Notification notification = new NotificationCompat.Builder(getApplicationContext(), AisCoreUtils.AIS_LOCATION_CHANNEL_ID)
                .setContentTitle(contentTitle)
                .setSmallIcon(R.drawable.ic_ais_gps_logo)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.gps_satellite))
                .setStyle(new NotificationCompat.BigTextStyle().bigText(contentText).setBigContentTitle(contentTitle))
                .setContentIntent(pendingIntent)
                .addAction(exitAction)
                .build();
        return notification;
    }

    @Override
    public IBinder onBind(Intent arg0) {
        Log.d(TAG, "onBind");
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand");
        createNotificationChannel();

        AisCoreUtils.GPS_SERVICE_LOCATIONS_DETECTED = 0;
        AisCoreUtils.GPS_SERVICE_LOCATIONS_SENT = 0;

        Notification notification = getNotification();
        startForeground(AisCoreUtils.AIS_LOCATION_NOTIFICATION_ID, notification);

        // fuse
        startLocationUpdates();

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate");
        //
        AisCoreUtils.GPS_SERVICE_START_TIMESTAMP = String.valueOf(System.currentTimeMillis());

        initializeFuseLocationManager();
        createLocationCallback();
        createLocationRequest();
        buildLocationSettingsRequest();
    }

    private void buildLocationSettingsRequest() {
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder();
        builder.addLocationRequest(mLocationRequest);
    }

    private void createLocationRequest() {
        mLocationRequest = new LocationRequest();

        // Sets the desired interval for active location updates. This interval is
        // inexact. You may not receive updates at all if no location sources are available, or
        // you may receive them slower than requested. You may also receive updates faster than
        // requested if other applications are requesting location at a faster interval.
        mLocationRequest.setInterval(UPDATE_INTERVAL_IN_MILLISECONDS);

        // Sets the fastest rate for active location updates. This interval is exact, and your
        // application will never receive updates faster than this value.
        mLocationRequest.setFastestInterval(FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS);

        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        // No location updates if the device does not move or cross that distance.
        mLocationRequest.setSmallestDisplacement(LOCATION_DISTANCE);
    }


    /**
     * Creates a callback for receiving location events.
     */
    private void createLocationCallback() {
        mLocationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                super.onLocationResult(locationResult);

                mCurrentLocation = locationResult.getLastLocation();

                // update notification
                AisCoreUtils.GPS_SERVICE_LOCATIONS_DETECTED++;
                Notification notification = getNotification();
                NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                assert notificationManager != null;
                notificationManager.notify(AisCoreUtils.AIS_LOCATION_NOTIFICATION_ID, notification);

                // report location to AIS gate
                DomWebInterface.updateDeviceLocation(getApplicationContext(), mCurrentLocation);
                //

                // update notification

            }
        };
    }

    /**
     * Requests location updates from the FusedLocationApi. Note: we don't call this unless location
     * runtime permission has been granted.
     */
    private void startLocationUpdates() {
        // Begin by checking if the device has the necessary location settings.
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        mFusedLocationClient.requestLocationUpdates(mLocationRequest, mLocationCallback, Looper.myLooper());
    }

    /**
     * Removes location updates from the FusedLocationApi.
     */
    private void stopLocationUpdates() {
        // It is a good practice to remove location requests when the activity is in a paused or
        // stopped state. Doing so helps battery performance and is especially
        // recommended in applications that request frequent location updates.
        try {
            mFusedLocationClient.removeLocationUpdates(mLocationCallback);
        } catch (Exception ex) {
            Log.e(TAG, "fail to stopLocationUpdates", ex);
        }

    }


    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        super.onDestroy();
        // fuse
        stopLocationUpdates();
    }

    private void initializeFuseLocationManager() {
        Log.d(TAG, "initializeFuseLocationManager - LOCATION_INTERVAL: "+ UPDATE_INTERVAL_IN_MILLISECONDS + " LOCATION_DISTANCE: " + LOCATION_DISTANCE);
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
    }
}