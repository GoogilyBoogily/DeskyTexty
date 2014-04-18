package com.googilyboogily.deskytexty;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.widget.Toast;

import com.googilyboogily.deskytexty.services.SaveService;
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

import android.text.format.Time;

import java.io.IOException;

public class SmsListener extends BroadcastReceiver {
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
				str += msgs[i].getMessageBody();

				messageBody = msgs[i].getMessageBody();

				str += "\n";

				mobileNum = msgs[i].getOriginatingAddress();
			} // end for

			//
			Intent serviceIntent = new Intent(context, SaveService.class);

			serviceIntent.putExtra("mobileNum", mobileNum);
			serviceIntent.putExtra("messageBody", messageBody);

			context.startService(serviceIntent);
		} // end if
	} // end onReceive()


} // end class SmsListener
