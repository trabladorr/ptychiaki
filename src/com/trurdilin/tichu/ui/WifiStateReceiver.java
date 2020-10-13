package com.trurdilin.tichu.ui;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;

import com.trurdilin.tichu.net.AutoNetworkingGoogleNSD;

//receives changes in wifi connectivity, calls callback methods on activity it was registered on
public class WifiStateReceiver extends BroadcastReceiver{
	private static final IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
	
	private static WifiStateHandler handler;
	private static Activity activity;
	private static WifiStateReceiver receiver;
	
	private static final Object superLock = new Object();
		
	public static void register(Activity activity, WifiStateHandler handler){
		synchronized (superLock) {
			try{
				WifiStateReceiver.activity.unregisterReceiver(receiver);
			}
			catch (Exception e){
			}
			WifiStateReceiver.activity = activity;
			WifiStateReceiver.handler = handler;
			WifiStateReceiver.receiver = new WifiStateReceiver();
			activity.registerReceiver(receiver, filter);
		}
	}
	
	public static void unregister(){
		synchronized (superLock) {
			try{
				WifiStateReceiver.activity.unregisterReceiver(receiver);
			}
			catch (Exception e){
			}
			activity = null;
			receiver = null;
			handler = null;
		}
	}
	
	@Override
	public void onReceive(Context context, Intent intent) {
		if(AutoNetworkingGoogleNSD.wifiConnected(context))
			synchronized (superLock) {
				handler.wifiEnabled();
			}
		else
			synchronized (superLock) {
				handler.wifiDisabled();
			}
	}
	 
	 //callback interface
	 public static interface WifiStateHandler{
		 public void wifiEnabled();
		 public void wifiDisabled();
	 }

}
