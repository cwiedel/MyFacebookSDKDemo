package se.hellsoftapp.facebooklogindemo;

import android.app.IntentService;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;

import com.facebook.Request;
import com.facebook.Response;
import com.facebook.Session;
import com.facebook.model.GraphObject;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

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
    private static final String FACEBOOK_PREFS = "facebook_settings";
    private static final String NEXT_SINCE_VALUE = "nextSinceValue";

    /**
     * Starts this service to perform action Foo with the given parameters. If
     * the service is already performing a task this action will be queued.
     *
     * @see IntentService
     */
    public static void startActionUpdateFromWall(Context context) {
        Intent intent = new Intent(context, MyFacebookSyncService.class);
        intent.setAction(ACTION_UPDATE_FROM_WALL);
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

    public MyFacebookSyncService() {
        super("MyFacebookSyncService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            final String action = intent.getAction();
            if (ACTION_UPDATE_FROM_WALL.equals(action)) {
                handleActionUpdateFromWall();
            } else if (ACTION_USER_LOGOUT.equals(action)) {
                handleActionUserLogout();
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
        Log.d("MyFacebookSyncService", "Logged in to facebook: " + isOpened);
        if (session != null && isOpened) {
            SharedPreferences preferences
                    = getSharedPreferences(FACEBOOK_PREFS, MODE_PRIVATE);
            long nextSinceValue = preferences.getLong(NEXT_SINCE_VALUE, -1);
            String graphPath = "me/home?fields=id,from,message,type";
            if(nextSinceValue > 0) {
                graphPath += "&since=" + nextSinceValue;
            }
            Request request = new Request(session, graphPath);
            Response response = request.executeAndWait();
            Log.d("MyFacebookSyncService", "Response: " + response.getError());

            // Fetch current time to use in next request!

            GraphObject graphObject = response.getGraphObject();
//            Log.d("MyFacebookSyncService", "Got graphObject: " + graphObject);
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
                        Log.e("MyFacebookSyncService",
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
        String message = wallMessage.getString("message");
        String createdTime = wallMessage.getString("created_time");

        ContentValues values = new ContentValues();
        values.put(MyFacebookWall.Contract.MESSAGE_ID, messageId);
        values.put(MyFacebookWall.Contract.FROM_ID, fromId);
        values.put(MyFacebookWall.Contract.FROM_NAME, fromName);
        values.put(MyFacebookWall.Contract.MESSAGE, message);
        values.put(MyFacebookWall.Contract.TYPE, type);
        values.put(MyFacebookWall.Contract.CREATED_TIME, createdTime);
        Uri newMessage = getContentResolver().insert(
                MyFacebookWall.Contract.FACEBOOK_WALL_URI,
                values);
        if (newMessage == null) {
            Log.e("MyFacebookSyncService", "Invalid message!");
        } else {
            Log.d("MyFacebookSyncService", "Inserted: " + newMessage);
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
