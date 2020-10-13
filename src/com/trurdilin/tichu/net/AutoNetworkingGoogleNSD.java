package com.trurdilin.tichu.net;

import java.lang.reflect.Method;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdManager.DiscoveryListener;
import android.net.nsd.NsdManager.RegistrationListener;
import android.net.nsd.NsdManager.ResolveListener;
import android.net.nsd.NsdServiceInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.util.Log;

public class AutoNetworkingGoogleNSD {
	private static NsdManager mNsdManager;
	private static ResolveListener mResolveListener;
	private static DiscoveryListener mDiscoveryListener;
	private static Map<String,NsdServiceInfo> activeHosts = new HashMap<String,NsdServiceInfo>();
	private static Map<String,NsdServiceInfo> activeClients = new HashMap<String,NsdServiceInfo>();

	private static DiscoveryHandler discoveryHandler = null;
	
	private static final String TAG = "DBG:"+AutoNetworkingGoogleNSD.class.getSimpleName()+":";
	private static final String SERVICE_NAME = "TichuAndroid";
	private static final String SERVICE_NAME_HOST = "Host";
	private static final String SERVICE_NAME_CLIENT = "Client";
	private static final String SERVICE_TYPE = "_xml._udp.";
	
	private static Object serviceInfoLock = new Object();
	private static Object discoveryHandlerLock = new Object();
	
	static{
		initializeDiscoveryListener();
		initializeResolveListener();
	}
	
	public static void startService(final MessageReceiver l, final boolean isHost, final Context context, final RegistrationListener handler) {
		
	    //execute on another Thread (not on UI one)
	    //less load on UI Thread, need to wait for Listener initialization
		Runnable r = new Runnable() {
			@Override
			public void run() {
				while (l.getPort() <= 0)
					try {
						Thread.sleep(NetSettings.sleepTime);
					}
					catch (InterruptedException e) {
					}
				NsdServiceInfo serviceInfo  = new NsdServiceInfo();

			    serviceInfo.setServiceName(SERVICE_NAME+l.getName()+(isHost?SERVICE_NAME_HOST:SERVICE_NAME_CLIENT));
			    serviceInfo.setServiceType(SERVICE_TYPE);
			    serviceInfo.setPort(l.getPort());
			    
			    mNsdManager = (NsdManager)context.getSystemService(Context.NSD_SERVICE);
			    
			    mNsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, handler);
			}
		};
		AsyncTask.execute(r);
	}
	
	public static void startDiscovery(Context context, DiscoveryHandler handler){
		synchronized(discoveryHandlerLock){
	    	discoveryHandler = handler;
	    }
		
		mNsdManager = (NsdManager)context.getSystemService(Context.NSD_SERVICE);
		
		mNsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, mDiscoveryListener);
	}
	
	public static void stopService(RegistrationListener handler){
		mNsdManager.unregisterService(handler);
	}
	
	public static void stopDiscovery(DiscoveryHandler handler){
		if (handler != null)
			synchronized(discoveryHandlerLock){
		    	discoveryHandler = handler;
		    }
		mNsdManager.stopServiceDiscovery(mDiscoveryListener);
	}
	
	private static void initializeDiscoveryListener() {
		mDiscoveryListener = new NsdManager.DiscoveryListener() {

	        @Override
	        public void onDiscoveryStarted(String regType) {
	        	synchronized(discoveryHandlerLock){
	        		if (discoveryHandler != null)
	        			discoveryHandler.discoveryStarted();
	    	    }
	        }

	        @Override
	        public void onServiceFound(NsdServiceInfo service) {
	            Log.d(TAG, "onServiceFound " + service);
	            if (!service.getServiceType().equals(SERVICE_TYPE)) {
	                Log.d(TAG, "Unknown Service Type: " + service.getServiceType());
	            }
	            else if (service.getServiceName().contains(SERVICE_NAME)){
	                mNsdManager.resolveService(service, mResolveListener);
	            }
	        }

	        @Override
	        public void onServiceLost(NsdServiceInfo service) {
	        	Log.e(TAG, "onServiceLost " + service);
	        	if (discoveryHandler != null && service.getServiceName().contains(SERVICE_NAME)){
	        		String name = service.getServiceName().replace(SERVICE_NAME, "");
	        		boolean isHost = name.contains(SERVICE_NAME_HOST);
	        		name = name.split(isHost?SERVICE_NAME_HOST:SERVICE_NAME_CLIENT)[0];
	        		
	        		synchronized(serviceInfoLock){
	        			if (isHost)
	        				activeHosts.remove(name);
	        			else
	        				activeClients.remove(name);
		        	}
		            synchronized(discoveryHandlerLock){
		            	discoveryHandler.lost(name, isHost, service);
		            }
		        }
	        }

	        @Override
	        public void onDiscoveryStopped(String serviceType) {
	        	synchronized(discoveryHandlerLock){
	        		if (discoveryHandler != null){
		    	    	discoveryHandler.discoveryStopped();
		    	    	discoveryHandler = null;
	        		}
	    	    }
	        }

	        @Override
	        public void onStartDiscoveryFailed(String serviceType, int errorCode) {
	        }

	        @Override
	        public void onStopDiscoveryFailed(String serviceType, int errorCode) {
	        }
	    };
	}
	
	private static void initializeResolveListener() {
	    mResolveListener = new NsdManager.ResolveListener() {

	        @Override
	        public void onResolveFailed(NsdServiceInfo service, int errorCode) {
	            Log.e(TAG, "onResolveFailed " + errorCode + " " + service);
	        }

	        @Override
	        public void onServiceResolved(NsdServiceInfo service) {
	            Log.e(TAG, "onServiceResolved " + service);
        		String name = service.getServiceName().replace(SERVICE_NAME, "");
        		boolean isHost = name.contains(SERVICE_NAME_HOST);
        		name = name.split(isHost?SERVICE_NAME_HOST:SERVICE_NAME_CLIENT)[0];

        		synchronized(serviceInfoLock){
        			if (isHost)
        				activeHosts.put(name, service);
        			else
        				activeClients.put(name, service);
	            }
	            synchronized(discoveryHandlerLock){
	            	if (discoveryHandler != null)
	            		discoveryHandler.discovered(name, isHost, service);
	    	    }
	        }
	    };
	}
	
	public static NsdServiceInfo getHostInfo(String name){
		synchronized(serviceInfoLock){
			return activeHosts.get(name);
        }
	}
	
	public static NsdServiceInfo getClientInfo(String name){
		synchronized(serviceInfoLock){
			return activeClients.get(name);
        }
	}
	
	public static Set<String> getHosts(){
		synchronized(serviceInfoLock){
			return new HashSet<String>(activeHosts.keySet());
        }
	}
	
	public static Set<String> getClients(){
		synchronized(serviceInfoLock){
			return new HashSet<String>(activeClients.keySet());
        }
	}
	
	//helper methods to detect connectivity
	public static String wifiIpAddressString(Context context) {
		InetAddress address = wifiIpAddress(context);
		if (address == null)
			return null;

	    return address.getHostAddress();
	}
	
	public static InetAddress wifiIpAddress(Context context) {
		if (!wifiConnected(context))
			return null;
		
	    WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);

	    int ipAddress = wifiManager.getConnectionInfo().getIpAddress();

	    // Endian correction
	    if (ByteOrder.nativeOrder().equals(ByteOrder.LITTLE_ENDIAN)) {
	        ipAddress = Integer.reverseBytes(ipAddress);
	    }

	    byte[] ipByteArray = BigInteger.valueOf(ipAddress).toByteArray();

	    try {
			return InetAddress.getByAddress(ipByteArray);
		}
	    catch (UnknownHostException e) {
			return null;
		}
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
	
	//interfaces for callback
	public static interface DiscoveryHandler{
		public void discoveryStarted();
		public void discovered(String name, boolean isHost, NsdServiceInfo serviceInfo);
		public void lost(String name, boolean isHost, NsdServiceInfo serviceInfo);
		public void discoveryStopped();
	}
}
