package com.trurdilin.tichu.ui;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.jmdns.ServiceInfo;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.trurdilin.tichu.R;
import com.trurdilin.tichu.net.AutoNetworkingJmDNS;
import com.trurdilin.tichu.net.AutoNetworkingJmDNS.RegistrationListener;
import com.trurdilin.tichu.net.Message;
import com.trurdilin.tichu.net.Message.CompoundMessage;
import com.trurdilin.tichu.net.Message.XMLNode;
import com.trurdilin.tichu.net.MessageCreator;
import com.trurdilin.tichu.net.MessageReceiver;
import com.trurdilin.tichu.net.MessageReceiver.MessageHandler;
import com.trurdilin.tichu.net.MessageSender;
import com.trurdilin.tichu.net.NetSettings;
import com.trurdilin.tichu.net.NetTools;
import com.trurdilin.tichu.net.rsa.CertificateManager;
import com.trurdilin.tichu.ui.WifiStateReceiver.WifiStateHandler;

public class WaitingActivity extends Activity implements RegistrationListener, MessageHandler, WifiStateHandler {
	
	private MessageReceiver l = null;
	private String userName = "";
	private ServiceInfo info = null;
	private boolean isHost = true;
	private String hostToConnect = null;
	private InetSocketAddress hostToConnectAddress = null;
	private LinearLayout playerListView;
	private ToggleButton readyToggle;
	
	private List<String> connectedPlayers = new CopyOnWriteArrayList<String>();
	private List<String> readyPlayers = new CopyOnWriteArrayList<String>();
	
	private static final SecureRandom r = new SecureRandom();
	private static final Object listenerLock = new Object();
	private static final Object infoLock = new Object();
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.waiting_activity);
	
		playerListView = (LinearLayout)findViewById(R.id.waiting_player_list);
		
		SharedPreferences pref = getSharedPreferences(ConfigActivity.PREF_NAME, 0);
		userName = pref.getString(ConfigActivity.PREF_USER_NAME, "");
		
		Intent startingIntent = getIntent();
		
		isHost = startingIntent.getBooleanExtra(StartActivity.INTENT_MODE_HOST, true);
		
		readyToggle = (ToggleButton)findViewById(R.id.waiting_button_ready);
		readyToggle.setChecked(false);
		readyToggle.setEnabled(false);
		
		if (!isHost){
			hostToConnect = startingIntent.getStringExtra(StartActivity.INTENT_HOST_NAME);
			try {
				hostToConnectAddress = new InetSocketAddress(
						InetAddress.getByName(startingIntent.getStringExtra(StartActivity.INTENT_HOST_ADDRESS)),
						startingIntent.getIntExtra(StartActivity.INTENT_HOST_PORT, 0));
			} 
			catch (UnknownHostException e) {
			}
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.empty , menu);
		return true;
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		
		if (!NetTools.wifiConnected(this)){
			returnToPreviousActivity();
			return;
		}
		
		boolean listenerOn;
		synchronized(listenerLock){
			listenerOn = l != null;
		}
		
		if (!listenerOn)
			new StartListenerTask().execute();
		
		WifiStateReceiver.register(this, this);
		

		
	}
	
	@Override
	protected void onPause() {
		super.onPause();
		WifiStateReceiver.unregister();
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();

		boolean listenerOn;
		synchronized(listenerLock){
			listenerOn = l != null;
		}
		if (listenerOn)
			l.stopListen();
		
		MessageSender.stopSending();
	}
	
	@Override
	public void onBackPressed() {
		findViewById(R.id.waiting_progressbar).setVisibility(View.VISIBLE);
		((TextView)findViewById(R.id.waiting_text_fixed_choice)).setText(R.string.waiting_fixed_service_closing);
		
		if (isHost){
			for (String player: connectedPlayers){
				removePlayer(player);
				if (!player.equals(userName))
					MessageSender.sendMessage(MessageCreator.leaveAccepted(player, userName));
			}
		}
		else{
			for (String player: connectedPlayers)
				removePlayer(player);
			MessageSender.sendMessage(MessageCreator.leaveGame(hostToConnect));
		}
		
		ServiceInfo tmpnfo;
		synchronized (infoLock) {
			tmpnfo = info;
		}
		AutoNetworkingJmDNS.stopService(this, tmpnfo, this);
	}

	public void returnToPreviousActivity(){
		finish();
	}
	
	private class StartListenerTask extends AsyncTask<Void, Void, Boolean>
	{
	    @Override
	    protected Boolean doInBackground(Void... params) {
	    	if (!startListener())
	    		return Boolean.FALSE;
	    	if (!startSender())
	    		return Boolean.FALSE;
	    	else
	    		return Boolean.TRUE;
	    }

	    @Override
	    protected void onPreExecute() {
			findViewById(R.id.waiting_progressbar).setVisibility(View.VISIBLE);
	    	if (isHost)
	    		((TextView)findViewById(R.id.waiting_text_fixed_choice)).setText(R.string.waiting_fixed_service_initializing);
	    	else
	    		((TextView)findViewById(R.id.waiting_text_fixed_choice)).setText(R.string.waiting_fixed_joining);
	    };
	    
	    @Override
	    protected void onPostExecute(Boolean result) {
	        super.onPostExecute(result);
	        if (!result){
	        	Toast.makeText(getApplicationContext(), R.string.waiting_toast_listener_failed, Toast.LENGTH_SHORT).show();
	        	returnToPreviousActivity();
	        }
	        if (!isHost){
	        	MessageSender.setAddress(hostToConnect, hostToConnectAddress);
	        	MessageReceiver tmpl;
 				synchronized(listenerLock){
	 				tmpl = l;
	 			}
 	 			MessageSender.sendMessage(MessageCreator.joinGame(hostToConnect, new InetSocketAddress(NetTools.wifiIpAddress(WaitingActivity.this),tmpl.getPort())));
	        }
	    }
	    
	    private boolean startListener(){
	    	try {
	 			MessageReceiver.init(r, userName, WaitingActivity.this);
	 			MessageReceiver tmp = MessageReceiver.bindInstance(WaitingActivity.this);
	 			tmp.startListen();
	 			AutoNetworkingJmDNS.startService(WaitingActivity.this, tmp, isHost, WaitingActivity.this);
	 			synchronized(listenerLock){
	 				l = tmp;
	 			}
	 			return true;
	 		}
	    	catch (Exception e) {
	 			Log.e(this.getClass().getSimpleName(),"Error initializing listener/ jndns registration:\n"+Log.getStackTraceString(e));
	 			try{
	 				MessageReceiver tmpl;
	 				synchronized(listenerLock){
		 				tmpl = l;
		 			}
	 				tmpl.stopListen();
		    		ServiceInfo tmpnfo;
		    		synchronized (infoLock) {
		    			tmpnfo = info;
					}
		    		AutoNetworkingJmDNS.stopService(WaitingActivity.this, tmpnfo, WaitingActivity.this);
	    		}
	    		catch (Exception ex) {
		 		}
	    	}	    	
	    	return false;
	 	}
	    
	    private boolean startSender(){
 			try {

 	 			MessageSender.init(WaitingActivity.this, userName);
 	 			MessageSender.startSending();

	 			return true;
	 		}
	    	catch (Exception e) {
	 			Log.e(this.getClass().getSimpleName(),"Error initializing message sender:\n"+Log.getStackTraceString(e));
	 			try{
	 				MessageSender.stopSending();
	    		}
	    		catch (Exception ex) {
		 		}
	    	}	    	
	    	return false;
	    }
	    
	}
	
	private void showAuthDialog(final Button b, final Certificate cert){
		AlertDialog.Builder alert = new AlertDialog.Builder(this);

		alert.setTitle(getString(R.string.alert_auth_name));
		alert.setMessage(getString(R.string.alert_auth_message));

		// Set an TextView to show the authentication codes
		final TextView authCodes = new TextView(this);
		
		authCodes.setTextColor(Color.MAGENTA);
		authCodes.setText(CertificateManager.authenticationString(cert, isHost));
		
		alert.setView(authCodes);

		alert.setPositiveButton(getString(R.string.alert_auth_button_ok), new DialogInterface.OnClickListener() {
		public void onClick(DialogInterface dialog, int whichButton) {
				try {
					CertificateManager.addAuthenticatedCertificate(cert, WaitingActivity.this);
					b.setVisibility(View.INVISIBLE);
				} 
				catch (Exception e) {
				}
			}
		});

		alert.setNegativeButton(getString(R.string.alert_auth_button_exit), new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
				try {
					CertificateManager.removeAuthenticatedCertificate(CertificateManager.extractUsername(cert), WaitingActivity.this);
					b.setVisibility(View.VISIBLE);
				} 
				catch (Exception e) {
				}
			}
		});

		alert.show();
	}
	
	private boolean addPlayer(final String player, final Certificate cert, String address, Integer port){
		if (connectedPlayers.size() == 4 || connectedPlayers.contains(player))
			return false;
		
		connectedPlayers.add(player);

		if (address != null){
			try {
				MessageSender.setAddress(player, new InetSocketAddress(InetAddress.getByName(address), port));
			} catch (Exception e) {
				return false;
			}
		}
		
		@SuppressLint("InflateParams")
		RelativeLayout childView = (RelativeLayout)LayoutInflater.from(WaitingActivity.this).inflate(R.layout.waiting_activity_player_entry, null);
        childView.setTag(player);
        ((TextView)childView.findViewById(R.id.waiting_choice_title)).setText(player);
        final Button kickButton = (Button)childView.findViewById(R.id.waiting_choice_kick_button);
    	
        kickButton.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				for (String playerIter: connectedPlayers)
					if (!playerIter.equals(userName) && !playerIter.equals(player))
						MessageSender.sendMessage(MessageCreator.leaveAccepted(playerIter, player));
				MessageSender.sendMessage(MessageCreator.joinRejected(player));
				removePlayer(player);
				removeReadyPlayers();
			}
		});
        
        if (!isHost || player.equals(userName))
        	kickButton.setVisibility(View.INVISIBLE);
        
        final Button authButton = (Button)childView.findViewById(R.id.waiting_choice_auth_button);
	
		authButton.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				MessageSender.sendMessage(MessageCreator.authRequest(player));
				showAuthDialog(authButton, cert);
			}
		});
	
        try {
			if (player.equals(userName) || 
					(!isHost && !player.equals(hostToConnect)) ||
					CertificateManager.isAuthenticated(WaitingActivity.this, cert))
				authButton.setVisibility(View.INVISIBLE);
		} 
        catch (Exception e) {
		}
        
        playerListView.addView(childView);
		
		return true;
	}
	
	private boolean removePlayer(String player){
		if (connectedPlayers.size() == 0 || !connectedPlayers.contains(player))
			return false;
		
		playerListView.removeView(playerListView.findViewWithTag(player));
		
		connectedPlayers.remove(player);
		return true;
	}
	
	private void addReadyPlayer(String player){
		readyPlayers.add(player);
		((TextView)((LinearLayout)playerListView.findViewWithTag(player)).findViewById(R.id.waiting_choice_title)).setTextColor(Color.BLUE);
		
		if (isHost){
	 		for (String playerTmp: connectedPlayers)
				if (!player.equals(userName))
					MessageSender.sendMessage(MessageCreator.playerReady(playerTmp, readyPlayers.toArray(new String[readyPlayers.size()])));
		}
		
		if (readyPlayers.size() == 4){
			launchGame();
		}
	}
	
	private void removeReadyPlayers(){
		for (String player: readyPlayers){
			((TextView)((LinearLayout)playerListView.findViewWithTag(player)).findViewById(R.id.waiting_choice_title)).setTextColor(Color.BLACK);
		}
		readyPlayers.clear();
		
		readyToggle.setEnabled(false);
		readyToggle.setClickable(false);
	}
	
	private Button getAuthButtonForPlayer(String player){
		if (connectedPlayers.size() == 0 || !connectedPlayers.contains(player))
			return null;
		
		return (Button)playerListView.findViewWithTag(player).findViewById(R.id.waiting_choice_auth_button);
	}
	
	public void onToggleClicked(View view) {
	    boolean on = readyToggle.isChecked();
	    if (on) {
	     	if (isHost){
	     		addReadyPlayer(userName);
	    		readyToggle.setClickable(false);
	    	}
	     	else{
	     		MessageSender.sendMessage(MessageCreator.playerReady(hostToConnect, new String[0]));
	     	}
	    }
	}

	public void launchGame(){
		Intent gameIntent = new Intent(getApplicationContext(), GameActivity.class);
		
		gameIntent.setAction("android.intent.action.MAIN");
		gameIntent.putExtra(StartActivity.INTENT_MODE_HOST, isHost);
		gameIntent.putExtra(StartActivity.INTENT_PLAYERS, connectedPlayers.toArray(new String[connectedPlayers.size()]));
		
		startActivity(gameIntent);
	}
	
	@Override
	public void handleMessage(final CompoundMessage m) {
		WaitingActivity.this.runOnUiThread(new Runnable() {
		    @Override
		    public void run() {
				//Toast.makeText(getBaseContext(), m.message.toString(), Toast.LENGTH_SHORT).show();
				//Log.d(this.getClass().getSimpleName(),"DBG:Received message:\n"+m.message.toString());
				
				if (isHost){
					if (m.message.getMessageType().equals(Message.TYPE_JOIN_REQUEST)){
						if (connectedPlayers.size() == 4)
							MessageSender.sendMessage(MessageCreator.joinRejected(m.username));
						else {
							addPlayer(m.username, m.cert, m.message.getSubNodeByName(Message.FIELD_IP).getText(), m.message.getSubNodeByName(Message.FIELD_PORT).getTextAsInt());
	
							for (String player: connectedPlayers)
								if (!player.equals(userName))
									MessageSender.sendMessage(MessageCreator.joinAccepted(player, connectedPlayers.toArray(new String[connectedPlayers.size()])));
							
							if (connectedPlayers.size() == 4){
								readyToggle.setEnabled(true);
								readyToggle.setClickable(true);
							}
						}
					}
					else if (m.message.getMessageType().equals(Message.TYPE_LEAVE_REQUEST)){
						if (removePlayer(m.username)){
							try {
								CertificateManager.removeSessionCertificate(m.username);
							} 
							catch (Exception e) {
							}
							
							for (String player: connectedPlayers)
								if (!player.equals(userName))
									MessageSender.sendMessage(MessageCreator.leaveAccepted(player, m.username));
							
							removeReadyPlayers();
						}
					}
					else if (m.message.getMessageType().equals(Message.TYPE_AUTH)){
						showAuthDialog(getAuthButtonForPlayer(m.username), m.cert);
					}
					else if (m.message.getMessageType().equals(Message.TYPE_READY)){
						if (!readyPlayers.contains(m.username)){
							addReadyPlayer(m.username);
							for (String player: connectedPlayers)
								if (!player.equals(userName))
									MessageSender.sendMessage(MessageCreator.playerReady(player, readyPlayers.toArray(new String[readyPlayers.size()])));
						}
					}
				}
				else{
					if (m.message.getMessageType().equals(Message.TYPE_JOIN_ACCEPTED)){
						for (XMLNode node: m.message.getSubNodesByName(Message.FIELD_PLAYER))
							addPlayer(node.getText(), m.cert, null, null);
						if (connectedPlayers.size() == 4){
							readyToggle.setEnabled(true);
							readyToggle.setClickable(true);
						}
					}
					else if (m.message.getMessageType().equals(Message.TYPE_JOIN_REJECTED)){
						//TODO: display error message
						onBackPressed();
					}
					else if (m.message.getMessageType().equals(Message.TYPE_LEAVE_ACCEPTED)){
						removePlayer(m.message.getSubNodeByName(Message.FIELD_PLAYER).getText());
						if (m.message.getSubNodeByName(Message.FIELD_PLAYER).getText().equals(hostToConnect)){
							Toast.makeText(getApplicationContext(), R.string.waiting_toast_server_closing, Toast.LENGTH_SHORT).show();
							onBackPressed();
						}
						removeReadyPlayers();
					}
					else if (m.message.getMessageType().equals(Message.TYPE_AUTH)){
						showAuthDialog(getAuthButtonForPlayer(m.username), m.cert);
					}
					else if (m.message.getMessageType().equals(Message.TYPE_READY)){
						readyPlayers.clear();
						for (XMLNode node: m.message.getSubNodesByName(Message.FIELD_PLAYER))
							addReadyPlayer(node.getText());
					}
				}
		    }
		});
	}

	@Override
	public void wifiEnabled() {
	}

	@Override
	public void wifiDisabled() {
		Toast.makeText(getApplicationContext(), R.string.waiting_toast_wifi_failed, Toast.LENGTH_SHORT).show();
		returnToPreviousActivity();
	}

	@Override
	public void actionFailed(final Exception e) {
		WaitingActivity.this.runOnUiThread(new Runnable() {
		    @Override
		    public void run() {
		    	synchronized (infoLock) {
			    	if (info == null){
			    		Log.e(this.getClass().getSimpleName(),"ServiceRegistrationFailed");
			    		if (NetSettings.debug)
			    			Log.e(this.getClass().getSimpleName(),Log.getStackTraceString(e));
			    	}
			    	else{
			    		Log.e(this.getClass().getSimpleName(),"ServiceUnregistrationFailed");
			    		if (NetSettings.debug)
			    			Log.e(this.getClass().getSimpleName(),Log.getStackTraceString(e));
			    	}
		    	}
		    	returnToPreviousActivity();
		    }
		});
		
	}

	@Override
	public void actionSuccess(final ServiceInfo serviceInfo) {
		WaitingActivity.this.runOnUiThread(new Runnable() {
		    @Override
		    public void run() {
	    		findViewById(R.id.waiting_progressbar).setVisibility(View.GONE);
		    	synchronized (infoLock) {
			    	if (info == null){
			    		((TextView)findViewById(R.id.waiting_text_fixed_choice)).setText(R.string.waiting_fixed_service_enabled);
			    		if (isHost)
			    			addPlayer(userName, null, null, null);
			    		info = serviceInfo;
			    	}
			    	else{
			    		info = null;
			    		returnToPreviousActivity();
			    	}
				}
		    }
		});
	}

}
