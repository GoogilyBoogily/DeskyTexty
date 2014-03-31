package com.googilyboogily.deskytexty;

import android.content.Intent;
import android.content.IntentSender;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.widget.TextView;
import android.widget.Toast;

import com.googilyboogily.deskytexty.tasks.SaveSmsAsyncTask;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.drive.Contents;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveApi;
import com.google.android.gms.drive.DriveFolder;
import com.google.android.gms.drive.DriveId;
import com.google.android.gms.drive.MetadataChangeSet;
import com.google.android.gms.drive.query.Filters;
import com.google.android.gms.drive.query.Query;
import com.google.android.gms.drive.query.SearchableField;

import com.googilyboogily.deskytexty.tasks.SaveSmsAsyncTask;

import java.io.IOException;


public class MainActivity extends BaseDriveActivity {

	private static final String TAG = "deskytexty";
	private static final int REQUEST_CODE_CAPTURE_IMAGE = 1;
	private static final int REQUEST_CODE_CREATOR = 2;
	private static final int REQUEST_CODE_RESOLUTION = 3;

	private GoogleApiClient mGoogleApiClient;

	//
	static TextView msgText;

	//
	DriveFolder appFolder;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		// Get user SMS cursor
		Uri uri = Uri.parse("content://sms/inbox");
		Cursor c = getContentResolver().query(uri, null, null, null, null);

		// Arrays to hold the sms messages and phone numbers
		String[] body = new String[c.getCount()];
		String[] number = new String[c.getCount()];

		if(c.moveToFirst()) {
			for(int i = 0; i < c.getCount(); i++) {
				body[i] = c.getString(c.getColumnIndexOrThrow("body")).toString();
				number[i] = c.getString(c.getColumnIndexOrThrow("address")).toString();
				c.moveToNext();
			} // end for
		} // end if
		c.close();

		// Set the textview to the first sms message
		msgText = (TextView)findViewById(R.id.textView);
		msgText.setText(body[1]);
	} // end onCreate()

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.main, menu);
		return super.onCreateOptionsMenu(menu);
	} // onCrateOptionMenu()

	@Override
	protected void onResume() {
		super.onResume();
		if (mGoogleApiClient == null) {
			// Create the API client and bind it to an instance variable.
			// We use this instance as the callback for connection and connection
			// failures.
			// Since no account name is passed, the user is prompted to choose.
			mGoogleApiClient = new GoogleApiClient.Builder(this)
				.addApi(Drive.API)
				.addScope(Drive.SCOPE_FILE)
				.addConnectionCallbacks(this)
				.addOnConnectionFailedListener(this)
				.build();
		} // end if

		// Connect the client.
		mGoogleApiClient.connect();
	} // end onResume


	@Override
	protected void onPause() {
		if (mGoogleApiClient != null) {
			mGoogleApiClient.disconnect();
		} // end if

		super.onPause();
	} // end onPause()

	@Override
	protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {

	} // end onActivityResult()

	@Override
	public void onConnectionFailed(ConnectionResult result) {
		// Called whenever the API client fails to connect.
		Log.i(TAG, "GoogleApiClient connection failed: " + result.toString());

		if (!result.hasResolution()) {
			// show the localized error dialog.
			GooglePlayServicesUtil.getErrorDialog(result.getErrorCode(), this, 0).show();
			return;
		} //  end if
		// The failure has a resolution. Resolve it.
		// Called typically when the app is not yet authorized, and an
		// authorization
		// dialog is displayed to the user.
		try {
			result.startResolutionForResult(this, REQUEST_CODE_RESOLUTION);
		} catch (IntentSender.SendIntentException e) {
			Log.e(TAG, "Exception while starting resolution activity", e);
		} // end try/catch
	} // end onConnectionFailed()

	@Override
	public void onConnected(Bundle connectionHint) {
		Log.i(TAG, "API client connected.");

		// QUERY FOR APP FOLDER
		Query query = new Query.Builder().addFilter(Filters.and(Filters.eq(SearchableField.TITLE, "DESKYTEXTYAPPFOLDER"),
																Filters.eq(SearchableField.MIME_TYPE, "application/vnd.google-apps.folder"),
																Filters.eq(SearchableField.TRASHED, false))).build();

		Drive.DriveApi.query(getGoogleApiClient(), query).setResultCallback(metadataCallback);
	} // end onConnected()

	ResultCallback<DriveFolder.DriveFolderResult> folderCreatedCallback = new ResultCallback<DriveFolder.DriveFolderResult>() {
		@Override
		public void onResult(DriveFolder.DriveFolderResult result) {
			if (!result.getStatus().isSuccess()) {
				showMessage("Error while trying to create the folder");
				return;
			} // end if

			showMessage("Created a folder: " + result.getDriveFolder().getDriveId());
		} // end onResult
	};

	final private ResultCallback<DriveApi.MetadataBufferResult> metadataCallback = new
		ResultCallback<DriveApi.MetadataBufferResult>() {
			@Override
			public void onResult(DriveApi.MetadataBufferResult result) {
				if (!result.getStatus().isSuccess()) {
					showMessage("Problem while retrieving results");
					return;
				} // end if

				// If there are already files named DESKYTEXTYAPPFOLDER, display them, otherwise, make it
				if(result.getMetadataBuffer().getCount() != 0) {
					String newString = String.valueOf(result.getMetadataBuffer().getCount()) +
						" : " + result.getMetadataBuffer().get(0).getTitle() + " -- " +
						result.getMetadataBuffer().get(0).getCreatedDate();
					msgText.setText(newString);

					appFolder = Drive.DriveApi.getFolder(getGoogleApiClient(), result.getMetadataBuffer().get(0).getDriveId());
				} else {
					// Create the metadata for "DESKYTEXTYAPPFOLDER"
					MetadataChangeSet changeSet = new MetadataChangeSet.Builder().setTitle("DESKYTEXTYAPPFOLDER").build();

					// Actually create the folder
					Drive.DriveApi.getRootFolder(getGoogleApiClient()).createFolder(getGoogleApiClient(), changeSet).setResultCallback(folderCreatedCallback);

					msgText.setText("Created Folder DESKYTEXTYAPPFOLDER");
				}
			} // end onResult()
		};

	@Override
	public void onConnectionSuspended(int cause) {
		Log.i(TAG, "GoogleApiClient connection suspended");
	} // end onConnectionSuspended()

} // end class MainActivity()
