package com.trurdilin.tichu.ui;

import java.util.Set;

import javax.jmdns.ServiceInfo;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.trurdilin.tichu.R;
import com.trurdilin.tichu.net.AutoNetworkingJmDNS;
import com.trurdilin.tichu.net.AutoNetworkingJmDNS.DiscoveryListener;
import com.trurdilin.tichu.net.NetSettings;
import com.trurdilin.tichu.net.NetTools;
import com.trurdilin.tichu.ui.WifiStateReceiver.WifiStateHandler;

public class FindServerActivity extends Activity implements DiscoveryListener, WifiStateHandler{
	
	private String userName = "";
	private LinearLayout serverListView;
	private boolean discoveryState = false;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.find_activity);
		
		serverListView = (LinearLayout)findViewById(R.id.find_server_list);

		SharedPreferences pref = getSharedPreferences(ConfigActivity.PREF_NAME, 0);
		userName = pref.getString(ConfigActivity.PREF_USER_NAME, "");
		
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.find, menu);
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		int id = item.getItemId();
		if (id == R.id.menu_find_action_refresh) {
			if (!discoveryState){
				discoveryState = true;
				AutoNetworkingJmDNS.startDiscovery(this, this);
				findViewById(R.id.find_progressbar).setVisibility(View.VISIBLE);
				((TextView)findViewById(R.id.find_text_fixed_choice)).setText(R.string.find_fixed_initializing);
			}
			return true;
		}
		return super.onOptionsItemSelected(item);
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		
		if (!NetTools.wifiConnected(this)){
			finish();
			return;
		}
		
		discoveryState = true;

		AutoNetworkingJmDNS.startDiscovery(this, this);
		findViewById(R.id.find_progressbar).setVisibility(View.VISIBLE);
		((TextView)findViewById(R.id.find_text_fixed_choice)).setText(R.string.find_fixed_initializing);
		
		Set<String> activeHosts = AutoNetworkingJmDNS.getHosts();
		for (String host:activeHosts)
			addHost(host);
		
		WifiStateReceiver.register(this, this);
		

	}
	
	@Override
	protected void onPause() {
		super.onPause();
		try{
			AutoNetworkingJmDNS.stopDiscovery(this, this);
		}
		catch (Exception e){
			if (NetSettings.debug)
				e.printStackTrace();
		}
		
		Set<String> activeHosts = AutoNetworkingJmDNS.getHosts();
		for (String host:activeHosts)
			removeHost(host);
		
		WifiStateReceiver.unregister();
		
		discoveryState = false;
			
	}

	
	
	private void addHost(final String name){
		FindServerActivity.this.runOnUiThread(new Runnable() {

		    @Override
		    public void run() {
		        
		        if (name.equals(userName) || serverListView.findViewWithTag(name) != null || (AutoNetworkingJmDNS.getHostInfo(name) != null && AutoNetworkingJmDNS.getHostInfo(name).equals(NetTools.wifiIpAddress(FindServerActivity.this))))
		        	return;
		        
		        @SuppressLint("InflateParams")
		        RelativeLayout childView = (RelativeLayout)LayoutInflater.from(FindServerActivity.this).inflate(R.layout.find_activity_game_entry, null);
		        childView.setTag(name);
		        String fullServerName = name+(name.charAt(name.length()-1) == 's'?getString(R.string.find_server_name_prefix_ends_in_s):getString(R.string.find_server_name_prefix_no_s_in_end)); 
		        ((TextView)childView.findViewById(R.id.find_server_choice_title)).setText(fullServerName);
		        Button b = (Button)childView.findViewById(R.id.find_server_choice_button);
		        b.setOnClickListener(new OnClickListener() {
					
					@Override
					public void onClick(View v) {

						Intent waitIntent = new Intent(getApplicationContext(), WaitingActivity.class);
						waitIntent.setAction("android.intent.action.MAIN");
						waitIntent.putExtra(StartActivity.INTENT_MODE_HOST, false);
						waitIntent.putExtra(StartActivity.INTENT_HOST_NAME, name);
						waitIntent.putExtra(StartActivity.INTENT_HOST_ADDRESS, AutoNetworkingJmDNS.getHostInfo(name).getInet4Addresses()[0].getHostAddress());
						waitIntent.putExtra(StartActivity.INTENT_HOST_PORT, AutoNetworkingJmDNS.getHostInfo(name).getPort());
						startActivity(waitIntent);
					}
				});
		        serverListView.addView(childView);
		        
		    }
		});
	}
	
	private void removeHost(final String name){
		FindServerActivity.this.runOnUiThread(new Runnable() {

		    @Override
		    public void run() {
		        serverListView.removeView(serverListView.findViewWithTag(name));
		    }
		});
	}

	@Override
	public void wifiEnabled() {
	}

	@Override
	public void wifiDisabled() {
		finish();
	}

	@Override
	public void discovered(String name, boolean isHost, ServiceInfo serviceInfo) {
		addHost(name);
		
	}

	@Override
	public void lost(String name, boolean isHost, ServiceInfo serviceInfo) {
		removeHost(name);
		
	}

	@Override
	public void actionSuccess() {
		FindServerActivity.this.runOnUiThread(new Runnable() {

		    @Override
		    public void run() {
	    		findViewById(R.id.find_progressbar).setVisibility(View.GONE);
				
	    		if (discoveryState){
					((TextView)findViewById(R.id.find_text_fixed_choice)).setText(R.string.find_fixed_choice);
					discoveryState = false;
				}
		    }
		});
		
		
	}

	@Override
	public void actionFailed(Exception e) {
		Log.e(this.getClass().getSimpleName(),"actionFailed: "+Log.getStackTraceString(e));
		
	}
}
