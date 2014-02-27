package se.hellsoftapp.facebooklogindemo;

import android.app.AlarmManager;
import android.app.IntentService;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;

import com.facebook.HttpMethod;
import com.facebook.Request;
import com.facebook.Response;
import com.facebook.Session;
import com.facebook.model.GraphObject;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileNotFoundException;

/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 * <p/>
 * helper methods.
 */
public class MyFacebookSyncService extends IntentService {
    // IntentService can perform, e.g. ACTION_FETCH_NEW_ITEMS
    private static final String ACTION_UPDATE_FROM_WALL = "se.hellsoft.facebooklogindemo.action.UPDATE_FROM_WALL";
    private static final String ACTION_USER_LOGOUT = "se.hellsoft.facebooklogindemo.action.USER_LOGOUT";
    private static final String ACTION_POST_PHOTO = "se.hellsoft.facebooklogindemo.action.POST_PHOTO";
    private static final String FACEBOOK_PREFS = "facebook_settings";
    private static final String NEXT_SINCE_VALUE = "nextSinceValue";
    private static AlarmManager alarmMgr;
    private static PendingIntent alarmIntent;


    /**
     * Starts this service to perform action Foo with the given parameters. If
     * the service is already performing a task this action will be queued.
     *
     * @see IntentService
     */
    public static void startActionUpdateFromWall(Context context) {
        Intent intent = new Intent(context, MyFacebookSyncService.class);
        intent.setAction(ACTION_UPDATE_FROM_WALL);
        PendingIntent pendingIntent = PendingIntent.getService(context, 0, intent, 0);

        alarmMgr = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        alarmMgr.setInexactRepeating(AlarmManager.ELAPSED_REALTIME,0, AlarmManager.INTERVAL_FIFTEEN_MINUTES, pendingIntent );
        context.startService(intent);

    }

    /**
     * Starts this service to perform action Baz with the given parameters. If
     * the service is already performing a task this action will be queued.
     *
     * @see IntentService
     */
    public static void startActionUserLogout(Context context) {
        Intent intent = new Intent(context, MyFacebookSyncService.class);
        intent.setAction(ACTION_USER_LOGOUT);
        context.startService(intent);
    }

    public static void startPostFacebookPhoto(Context context, Uri photoUri, String caption) {
        Intent intent = new Intent(context, MyFacebookSyncService.class);
        intent.setAction(ACTION_POST_PHOTO);
        intent.setData(photoUri);
        intent.putExtra("caption", caption);
        context.startService(intent);
    }

    public MyFacebookSyncService() {
        super(MainActivity.TAG);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            final String action = intent.getAction();

            if (ACTION_UPDATE_FROM_WALL.equals(action)) {
                handleActionUpdateFromWall();
                Log.d(MainActivity.TAG, "update triggered");
            } else if (ACTION_POST_PHOTO.equals(action)) {
                String caption = intent.getStringExtra("caption");
                handlePhotoUpload(intent.getData(), caption);
            } else if (ACTION_USER_LOGOUT.equals(action)) {
                handleActionUserLogout();
            }
        }
    }

    private void handlePhotoUpload(Uri photoUri, String caption) {
        Session session = Session.getActiveSession();
        boolean isOpened = session.isOpened();
        Log.d(MainActivity.TAG, "Logged in to facebook: " + isOpened);
        if (session != null && isOpened && photoUri != null) {
            try {
                ContentResolver resolver = getContentResolver();
                Bitmap bitmap = BitmapFactory.decodeStream(resolver.openInputStream(photoUri));
                Request request = Request.newUploadPhotoRequest(session, bitmap, new Request.Callback() {
                    @Override
                    public void onCompleted(Response response) {
                        Log.d(MainActivity.TAG, "Response: " + response.getError());
                    }

                });

                // ADD CAPTION FROM OUR TEXTFIELD
                Bundle params = request.getParameters();
                params.putString("name", caption);
                request.setParameters(params);


                Response response = request.executeAndWait();

                GraphObject graphObject = response.getGraphObject();
                if (graphObject != null) {
                    Log.d(MainActivity.TAG, graphObject.toString());
                } else {
                    Log.d(MainActivity.TAG, "Response: " + response);
                }

            } catch (FileNotFoundException e) {
                Log.e(MainActivity.TAG, "Error uploading photo to Facebook!");
            }
        }
    }

    /**
     * Handle action Foo in the provided background thread with the provided
     * parameters.
     */
    private void handleActionUpdateFromWall() {
        // Get active Facebook Session
        Session session = Session.getActiveSession();
        boolean isOpened = session.isOpened();
        Log.d(MainActivity.TAG, "Logged in to facebook: " + isOpened);
        if (session != null && isOpened) {
            SharedPreferences preferences
                    = getSharedPreferences(FACEBOOK_PREFS, MODE_PRIVATE);
            long nextSinceValue = preferences.getLong(NEXT_SINCE_VALUE, -1);
            Bundle params = new Bundle();
            params.putString("fields", "id,from,message,type,place");
            params.putInt("limit", 50);
            String graphPath = "me/home";
            if (nextSinceValue > 0) {
                params.putLong("since", nextSinceValue);
            }
            Request request = new Request(session, graphPath, params, HttpMethod.GET);
            Response response = request.executeAndWait();
            Log.d(MainActivity.TAG, "Response: " + response.getError());

            // Fetch current time to use in next request!

            GraphObject graphObject = response.getGraphObject();
//            Log.d(MainActivity.TAG, "Got graphObject: " + graphObject);
            if (graphObject != null) {
                long nowInSeconds = System.currentTimeMillis() / 1000;
                preferences.edit().putLong(NEXT_SINCE_VALUE, nowInSeconds).apply();

                JSONArray dataArray = (JSONArray) graphObject.getProperty("data");

                int length = dataArray.length();
                for (int i = 0; i < length; i++) {
                    JSONObject wallMessage = null;
                    try {
                        wallMessage = dataArray.getJSONObject(i);
                        storeWallMessage(wallMessage);
                    } catch (JSONException e) {
                        Log.e(MainActivity.TAG,
                                "Invalid message format: " + wallMessage, e);
                    }
                }
            }
        }
    }

    private void storeWallMessage(JSONObject wallMessage) throws JSONException {
        String messageId = wallMessage.getString("id");
        JSONObject from = wallMessage.getJSONObject("from");
        String fromId = from.getString("id");
        String fromName = from.getString("name");
        String type = wallMessage.getString("type");
        String message = wallMessage.optString("message");
        String createdTime = wallMessage.getString("created_time");

        String placeName = " ";

        if(!wallMessage.isNull("place")){
            JSONObject place = wallMessage.getJSONObject("place");
            placeName = place.optString("name");
        }

       // Log.d("storeWallMSG", "place JSON: " +place );


        ContentValues values = new ContentValues();
        values.put(MyFacebookWall.Contract.MESSAGE_ID, messageId);
        values.put(MyFacebookWall.Contract.FROM_ID, fromId);
        values.put(MyFacebookWall.Contract.FROM_NAME, fromName);
        values.put(MyFacebookWall.Contract.MESSAGE, message);
        values.put(MyFacebookWall.Contract.TYPE, type);
        values.put(MyFacebookWall.Contract.CREATED_TIME, createdTime);
        values.put(MyFacebookWall.Contract.PLACE_ID, placeName);

        Uri newMessage = getContentResolver()
                .insert(MyFacebookWall.Contract.FACEBOOK_WALL_URI,
                        values);
        if (newMessage == null) {
            Log.e(MainActivity.TAG, "Invalid message!");
        } else {
            Log.d(MainActivity.TAG, "Inserted: " + newMessage);
        }
    }

    /**
     * Handle action Baz in the provided background thread with the provided
     * parameters.
     */
    private void handleActionUserLogout() {
        getContentResolver()
                .delete(MyFacebookWall.Contract.FACEBOOK_WALL_URI,
                        null, null);
        SharedPreferences preferences
                = getSharedPreferences(FACEBOOK_PREFS, MODE_PRIVATE);
        preferences.edit().remove(NEXT_SINCE_VALUE).apply();
    }
}
