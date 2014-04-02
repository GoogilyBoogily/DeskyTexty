package com.googilyboogily.deskytexty;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.drive.Contents;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveApi;
import com.google.android.gms.drive.DriveFile;
import com.google.android.gms.drive.DriveFolder;
import com.google.android.gms.drive.DriveId;
import com.google.android.gms.drive.MetadataChangeSet;
import com.google.android.gms.drive.query.Filters;
import com.google.android.gms.drive.query.Query;
import com.google.android.gms.drive.query.SearchableField;

import java.io.IOException;

public class SmsListener extends BroadcastReceiver implements GoogleApiClient.ConnectionCallbacks,
                                                              GoogleApiClient.OnConnectionFailedListener {
	GoogleApiClient mGoogleApiClient;
	DriveId appFolderId;
	DriveFile fileToSave;

	protected static final int REQUEST_CODE_RESOLUTION = 1;

	String mobileNum;
	String messageBody;

	@Override
	public void onReceive(Context context, Intent intent) {
		Bundle bundle = intent.getExtras();
		SmsMessage[] msgs;
		String str = "";

		if (bundle != null) {
			Object[] pdus = (Object[]) bundle.get("pdus");
			msgs = new SmsMessage[pdus.length];

			for (int i = 0; i < msgs.length; i++) {
				msgs[i] = SmsMessage.createFromPdu((byte[]) pdus[i]);
				str += "SMS from " + msgs[i].getOriginatingAddress();

				mobileNum = msgs[i].getOriginatingAddress();

				str += " :";
				str += msgs[i].getMessageBody().toString();
				messageBody = msgs[i].getMessageBody().toString();

				str += "\n";

				mobileNum = msgs[i].getOriginatingAddress();
			} // end for

			SaveSmsToGoogleDrive(mobileNum, messageBody);
		} // end if
	} // end onReceive()

	private void SaveSmsToGoogleDrive(String mobileNum, String messageBody) {
		showMessage("Caught new SMS -- " + mobileNum + ": " + messageBody);

		mGoogleApiClient = new GoogleApiClient.Builder(MainActivity.getAppContext())
			.addApi(Drive.API)
			.addScope(Drive.SCOPE_FILE)
			.addConnectionCallbacks(this)
			.addOnConnectionFailedListener(this)
			.build();

		mGoogleApiClient.connect();
	} // end SaveSmsToGoogleDrive()

	/**
	 * Called when {@code mGoogleApiClient} is connected.
	 */
	@Override
	public void onConnected(Bundle connectionHint) {
		showMessage("Connected to Drive");

		Query query = new Query.Builder().addFilter(Filters.and(Filters.eq(SearchableField.TITLE, "DESKYTEXTYAPPFOLDER"),
													Filters.eq(SearchableField.MIME_TYPE, "application/vnd.google-apps.folder"),
													Filters.eq(SearchableField.TRASHED, false))).build();

		Drive.DriveApi.query(getGoogleApiClient(), query).setResultCallback(metadataCallback);
	} // end onConnected()

	final private ResultCallback<DriveApi.MetadataBufferResult> metadataCallback = new
		ResultCallback<DriveApi.MetadataBufferResult>() {
			@Override
			public void onResult(DriveApi.MetadataBufferResult result) {
				if(!result.getStatus().isSuccess()) {
					showMessage("Problem while retrieving results");
					return;
				} // end if

				if(result.getMetadataBuffer().getCount() != 0) {
					appFolderId = result.getMetadataBuffer().get(0).getDriveId();

					Drive.DriveApi.newContents(getGoogleApiClient()).setResultCallback(contentsResult);
				} // end if
			} // end onResult()
		};

	final private ResultCallback<DriveApi.ContentsResult> contentsResult = new
		ResultCallback<DriveApi.ContentsResult>() {
			@Override
			public void onResult(DriveApi.ContentsResult result) {
				if (!result.getStatus().isSuccess()) {
					showMessage("Error while trying to create new file contents");
					return;
				} // end if

				DriveFolder folder = Drive.DriveApi.getFolder(getGoogleApiClient(), appFolderId);

				MetadataChangeSet changeSet = new MetadataChangeSet.Builder()
					.setTitle("New file")
					.setMimeType("text/plain")
					.build();
				folder.createFile(getGoogleApiClient(), changeSet, result.getContents())
					.setResultCallback(fileCallback);
			} // end onResult()
		};

	final private ResultCallback<DriveFolder.DriveFileResult> fileCallback = new
		ResultCallback<DriveFolder.DriveFileResult>() {
			@Override
			public void onResult(DriveFolder.DriveFileResult result) {
				if (!result.getStatus().isSuccess()) {
					showMessage("Error while trying to create the file");
					return;
				} // end if

				showMessage("Created a file: " + result.getDriveFile().getDriveId());

				fileToSave = result.getDriveFile();

				fileToSave.openContents(mGoogleApiClient, DriveFile.MODE_WRITE_ONLY, null).setResultCallback(new ResultCallback<DriveApi.ContentsResult>() {
					@Override
					public void onResult(DriveApi.ContentsResult result) {
						if(!result.getStatus().isSuccess()) {
							// Handle error
							return;
						} // end if
						Contents contents = result.getContents();
						try {
							contents.getOutputStream().write(("Hello world -- " + mobileNum + ": " + messageBody).getBytes());
						} catch(IOException e) {
							e.printStackTrace();
						} // end try/catch

						fileToSave.commitAndCloseContents(mGoogleApiClient, contents).setResultCallback(new ResultCallback<Status>() {
							@Override
							public void onResult(Status result) {
								showMessage("Successfully committed file");
								mGoogleApiClient.disconnect();
							} // end onResult()
						});
					} // end onResult()
				});

			} // end onResult()
		};

	/**
	 * Called when {@code mGoogleApiClient} is disconnected.
	 */
	@Override
	public void onConnectionSuspended(int cause) {
		mGoogleApiClient.disconnect();
	}

	/**
	 * Called when {@code mGoogleApiClient} is trying to connect but failed.
	 * Handle {@code result.getResolution()} if there is a resolution is
	 * available.
	 */
	@Override
	public void onConnectionFailed(ConnectionResult result) {
		if (!result.hasResolution()) {
			// show the localized error dialog.
			GooglePlayServicesUtil.getErrorDialog(result.getErrorCode(), (Activity) MainActivity.getAppContext(), 0).show();
			return;
		}
		try {
			result.startResolutionForResult((Activity)MainActivity.getAppContext(), REQUEST_CODE_RESOLUTION);
		} catch (IntentSender.SendIntentException e) {

		}
	}

	/**
	 * Shows a toast message.
	 */
	public void showMessage(String message) {
		Toast.makeText(MainActivity.getAppContext(), message, Toast.LENGTH_LONG).show();
	}

	/**
	 * Getter for the {@code GoogleApiClient}.
	 */
	public GoogleApiClient getGoogleApiClient() {
		return mGoogleApiClient;
	}

} // end class SmsListener
