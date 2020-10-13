package com.trurdilin.tichu.net;

import java.io.IOException;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.MulticastLock;
import android.os.AsyncTask;


public class AutoNetworkingJmDNS {
	private static Map<String,ServiceInfo> activeHosts = new HashMap<String,ServiceInfo>();
	private static Map<String,ServiceInfo> activeClients = new HashMap<String,ServiceInfo>();
	private static JmDNS jmdns = null;
	private static int jmdnsUsage = 0;

	private static ServiceListener serviceListener = null;
	private static DiscoveryListener discoveryListener = null;
	
	private static final String SERVICE_NAME = "TichuAndroid";
	private static final String SERVICE_TYPE = "_tichu._tcp.local.";
	private static final String SERVICE_NAME_HOST = "Host";
	private static final String SERVICE_NAME_CLIENT = "Client";
	private static final String MULTICASTLOCKNAME = "TichuMulticastLock";

	private static MulticastLock multicastLock = null;
	private static Object discoveryListenerLock = new Object();
	private static Object serviceInfoLock = new Object();
	private static Object jmdnsLock = new Object();
	
	static{
		initializeDiscoveryListener();
	}
	
	public static void startService(final Context context, final MessageReceiver l, final boolean isHost, final RegistrationListener handler) {
		
	    //execute on another Thread: less load on UI Thread, need to wait for Listener initialization
		AsyncTask.execute(new Runnable() {
			@Override
			public void run() {
				while (l.getPort() <= 0)
					try {
						Thread.sleep(NetSettings.sleepTime);
					}
					catch (InterruptedException e) {
					}
				
				try {
					modifyJmDNSUsage(context, true);
				}
				catch (Exception e) {
					handler.actionFailed(e);
					return;
				}
				
				String name = SERVICE_NAME+l.getName()+(isHost?SERVICE_NAME_HOST:SERVICE_NAME_CLIENT);
				ServiceInfo serviceInfo = ServiceInfo.create(SERVICE_TYPE, name, l.getPort(), "");
				
				try {
					JmDNS tmp;
					synchronized(jmdnsLock){
						tmp = jmdns;
					}
					tmp.registerService(serviceInfo);
				}
				catch (Exception e) {
					try {
						modifyJmDNSUsage(context, false);
					} 
					catch (IOException e1) {
					}
					handler.actionFailed(e);
					return;
				}
				
				handler.actionSuccess(serviceInfo);
			}
		});
	}
	
	public static void startDiscovery(final Context context, final DiscoveryListener handler){
	    
		//execute on another Thread : less load on UI Thread
		AsyncTask.execute(new Runnable() {
			@Override
			public void run() {
				
				try {
					modifyJmDNSUsage(context, true);
				}
				catch (Exception e) {
					handler.actionFailed(e);
					return;
				}
				
				try {
					JmDNS tmp;
					synchronized(jmdnsLock){
						tmp = jmdns;
					}
					tmp.addServiceListener(SERVICE_TYPE, serviceListener);
				}
				catch (Exception e) {
					try {
						modifyJmDNSUsage(context, false);
					} 
					catch (IOException e1) {
					}
					handler.actionFailed(e);
					return;
				}
				
				synchronized (discoveryListenerLock) {
					discoveryListener = handler; 
				}
				handler.actionSuccess();
			}
		});
	}
	
	public static void stopService(final Context context, final ServiceInfo serviceInfo, final RegistrationListener handler){
		//execute on another Thread : less load on UI Thread
		AsyncTask.execute(new Runnable() {
			@Override
			public void run() {
				JmDNS tmp;
				synchronized(jmdnsLock){
					tmp = jmdns;
				}
				tmp.unregisterService(serviceInfo);
				
				try {
					modifyJmDNSUsage(context, false);
				} 
				catch (IOException e) {
					handler.actionFailed(e);
				}
				handler.actionSuccess(serviceInfo);
			}
			
		});
	}
	
	public static void stopDiscovery(final Context context, final DiscoveryListener handler){
		AsyncTask.execute(new Runnable() {
			@Override
			public void run() {
				try {
					JmDNS tmp;
					synchronized(jmdnsLock){
						tmp = jmdns;
					}
					tmp.removeServiceListener(SERVICE_TYPE, serviceListener);
				}
				catch (Exception e) {
					try {
						modifyJmDNSUsage(context, false);
					} 
					catch (IOException e1) {
					}
					handler.actionFailed(e);
					return;
				}
				handler.actionSuccess();
			}
		});
	}
	
	private static void initializeDiscoveryListener() {
		
		serviceListener = new ServiceListener() {
			
	        public void serviceResolved(ServiceEvent ev) {
	        	ServiceInfo service = ev.getInfo();
        		String name = service.getName().replace(SERVICE_NAME, "");
        		boolean isHost = name.contains(SERVICE_NAME_HOST);
        		name = name.split(isHost?SERVICE_NAME_HOST:SERVICE_NAME_CLIENT)[0];

        		synchronized(serviceInfoLock){
        			if (isHost)
        				activeHosts.put(name, service);
        			else
        				activeClients.put(name, service);
	            }
	            synchronized(discoveryListenerLock){
	            	if (discoveryListener != null)
	            		discoveryListener.discovered(name, isHost, service);
	    	    }
	        }
	        
	        public void serviceRemoved(ServiceEvent ev) {
	        	String evName = ev.getName();
	        	if (discoveryListener != null && evName.contains(SERVICE_NAME)){
	        		String name = evName.replace(SERVICE_NAME, "");
	        		boolean isHost = name.contains(SERVICE_NAME_HOST);
	        		name = name.split(isHost?SERVICE_NAME_HOST:SERVICE_NAME_CLIENT)[0];
	        		
	        		synchronized(serviceInfoLock){
	        			if (isHost)
	        				activeHosts.remove(name);
	        			else
	        				activeClients.remove(name);
		        	}
		            synchronized(discoveryListenerLock){
		            	discoveryListener.lost(name, isHost, ev.getInfo());
		            }
		        }
	        }
	        public void serviceAdded(ServiceEvent event) {
	            jmdns.requestServiceInfo(event.getType(), event.getName(), 1);
	        }
		};
	}
	
	private static void modifyJmDNSUsage(Context context, boolean add) throws IOException{
		if (add){
			synchronized(jmdnsLock){
				if (jmdns == null){
					setMulticastLock(context, true);
					InetAddress inet = NetTools.wifiIpAddress(context);
					jmdns = JmDNS.create(inet, ""+new Random().nextDouble());
				}
				jmdnsUsage ++;
		    }
		}
		else{
			synchronized(jmdnsLock){
				if (jmdnsUsage == 0){
					jmdns.close();
					jmdns = null;
					setMulticastLock(context, false);
				}
				jmdnsUsage --;
		    }
		}
	}
	
	private static synchronized void setMulticastLock(Context context, boolean acquire){
		if (acquire){
			android.net.wifi.WifiManager wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
			multicastLock = wifi.createMulticastLock(MULTICASTLOCKNAME);
			multicastLock.setReferenceCounted(true);
			multicastLock.acquire();
		}
		else if (multicastLock != null){
			multicastLock.release();
		}
	}
	
	//serviceInfo map accessors
	public static ServiceInfo getHostInfo(String name){
		synchronized(serviceInfoLock){
			return activeHosts.get(name);
        }
	}
	
	public static ServiceInfo getClientInfo(String name){
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
	
	//interfaces for callback
	public static interface DiscoveryListener{
		public void discovered(String name, boolean isHost, ServiceInfo serviceInfo);
		public void lost(String name, boolean isHost, ServiceInfo serviceInfo);
		public void actionSuccess();
		public void actionFailed(Exception e);
	}
	
	public static interface RegistrationListener{
		public void actionFailed(Exception e);
		public void actionSuccess(ServiceInfo serviceInfo);
	}
}	
