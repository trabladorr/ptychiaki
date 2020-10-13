package com.trurdilin.tichu.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.trurdilin.tichu.R;
import com.trurdilin.tichu.net.AutoNetworkingGoogleNSD;
import com.trurdilin.tichu.ui.ConfigActivity.AlphaDigitFilter;
import com.trurdilin.tichu.ui.WifiStateReceiver.WifiStateHandler;

public class StartActivity extends Activity implements WifiStateHandler{

	public static final String INTENT_MODE_HOST = "mode_host";
	public static final String INTENT_PLAYERS = "players";
	public static final String INTENT_HOST_NAME = "host_name";
	public static final String INTENT_HOST_ADDRESS = "host_address";
	public static final String INTENT_HOST_PORT = "host_port";
	
	private boolean wifiState = true;
	private String userName = "";
	private Button hostButton;
	private Button joinButton;
	
	private OnClickListener hostButtonListener = new OnClickListener() {
		@Override
		public void onClick(View v) {
			Intent waitIntent = new Intent(getApplicationContext(), WaitingActivity.class);
			waitIntent.setAction("android.intent.action.MAIN");
			waitIntent.putExtra(INTENT_MODE_HOST, true);
			startActivity(waitIntent);
		}
	};
	
	private OnClickListener joinButtonListener = new OnClickListener() {
		@Override
		public void onClick(View v) {
			Intent waitIntent = new Intent(getApplicationContext(), FindServerActivity.class);
			waitIntent.setAction("android.intent.action.MAIN");
			startActivity(waitIntent);
		}
	};
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.start_activity);
		
		hostButton = (Button)findViewById(R.id.start_button_host);
		hostButton.setOnClickListener(hostButtonListener);
		
		joinButton = (Button)findViewById(R.id.start_button_join);
		joinButton.setOnClickListener(joinButtonListener);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		int id = item.getItemId();
		if (id == R.id.menu_find_action_settings) {
			Intent configIntent = new Intent(getApplicationContext(), ConfigActivity.class);
			configIntent.setAction("android.intent.action.MAIN");
			startActivity(configIntent);
			return true;
		}
		return super.onOptionsItemSelected(item);
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		SharedPreferences pref = getSharedPreferences(ConfigActivity.PREF_NAME, 0);
		userName = pref.getString(ConfigActivity.PREF_USER_NAME, "");
		
		if (userName.isEmpty())
			showNameDialog();
		
		modifyForWifiState(AutoNetworkingGoogleNSD.wifiConnected(getApplicationContext()));
		WifiStateReceiver.register(this, this);
	}
	
	@Override
	protected void onPause() {
		super.onPause();
		WifiStateReceiver.unregister();
	}
	
	@Override
	public void onBackPressed() {
		finish();
	}
	
	private void showNameDialog(){
		AlertDialog.Builder alert = new AlertDialog.Builder(this);

		alert.setTitle(getString(R.string.alert_username_name));
		alert.setMessage(getString(R.string.alert_username_message));

		// Set an EditText view to get user input 
		final EditText input = new EditText(this);

		input.setFilters(new InputFilter[]{new AlphaDigitFilter()});
		input.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
		alert.setView(input);

		alert.setPositiveButton(getString(R.string.alert_username_button_ok), new DialogInterface.OnClickListener() {
		public void onClick(DialogInterface dialog, int whichButton) {
				userName = input.getText().toString();
				SharedPreferences.Editor prefEd = getSharedPreferences(ConfigActivity.PREF_NAME, 0).edit();
				prefEd.putString(ConfigActivity.PREF_USER_NAME, userName);
				prefEd.commit();
			}
		});

		alert.setNegativeButton(getString(R.string.alert_username_button_exit), new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
				finish();
			}
		});

		alert.show();
	}

	@Override
	public void wifiEnabled() {
		modifyForWifiState(true);
	}

	@Override
	public void wifiDisabled() {
		modifyForWifiState(false);
	}
	
	private void modifyForWifiState(boolean state){
		if (state == wifiState)
			return;
		wifiState = state;
		hostButton.setEnabled(state);
		joinButton.setEnabled(state);
		if (state)
			((TextView)findViewById(R.id.start_text_fixed_choice)).setText(R.string.start_fixed_choice);
		else
			((TextView)findViewById(R.id.start_text_fixed_choice)).setText(R.string.start_fixed_choice_disabled);
	}
}


