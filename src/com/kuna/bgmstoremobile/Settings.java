package com.kuna.bgmstoremobile;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public class Settings {
	public static boolean PauseWhenPlugout = true;
	
	public static void LoadSettings(Context c) {
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(c);
		PauseWhenPlugout = settings.getBoolean("pausewhenplugout", true);
	}
	
	public static void SaveSettings(Context c) {
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(c);
		SharedPreferences.Editor editor = settings.edit();
		editor.putBoolean("pausewhenplugout", PauseWhenPlugout);
		editor.commit();
	}
}
