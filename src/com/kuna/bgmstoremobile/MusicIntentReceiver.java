package com.kuna.bgmstoremobile;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

public class MusicIntentReceiver extends BroadcastReceiver {
	private Handler h;
	
	public MusicIntentReceiver(Context c, Handler h) {
		this.h = h;
	    IntentFilter filter = new IntentFilter(Intent.ACTION_HEADSET_PLUG);
	    c.registerReceiver(this, filter);
	}
	
    @Override public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(Intent.ACTION_HEADSET_PLUG)) {
            int state = intent.getIntExtra("state", -1);
            switch (state) {
            case 0:
                Log.d("BGMStoreMobile", "Headset is unplugged");
                h.obtainMessage(0).sendToTarget();
                break;
            case 1:
                Log.d("BGMStoreMobile", "Headset is plugged");
                h.obtainMessage(1).sendToTarget();
                break;
            default:
                Log.d("BGMStoreMobile", "I have no idea what the headset state is");
            }
        }
    }
}