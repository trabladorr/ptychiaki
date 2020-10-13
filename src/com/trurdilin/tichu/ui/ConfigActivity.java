package com.trurdilin.tichu.ui;

import java.util.List;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.trurdilin.tichu.R;
import com.trurdilin.tichu.net.rsa.CertificateManager;

public class ConfigActivity extends Activity{

	public static final String PREF_NAME = "Tichu_Settings";
	public static final String PREF_USER_NAME = "User_Name";
	
	private Button changeUsernameButton;
	private EditText username;
	private LinearLayout authenticatedListView;
	
	private OnClickListener changeUsernameListener = new OnClickListener() {
		@Override
		public void onClick(View v) {
			String userName = username.getText().toString();
			SharedPreferences.Editor prefEd = getSharedPreferences(PREF_NAME, 0).edit();
			prefEd.putString(PREF_NAME, userName);
			prefEd.commit();
		}
	};
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.config_activity);
		
		changeUsernameButton = (Button)findViewById(R.id.config_button_set_username);
		changeUsernameButton.setOnClickListener(changeUsernameListener);
		
		username = (EditText)findViewById(R.id.config_edittext_username);
		username.setFilters(new InputFilter[]{new AlphaDigitFilter()});
		username.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
		
		authenticatedListView = (LinearLayout)findViewById(R.id.config_authenticated_certificates);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.empty , menu);
		return true;
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		
		username.setText(getSharedPreferences(PREF_NAME, 0).getString(PREF_USER_NAME, ""));
		
		List<String> authUsernames = null;
		try {
			authUsernames = CertificateManager.getAuthenticated(ConfigActivity.this);
		} catch (Exception e) {
		}
		
		if (authUsernames == null || authUsernames.isEmpty())
			findViewById(R.id.config_text_fixed_authenticated).setVisibility(View.GONE);
		else{
			authenticatedListView.removeAllViews();
			for (String player:authUsernames)
				addAuthenticated(player);
		}
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
	
	private void addAuthenticated(final String player){
		@SuppressLint("InflateParams")
		
		RelativeLayout childView = (RelativeLayout)LayoutInflater.from(ConfigActivity.this).inflate(R.layout.config_activity_auth_entry, null);
        childView.setTag(player);
        
        ((TextView)childView.findViewById(R.id.config_auth_entry_title)).setText(player);
        ((Button)childView.findViewById(R.id.config_auth_entry_button)).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				removeAuthenticated(player, true);
			}
		});
        
        authenticatedListView.addView(childView);
	}
	
	private void removeAuthenticated(String player, boolean deleteCertificate){
		try{
			if (deleteCertificate)
				CertificateManager.removeAuthenticatedCertificate(player, this);
			authenticatedListView.removeView(authenticatedListView.findViewWithTag(player));
			
			List<String> authUsernames = null;
			try {
				authUsernames = CertificateManager.getAuthenticated(ConfigActivity.this);
			} catch (Exception e) {
			}
			
			if (authUsernames == null || authUsernames.isEmpty())
				findViewById(R.id.config_text_fixed_authenticated).setVisibility(View.GONE);
		}
		catch(Exception e){
		}
	}
	
	public static class AlphaDigitFilter implements InputFilter {

	    @Override
	    public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {

	        if (source instanceof SpannableStringBuilder) {
	            SpannableStringBuilder sourceAsSpannableBuilder = (SpannableStringBuilder) source;
	            for (int i = end - 1; i >= start; i--) {
	                char currentChar = source.charAt(i);
	                if (!Character.isLetterOrDigit(currentChar)) {
	                    sourceAsSpannableBuilder.delete(i, i + 1);
	                }
	            }
	            return source.toString();
	        } else {
	            StringBuilder filteredStringBuilder = new StringBuilder();
	            for (int i = start; i < end; i++) {
	                char currentChar = source.charAt(i);
	                if (Character.isLetterOrDigit(currentChar)) {
	                    filteredStringBuilder.append(currentChar);
	                }
	            }
	            return filteredStringBuilder.toString();
	        }
	    }

	}
}


