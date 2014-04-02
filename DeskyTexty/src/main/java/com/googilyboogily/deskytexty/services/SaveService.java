package com.googilyboogily.deskytexty.services;

import android.app.Activity;
import android.app.IntentService;
import android.content.Intent;
import android.content.IntentSender;
import android.os.Bundle;
import android.widget.Toast;

import com.googilyboogily.deskytexty.MainActivity;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.drive.Drive;

public class SaveService extends IntentService implements GoogleApiClient.ConnectionCallbacks,
                                                          GoogleApiClient.OnConnectionFailedListener {

	GoogleApiClient mGoogleApiClient;
	protected static final int REQUEST_CODE_RESOLUTION = 1;

	/**
	* A constructor is required, and must call the super IntentService(String)
	* constructor with a name for the worker thread.
	*/
	public SaveService() {
		super("SaveService");
	} // end SaveService()

	/**
	* The IntentService calls this method from the default worker thread with
	* the intent that started the service. When this method returns, IntentService
	* stops the service, as appropriate.
	*/
	@Override
	protected void onHandleIntent(Intent intent) {
		// Normally we would do some work here, like download a file.
		// For our sample, we just sleep for 5 seconds.
		long endTime = System.currentTimeMillis() + 3 * 1000;
			while (System.currentTimeMillis() < endTime) {
				synchronized(this) {
					try {
						showMessage("Service started running.");
						// Holy shit Java, you need to chill
						((Object) this).wait(endTime - System.currentTimeMillis());
						showMessage("Service finished running.");

						// Stop the service
						stopSelf();
					} catch (Exception e) {

					} // end try/catch
				} // end synchronized
			} // end while
	} // end onHandleIntent()

	public void SaveToDrive() {
		mGoogleApiClient = new GoogleApiClient.Builder(MainActivity.getAppContext())
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


	}

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
			GooglePlayServicesUtil.getErrorDialog(result.getErrorCode(),(Activity)MainActivity.getAppContext(), 0).show();
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
		Toast.makeText(this, message, Toast.LENGTH_LONG).show();
	}

	/**
	 * Getter for the {@code GoogleApiClient}.
	 */
	public GoogleApiClient getGoogleApiClient() {
		return mGoogleApiClient;
	}
} // end class SaveService
