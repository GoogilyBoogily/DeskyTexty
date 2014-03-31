package com.googilyboogily.deskytexty.tasks;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.drive.Contents;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveApi.ContentsResult;
import com.google.android.gms.drive.DriveFile;
import com.google.android.gms.drive.DriveId;
import com.google.android.gms.drive.DriveResource.MetadataResult;
import com.google.android.gms.drive.MetadataChangeSet;

import android.os.AsyncTask;

/**
 * An async task to open, make changes to and close a file.
 */
public abstract class SaveSmsAsyncTask
	extends AsyncTask<DriveId, Boolean, com.google.android.gms.common.api.Status> {

	private static final String TAG = "SaveSmsAsyncTask";

	private GoogleApiClient mClient;

	/**
	 * Constructor.
	 *
	 * @param client A connected {@code GoogleApiClient} instance.
	 */
	public SaveSmsAsyncTask(GoogleApiClient client) {
		mClient = client;
	}

	/**
	 * Handles the editing to file metadata and contents.
	 */
	public abstract Changes edit(Contents contents);

	/**
	 * Opens contents for the given file, executes the editing tasks, saves the
	 * metadata and content changes.
	 */
	@Override
	protected com.google.android.gms.common.api.Status doInBackground(DriveId... params) {
		DriveFile file = Drive.DriveApi.getFile(mClient, params[0]);

		PendingResult<ContentsResult> openContentsResult =
			file.openContents(mClient, DriveFile.MODE_WRITE_ONLY, null);

		if (!openContentsResult.await().getStatus().isSuccess()) {
			return openContentsResult.await().getStatus();
		} // end if

		Changes changes = edit(openContentsResult.await().getContents());
		PendingResult<MetadataResult> metadataResult = null;

		PendingResult<com.google.android.gms.common.api.Status>
			closeContentsResult = null;

		if (changes.getMetadataChangeSet() != null) {
			metadataResult = file.updateMetadata(mClient, changes.getMetadataChangeSet());
			if (!metadataResult.await().getStatus().isSuccess()) {
				return metadataResult.await().getStatus();
			} // end if
		} // end if

		if (changes.getContents() != null) {
			closeContentsResult = file.commitAndCloseContents(mClient, changes.getContents());
			closeContentsResult.await();
		} // end if

		return closeContentsResult.await().getStatus();
	}

	/**
	 * Represents the delta of the metadata changes and keeps a pointer to the file
	 * contents to be stored permanently.
	 */
	public class Changes {
		private MetadataChangeSet mMetadataChangeSet;
		private Contents mContents;

		public Changes(MetadataChangeSet metadataChangeSet, Contents contents) {
			mMetadataChangeSet = metadataChangeSet;
			mContents = contents;
		} // end Chances()

		public MetadataChangeSet getMetadataChangeSet() {
			return mMetadataChangeSet;
		} // end getMetadataChangeSet()

		public Contents getContents() {
			return mContents;
		} // end getContents()
	} // end class Changes
} // end class SaveSmsAsyncTask
