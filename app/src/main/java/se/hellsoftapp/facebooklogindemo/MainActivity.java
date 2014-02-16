package se.hellsoftapp.facebooklogindemo;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.Toast;

import com.facebook.Session;
import com.facebook.SessionState;
import com.facebook.UiLifecycleHelper;
import com.facebook.widget.LoginButton;
import com.facebook.widget.ProfilePictureView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MainActivity extends FragmentActivity {
    public static final String TAG = "FacebookLoginDemo";
    private static final int CAPTURE_PHOTO_REQUEST_CODE = 1001;
    private Uri mCapturedPhotoUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.container, new FacebookLoginFragment())
                    .commit();
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == CAPTURE_PHOTO_REQUEST_CODE && resultCode == RESULT_OK) {
            Log.d(TAG, "Photo captured: " + mCapturedPhotoUri);
            MyFacebookSyncService.startPostFacebookPhoto(this, mCapturedPhotoUri);
        }
    }

    public void onFacebookRefresh(View view) {
        MyFacebookSyncService.startActionUpdateFromWall(this);
    }

    public void onPostPhotoClicked(View view) {
        Session session = Session.getActiveSession();
        if (session.isOpened()) {
            session.requestNewPublishPermissions(
                    new Session.NewPermissionsRequest(this,
                            "publish_stream"));

            Intent takePhotoIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            mCapturedPhotoUri = getOutputPhotoUri(this);

            takePhotoIntent.putExtra(MediaStore.EXTRA_OUTPUT, mCapturedPhotoUri);

            startActivityForResult(takePhotoIntent, CAPTURE_PHOTO_REQUEST_CODE);
        }
    }

    private static Uri getOutputPhotoUri(Context context) {
        File mediaStorageDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);

        // Create a media file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        StringBuilder stringBuilder = new StringBuilder();
        return Uri.fromFile(new File(stringBuilder.append(mediaStorageDir.getPath())
                .append(File.separator)
                .append("IMG_")
                .append(timeStamp)
                .append(".jpg").toString()));
    }


    /**
     * A placeholder fragment containing a simple view.
     */
    public static class FacebookLoginFragment extends Fragment implements LoaderManager.LoaderCallbacks<Cursor> {

        private Session.StatusCallback mCallback = new Session.StatusCallback() {
            @Override
            public void call(Session session, SessionState state, Exception exception) {
                onSessionStateChange(session, state, exception);
            }
        };
        private UiLifecycleHelper mUiHelper;
        private SimpleCursorAdapter mListAdapter;

        public FacebookLoginFragment() {
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            mUiHelper = new UiLifecycleHelper(getActivity(), mCallback);
            mUiHelper.onCreate(savedInstanceState);
        }

        @Override
        public void onResume() {
            super.onResume();

            // For scenarios where the main activity is launched and user
            // session is not null, the session state change notification
            // may not be triggered. Trigger it if it's open/closed.
            Session session = Session.getActiveSession();
            if (session != null &&
                    (session.isOpened() || session.isClosed())) {
                onSessionStateChange(session, session.getState(), null);
            }

            mUiHelper.onResume();
        }

        private void onSessionStateChange(final Session session, SessionState state, Exception e) {
            View refreshButton = getActivity().findViewById(R.id.refresh_button);
            View photoButton = getActivity().findViewById(R.id.post_photo_button);
            if (session.isOpened()) {
                Toast.makeText(getActivity(), "User logged in to Facebook.", Toast.LENGTH_SHORT).show();
                refreshButton.setEnabled(true);
                photoButton.setEnabled(true);
            } else if (session.isClosed()) {
                Toast.makeText(getActivity(), "User logged out from Facebook.", Toast.LENGTH_SHORT).show();
                photoButton.setEnabled(false);
                refreshButton.setEnabled(false);
                MyFacebookSyncService.startActionUserLogout(getActivity());
            }
        }

        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            super.onActivityResult(requestCode, resultCode, data);
            mUiHelper.onActivityResult(requestCode, resultCode, data);
        }

        @Override
        public void onPause() {
            super.onPause();
            mUiHelper.onPause();
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            mUiHelper.onDestroy();
        }

        @Override
        public void onSaveInstanceState(Bundle outState) {
            super.onSaveInstanceState(outState);
            mUiHelper.onSaveInstanceState(outState);
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.facebook_login, container, false);
            LoginButton loginButton = (LoginButton) rootView.findViewById(R.id.facebookLoginButton);
            loginButton.setFragment(this);
            loginButton.setReadPermissions("user_status", "user_friends",
                    "friends_status", "read_stream");

            mListAdapter = new SimpleCursorAdapter(getActivity(),
                    R.layout.facebook_message,
                    null,
                    new String[]{MyFacebookWall.Contract.FROM_NAME,
                            MyFacebookWall.Contract.MESSAGE, MyFacebookWall.Contract.FROM_ID},
                    new int[]{R.id.from_name, R.id.message, R.id.profile_picture}, 0);
            mListAdapter.setViewBinder(new SimpleCursorAdapter.ViewBinder() {
                @Override
                public boolean setViewValue(View view, Cursor cursor, int columnIndex) {
                    if (columnIndex == cursor.getColumnIndex(MyFacebookWall.Contract.FROM_ID)) {
                        ((ProfilePictureView) view).setProfileId(cursor.getString(columnIndex));
                        return true;
                    }
                    return false;
                }
            });

            ListView facebookMessages = (ListView) rootView.findViewById(R.id.facebook_message_list);
            facebookMessages.setAdapter(mListAdapter);

            getLoaderManager().initLoader(0, null, this);

            return rootView;
        }

        @Override
        public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
            return new CursorLoader(getActivity(),
                    MyFacebookWall.Contract.FACEBOOK_WALL_URI,
                    new String[]{MyFacebookWall.Contract.ID,
                            MyFacebookWall.Contract.FROM_NAME,
                            MyFacebookWall.Contract.FROM_ID,
                            MyFacebookWall.Contract.MESSAGE},
                    null, null, null);
        }

        @Override
        public void onLoadFinished(Loader<Cursor> objectLoader, Cursor cursor) {
            mListAdapter.swapCursor(cursor);
            Log.d(TAG, "Loader finished: " + cursor.getCount());
        }

        @Override
        public void onLoaderReset(Loader<Cursor> objectLoader) {
            Log.d(TAG, "Loader reset!");
        }
    }

}
