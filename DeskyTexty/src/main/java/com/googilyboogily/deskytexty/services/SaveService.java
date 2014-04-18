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
import android.text.format.Time;
import android.widget.Toast;
import android.os.Process;

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
		}

		@Override
		public void handleMessage(Message msg) {
			// Do service stuff!
			SaveToDrive();


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

		//
		Query query = new Query.Builder().addFilter(Filters.and(Filters.eq(SearchableField.TITLE, "DESKYTEXTYAPPFOLDER"),
													Filters.eq(SearchableField.MIME_TYPE, "application/vnd.google-apps.folder"),
													Filters.eq(SearchableField.TRASHED, false))).build();

		//
		// TODO: Methodize everything
		//


		// Query the root folder for the app folder and wait to finish
		DriveApi.MetadataBufferResult queryResult = Drive.DriveApi.query(getGoogleApiClient(), query).await();

		// TODO: Put in better place
		DriveApi.MetadataBufferResult listChildrenResult;

		// If there is at least one result
		// TODO: Add else statement

		//if(queryResult.getMetadataBuffer().getCount() != 0) {
		appFolderId = queryResult.getMetadataBuffer().get(0).getDriveId();

		appFolder = Drive.DriveApi.getFolder(getGoogleApiClient(), appFolderId);

		listChildrenResult = appFolder.listChildren(getGoogleApiClient()).await();
		//} // end if



		int numOfChildren = listChildrenResult.getMetadataBuffer().getCount();

		Boolean folderExists = false;

		String[] temp = new String[numOfChildren];

		for(int count = 0; count < numOfChildren; count++) {
			temp[count] = listChildrenResult.getMetadataBuffer().get(count).getTitle();

			if(listChildrenResult.getMetadataBuffer().get(count).getTitle().equals(mobileNum)) {
				folderExists = true;
				numFolder = Drive.DriveApi.getFolder(getGoogleApiClient() ,listChildrenResult.getMetadataBuffer().get(count).getDriveId());
			} // end if
		} // end for


		MetadataChangeSet changeSet;
		DriveFolder.DriveFileResult driveFileResult;
		if(folderExists) {
			DriveApi.ContentsResult newContentsResult = Drive.DriveApi.newContents(getGoogleApiClient()).await();

			// TODO: Probably get the time that the message was received, not the current time
			// Get the current time
			String currentTime;
			Time now = new Time(Time.getCurrentTimezone());
			now.setToNow();
			// Link for formatting: http://www.cplusplus.com/reference/ctime/strftime/
			currentTime = now.format("%D-%T");

			changeSet = new MetadataChangeSet.Builder()
				.setTitle(currentTime + ".msg")
				.setMimeType("text/plain")
				.build();

			driveFileResult = numFolder.createFile(getGoogleApiClient(), changeSet, newContentsResult.getContents()).await();
		} else {
			changeSet = new MetadataChangeSet.Builder().setTitle(mobileNum).build();
			DriveFolder.DriveFolderResult driveFolderResult = appFolder.createFolder(getGoogleApiClient(), changeSet).await();

			numFolder = driveFolderResult.getDriveFolder();

			DriveApi.ContentsResult newContentsResult = Drive.DriveApi.newContents(getGoogleApiClient()).await();

			// TODO: Probably get the time that the message was received, not the current time
			// Get the current time
			String currentTime;
			Time now = new Time(Time.getCurrentTimezone());
			now.setToNow();
			// Link for formatting: http://www.cplusplus.com/reference/ctime/strftime/
			currentTime = now.format("%D-%T");

			changeSet = new MetadataChangeSet.Builder()
				.setTitle(currentTime + ".msg")
				.setMimeType("text/plain")
				.build();

			driveFileResult = numFolder.createFile(getGoogleApiClient(), changeSet, newContentsResult.getContents()).await();
		} // end else/if


		showMessage("Created a file: " + driveFileResult.getDriveFile().getDriveId());

		fileToSave = driveFileResult.getDriveFile();

		DriveApi.ContentsResult contentsResult = fileToSave.openContents(mGoogleApiClient, DriveFile.MODE_WRITE_ONLY, null).await();

		Contents contents = contentsResult.getContents();

		try {
			contents.getOutputStream().write((messageBody).getBytes());
		} catch(IOException e) {
			e.printStackTrace();
		} // end try/catch

		Status statusResult = fileToSave.commitAndCloseContents(mGoogleApiClient, contents).await();

		if (statusResult.isSuccess()) {
			showMessage("Successfully committed file");
			mGoogleApiClient.disconnect();
		} else {
			showMessage("Failed to commit file");
		} // end else/if

	} // end onConnected()

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
