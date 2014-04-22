package com.googilyboogily.deskytexty;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SmsMessage;

import com.googilyboogily.deskytexty.services.SaveReceivedSMSService;

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

			// Create the SaveReceivedSMSService intent
			Intent serviceIntent = new Intent(context, SaveReceivedSMSService.class);

			// Push the mobile number and message body to the intent
			serviceIntent.putExtra("mobileNum", mobileNum);
			serviceIntent.putExtra("messageBody", messageBody);

			// Start the service up!
			context.startService(serviceIntent);
		} // end if
	} // end onReceive()


} // end class SmsListener
