package com.trurdilin.tichu.net;

import java.lang.reflect.Method;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;

public class NetTools {
	
	//helper methods to detect connectivity
	public static String wifiIpAddressString(Context context) {
		InetAddress address = NetTools.wifiIpAddress(context);
		if (address == null)
			return null;

	    return address.getHostAddress();
	}
	
	public static InetAddress wifiIpAddress(Context context) {
		if (!wifiConnected(context))
			return null;
		
		InetAddress ret = null;
		try {
			for (NetworkInterface intf : Collections.list(NetworkInterface.getNetworkInterfaces())){
				for (InetAddress addr : Collections.list(intf.getInetAddresses())){
					if (!addr.isLoopbackAddress() && addr instanceof Inet4Address){
						ret = addr;
					}
				}
			}
		} 
		catch (Exception ex) {
		}
		
		return ret;
	}
	
	public static NetworkInterface wifiInterface(Context context) {
		if (!wifiConnected(context))
			return null;
		
		try {
			for (NetworkInterface intf : Collections.list(NetworkInterface.getNetworkInterfaces())){
				if (intf.getName().contains("wlan"))
					return intf;
			}
		} 
		catch (Exception ex) {
		}
		
		return null;
	}
	
	public static boolean wifiConnected(Context context){
	    ConnectivityManager connManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
	    NetworkInfo info = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);

	    if (info.isConnected())
	    	return true;
	    
	    try{
	    	WifiManager wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
	        Method method = wifi.getClass().getDeclaredMethod("isWifiApEnabled");
	        method.setAccessible(true);
	        return (Boolean) method.invoke(wifi);
	    }
	    catch (final Throwable ignored){
	    	ignored.printStackTrace();
	    }

	    return false;
	    
	}

}
