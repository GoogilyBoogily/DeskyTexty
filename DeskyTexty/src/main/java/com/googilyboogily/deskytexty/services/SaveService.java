package com.googilyboogily.deskytexty.services;

import android.app.Activity;
import android.app.Service;
import android.content.Intent;
import android.content.IntentSender;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.text.format.Time;
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

public class SaveService extends Service  implements GoogleApiClient.ConnectionCallbacks,
                                                          GoogleApiClient.OnConnectionFailedListener {
	protected static final int REQUEST_CODE_RESOLUTION = 1;

	GoogleApiClient mGoogleApiClient;

	DriveId appFolderId;
	DriveFolder appFolder;

	DriveFolder numFolder;

	DriveFile fileToSave;

	String mobileNum;
	String messageBody;

	/**
	* A constructor is required, and must call the super IntentService(String)
	* constructor with a name for the worker thread.
	*/
	private Looper mServiceLooper;
	private ServiceHandler mServiceHandler;

	// Handler that receives messages from the thread
	private final class ServiceHandler extends Handler {
		public ServiceHandler(Looper looper) {
			super(looper);
		} // end ServiceHandler()

		@Override
		public void handleMessage(Message msg) {
			synchronized(this) {
				try {
					// Do service stuff!
					SaveToDrive();

				} catch(Exception e) {
					// SaveToDrive() threw an exception
					showMessage("SaveToDrive() threw an exception");
				} // end try/catch
			} // end synchronized

			// Stop the service
			stopSelf(msg.arg1);
		} // end handleMessage()
	} // end class ServiceHandler()

	@Override
	public void onCreate() {
		// Start up the thread running the service.  Note that we create a
		// separate thread because the service normally runs in the process's
		// main thread, which we don't want to block.  We also make it
		// background priority so CPU-intensive work will not disrupt our UI.
		HandlerThread thread = new HandlerThread("ServiceStartArguments", Process.THREAD_PRIORITY_BACKGROUND);
		thread.start();

		// Get the HandlerThread's Looper and use it for our Handler
		mServiceLooper = thread.getLooper();
		mServiceHandler = new ServiceHandler(mServiceLooper);
	} // end onCreate()

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		showMessage("Service starting");

		// Get the mobile number and message from the intent
		mobileNum = intent.getExtras().getString("mobileNum");
		messageBody = intent.getExtras().getString("messageBody");

		showMessage(mobileNum + ": " + messageBody);

		// For each start request, send a message to start a job and deliver the
		// start ID so we know which request we're stopping when we finish the job
		Message msg = mServiceHandler.obtainMessage();
		msg.arg1 = startId;
		mServiceHandler.sendMessage(msg);

		// If we get killed, after returning from here, restart
		return START_STICKY;
	} // end onStartCommand()

	@Override
	public IBinder onBind(Intent intent) {
		// We don't provide binding, so return null
		return null;
	} // end onBind()

	@Override
	public void onDestroy() {
		if(mGoogleApiClient != null) {
			mGoogleApiClient.disconnect();
		} // end if

		showMessage("Service done");
	} // end onDestroy()

	public void SaveToDrive() {
		mGoogleApiClient = new GoogleApiClient.Builder(this)
			.addApi(Drive.API)
			.addScope(Drive.SCOPE_FILE)
			.addConnectionCallbacks(this)
			.addOnConnectionFailedListener(this)
			.build();

		mGoogleApiClient.connect();
	} // end SaveToDrive()

	/**
	 * Called when {@code mGoogleApiClient} is connected.
	 */
	@Override
	public void onConnected(Bundle connectionHint) {
		showMessage("Connected to Drive");

		// Create the query for the app folder
		Query query = new Query.Builder().addFilter(Filters.and(Filters.eq(SearchableField.TITLE, "DESKYTEXTYAPPFOLDER"),
													Filters.eq(SearchableField.MIME_TYPE, "application/vnd.google-apps.folder"),
													Filters.eq(SearchableField.TRASHED, false))).build();

		// Query the root folder for app folder
		Drive.DriveApi.query(getGoogleApiClient(), query).setResultCallback(queryCallback);
	} // end onConnected()

	// Callback for app folder query
	ResultCallback<DriveApi.MetadataBufferResult> queryCallback = new ResultCallback<DriveApi.MetadataBufferResult>() {
		@Override
		public void onResult(DriveApi.MetadataBufferResult queryResult) {
			if (!queryResult.getStatus().isSuccess()) {
				showMessage("Error");
				return;
			} // end if
			showMessage("Success");

			// If there is at least one result
			// TODO: Add else statement

			//if(queryResult.getMetadataBuffer().getCount() != 0) {
			appFolderId = queryResult.getMetadataBuffer().get(0).getDriveId();

			appFolder = Drive.DriveApi.getFolder(getGoogleApiClient(), appFolderId);

			appFolder.listChildren(getGoogleApiClient()).setResultCallback(listChildrenCallback);
		} // end onResult()
	};

	// Callback for listing the children files inside the app folder
	ResultCallback<DriveApi.MetadataBufferResult> listChildrenCallback = new ResultCallback<DriveApi.MetadataBufferResult>() {
		@Override
		public void onResult(DriveApi.MetadataBufferResult listChildrenResult) {
			if (!listChildrenResult.getStatus().isSuccess()) {
				showMessage("Error");
				return;
			} // end if
			showMessage("Success");

			// Get the number of children files
			int numOfChildren = listChildrenResult.getMetadataBuffer().getCount();

			// Var to hold if a folder exists with the mobile phone number
			Boolean folderExists = false;

			// Array of strigs to hold all the children folder names
			String[] temp = new String[numOfChildren];

			for(int count = 0; count < numOfChildren; count++) {
				temp[count] = listChildrenResult.getMetadataBuffer().get(count).getTitle();

				if(listChildrenResult.getMetadataBuffer().get(count).getTitle().equals(mobileNum)) {
					folderExists = true;
					numFolder = Drive.DriveApi.getFolder(getGoogleApiClient(), listChildrenResult.getMetadataBuffer().get(count).getDriveId());
				} // end if
			} // end for


			if(folderExists) {
				Drive.DriveApi.newContents(getGoogleApiClient()).setResultCallback(smsMessageContentCallback);
			} else {
				MetadataChangeSet changeSet = new MetadataChangeSet.Builder().setTitle(mobileNum).build();
				appFolder.createFolder(getGoogleApiClient(), changeSet).setResultCallback(mobileNumFolderCallback);
			} // end else/if


		} // end onResult()
	};

	// Callback for initially creating a new SMS message
	ResultCallback<DriveApi.ContentsResult> smsMessageContentCallback = new ResultCallback<DriveApi.ContentsResult>() {
		@Override
		public void onResult(DriveApi.ContentsResult contentsResult) {
			if (!contentsResult.getStatus().isSuccess()) {
				showMessage("Error");
				return;
			} // end if
			showMessage("Success");

			// TODO: Probably get the time that the message was received, not the current time
			// Get the current time
			String currentTime;
			Time now = new Time(Time.getCurrentTimezone());
			now.setToNow();
			// Link for formatting: http://www.cplusplus.com/reference/ctime/strftime/
			currentTime = now.format("%D-%T");

			MetadataChangeSet changeSet = new MetadataChangeSet.Builder()
					.setTitle(currentTime + ".sms")
					.setMimeType("text/plain")
					.build();

			numFolder.createFile(getGoogleApiClient(), changeSet, contentsResult.getContents()).setResultCallback(smsMessageCreatedCallback);
		} // end onResult()
	};

	// Callback for creating a folder with the mobile number as the name
	ResultCallback<DriveFolder.DriveFolderResult> mobileNumFolderCallback = new ResultCallback<DriveFolder.DriveFolderResult>() {
		@Override
		public void onResult(DriveFolder.DriveFolderResult mobileNumFolderResult) {
			if (!mobileNumFolderResult.getStatus().isSuccess()) {
				showMessage("Error");
				return;
			} // end if
			showMessage("Success");

			numFolder = mobileNumFolderResult.getDriveFolder();

			Drive.DriveApi.newContents(getGoogleApiClient()).setResultCallback(smsMessageContentCallback);
		} // end onResult()
	};

	// Callback for putting the contents of the SMS message in
	ResultCallback<DriveFolder.DriveFileResult> smsMessageCreatedCallback = new ResultCallback<DriveFolder.DriveFileResult>() {
		@Override
		public void onResult(DriveFolder.DriveFileResult driveFileResult) {
			if (!driveFileResult.getStatus().isSuccess()) {
				showMessage("Error");
				return;
			} // end if
			showMessage("Success");


			//showMessage("Created a file: " + driveFileResult.getDriveFile().getDriveId());
			fileToSave = driveFileResult.getDriveFile();


			fileToSave.openContents(mGoogleApiClient, DriveFile.MODE_WRITE_ONLY, null).setResultCallback(openContentCallback);
		} // end onResult()
	};

	// Callback for opening the contents of the SMS message
	ResultCallback<DriveApi.ContentsResult> openContentCallback = new ResultCallback<DriveApi.ContentsResult>() {
		@Override
		public void onResult(DriveApi.ContentsResult contentsResult) {
			if (!contentsResult.getStatus().isSuccess()) {
				showMessage("Error");
				return;
			} // end if
			showMessage("Success");

			Contents contents = contentsResult.getContents();

			try {
				contents.getOutputStream().write((messageBody).getBytes());
			} catch(IOException e) {
				e.printStackTrace();
			} // end try/catch

			fileToSave.commitAndCloseContents(mGoogleApiClient, contents).setResultCallback(commitAndCloseCallback);
		} // end onResult()
	};

	// Callback for committing and closing the SMS message
	ResultCallback<Status> commitAndCloseCallback = new ResultCallback<Status>() {
		@Override
		public void onResult(Status statusResult) {
			if (!statusResult.isSuccess()) {
				showMessage("Failed to commit file");
			} else {
				showMessage("Successfully committed file");
			} // end else/if
		} // end onResult()
	};

	/**
	 * Called when {@code mGoogleApiClient} is disconnected.
	 */
	@Override
	public void onConnectionSuspended(int cause) {
		mGoogleApiClient.disconnect();
	} // end onConnectionSuspended()

	/**
	 * Called when {@code mGoogleApiClient} is trying to connect but failed.
	 * Handle {@code result.getResolution()} if there is a resolution is
	 * available.
	 */
	@Override
	public void onConnectionFailed(ConnectionResult result) {
		if (!result.hasResolution()) {
			// show the localized error dialog.
			GooglePlayServicesUtil.getErrorDialog(result.getErrorCode(), (Activity)this.getApplicationContext(), 0).show();
			return;
		} // end if

		try {
			result.startResolutionForResult((Activity)this.getApplicationContext(), REQUEST_CODE_RESOLUTION);
		} catch (IntentSender.SendIntentException e) {
			// empty catch
		} // end try/catch
	} // end onConnectionFailed()

	/**
	 * Shows a toast message.
	 */
	public void showMessage(String message) {
		Toast.makeText(this, message, Toast.LENGTH_LONG).show();
	} // end showMessage()

	/**
	 * Getter for the {@code GoogleApiClient}.
	 */
	public GoogleApiClient getGoogleApiClient() {
		return mGoogleApiClient;
	} // end getGoogleApiClient()

} // end class SaveService
