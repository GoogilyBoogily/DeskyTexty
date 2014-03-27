package com.googilyboogily.deskytexty;

import android.app.Activity;
import android.content.Intent;
import android.content.IntentSender;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.Telephony;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveApi;
import com.google.android.gms.drive.MetadataChangeSet;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;


public class MainActivity extends BaseDriveActivity {

	private static final String TAG = "deskytexty";
	private static final int REQUEST_CODE_CAPTURE_IMAGE = 1;
	private static final int REQUEST_CODE_CREATOR = 2;
	private static final int REQUEST_CODE_RESOLUTION = 3;

	private GoogleApiClient mGoogleApiClient;
	private Bitmap mBitmapToSave;


	TextView msgText;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);


		Cursor c = databaseRO.query(DatabaseOpenHelper.SMS_TABLE_NAME,
			new String[] { "smsID", "phoneNumber", "name", "shortenedMessage", "answerTo",
				"dIntents", "sIntents", "numParts", "resSIntent", "resDIntent", "date"}, null, null, null , null, null);
		int rowCount = c.getCount();
		c.moveToFirst();
		Telephony.Sms[] res = new Telephony.Sms[rowCount];
		for (int i = 0; i < rowCount; i++) {
			res[i] = new Telephony.Sms(c.getInt(0),
				c.getString(1),
				c.getString(2),
				c.getString(3),
				c.getString(4),
				c.getString(5),
				c.getString(6),
				c.getInt(7),
				c.getInt(8),
				c.getLong(9));
			c.moveToNext();
		} // end for
		c.close();

		// Get user SMS cursor
		Cursor cursor = getContentResolver().query(Uri.parse("content://sms/inbox"), null, null, null, null);
		cursor.moveToFirst();
		// Message string (dis gonna be big, probably)
		String msgData = "";
		// Loop through to get all messages
		do {
			for(int idx = 0; idx < cursor.getColumnCount(); idx++) {
				msgData += " " + cursor.getColumnName(idx) + ":" + cursor.getString(idx);
			}
		} while(cursor.moveToNext());

		msgText = (TextView)findViewById(R.id.textView);

		msgText.setText(msgData);

	} // end onCreate()

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.main, menu);
		return super.onCreateOptionsMenu(menu);
	}


	/**
	 * Create a new file and save it to Drive.
	 */
	private void saveFileToDrive() {
		// Start by creating a new contents, and setting a callback.
		Log.i(TAG, "Creating new contents.");
		final Bitmap image = mBitmapToSave;

		Drive.DriveApi.newContents(mGoogleApiClient).setResultCallback(new ResultCallback<DriveApi.ContentsResult>() {
			@Override
			public void onResult(DriveApi.ContentsResult result) {
				// If the operation was not successful, we cannot do anything
				// and must
				// fail.
				if (!result.getStatus().isSuccess()) {
					Log.i(TAG, "Failed to create new contents.");
					return;
				} // end if

				// Otherwise, we can write our data to the new contents.
				Log.i(TAG, "New contents created.");
				// Get an output stream for the contents.
				OutputStream outputStream = result.getContents().getOutputStream();
				// Write the bitmap data from it.
				ByteArrayOutputStream bitmapStream = new ByteArrayOutputStream();
				image.compress(Bitmap.CompressFormat.PNG, 100, bitmapStream);
				try {
					outputStream.write(bitmapStream.toByteArray());
				} catch (IOException e1) {
					Log.i(TAG, "Unable to write file contents.");
				} // end try/catch
				// Create the initial metadata - MIME type and title.
				// Note that the user will be able to change the title later.
				MetadataChangeSet metadataChangeSet = new MetadataChangeSet.Builder()
					.setMimeType("image/jpeg").setTitle("Android Photo.png").build();
				// Create an intent for the file chooser, and start it.
				IntentSender intentSender = Drive.DriveApi
					.newCreateFileActivityBuilder()
					.setInitialMetadata(metadataChangeSet)
					.setInitialContents(result.getContents())
					.build(mGoogleApiClient);
				try {
					startIntentSenderForResult(
						intentSender, REQUEST_CODE_CREATOR, null, 0, 0, 0);
				} catch (IntentSender.SendIntentException e) {
					Log.i(TAG, "Failed to launch file chooser.");
				} // end try/catch
			} // end onResult()
		});
	} // end saveFileToDrive()

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

		// Connect the client. Once connected, the camera is launched.
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
		switch (requestCode) {
			case REQUEST_CODE_CAPTURE_IMAGE:
				// Called after a photo has been taken.
				if (resultCode == Activity.RESULT_OK) {
					// Store the image data as a bitmap for writing later.
					mBitmapToSave = (Bitmap) data.getExtras().get("data");
				} // end if

				break;

			case REQUEST_CODE_CREATOR:
				// Called after a file is saved to Drive.
				if (resultCode == RESULT_OK) {
					Log.i(TAG, "Image successfully saved.");
					mBitmapToSave = null;
					// Just start the camera again for another photo.
					startActivityForResult(new Intent(MediaStore.ACTION_IMAGE_CAPTURE),
						REQUEST_CODE_CAPTURE_IMAGE);
				} // end if

				break;
		} // end switch
	} // end onActivityResult()

	@Override
	public void onConnectionFailed(ConnectionResult result) {
		// Called whenever the API client fails to connect.
		Log.i(TAG, "GoogleApiClient connection failed: " + result.toString());
		if (!result.hasResolution()) {
			// show the localized error dialog.
			GooglePlayServicesUtil.getErrorDialog(result.getErrorCode(), this, 0).show();
			return;
		}
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

		if (mBitmapToSave == null) {
			// This activity has no UI of its own. Just start the camera.
			startActivityForResult(new Intent(MediaStore.ACTION_IMAGE_CAPTURE),
				REQUEST_CODE_CAPTURE_IMAGE);
			return;
		} // end if

		//saveFileToDrive();
	} // end onConnected()

	@Override
	public void onConnectionSuspended(int cause) {
		Log.i(TAG, "GoogleApiClient connection suspended");
	} // end onConnectionSuspended()

} // end class MainActivity()
