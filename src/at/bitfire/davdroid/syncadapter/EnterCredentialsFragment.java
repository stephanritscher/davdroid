/*******************************************************************************
 * Copyright (c) 2014 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 ******************************************************************************/
package at.bitfire.davdroid.syncadapter;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;

import org.apache.commons.lang.StringUtils;

import android.app.Activity;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.os.Bundle;
import android.security.KeyChain;
import android.security.KeyChainAliasCallback;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;
import at.bitfire.davdroid.R;
import at.bitfire.davdroid.URIUtils;

public class EnterCredentialsFragment extends Fragment implements TextWatcher, OnClickListener, KeyChainAliasCallback {
	private final static String TAG = "davdroid.EnterCredentialsFragment";
	private final static int INTENT_KEYFILE = 1;
	String protocol;
	
	TextView textHttpWarning, textKey;
	EditText editBaseURL, editUserName, editPassword;
	CheckBox checkboxPreemptive;
	Button btnNext;
	ToggleButton btnKeyFile, btnKeyChain;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View v = inflater.inflate(R.layout.enter_credentials, container, false);
		
		// protocol selection spinner
		textHttpWarning = (TextView) v.findViewById(R.id.http_warning);
		
		Spinner spnrProtocol = (Spinner) v.findViewById(R.id.select_protocol);
		spnrProtocol.setOnItemSelectedListener(new OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
				protocol = parent.getAdapter().getItem(position).toString();
				textHttpWarning.setVisibility(protocol.equals("https://") ? View.GONE : View.VISIBLE);
			}

			@Override
			public void onNothingSelected(AdapterView<?> parent) {
				protocol = null;
			}
		});
		spnrProtocol.setSelection(1);	// HTTPS

		// other input fields
		editBaseURL = (EditText) v.findViewById(R.id.baseURL);
		editBaseURL.addTextChangedListener(this);
		
		editUserName = (EditText) v.findViewById(R.id.userName);
		editUserName.addTextChangedListener(this);
		
		editPassword = (EditText) v.findViewById(R.id.password);
		editPassword.addTextChangedListener(this);
		
		btnKeyFile = (ToggleButton) v.findViewById(R.id.keyfile);
		btnKeyFile.setOnClickListener(this);

		btnKeyChain = (ToggleButton) v.findViewById(R.id.keychain);
		btnKeyChain.setOnClickListener(this);
		
		textKey = (TextView) v.findViewById(R.id.key);
		
		checkboxPreemptive = (CheckBox) v.findViewById(R.id.auth_preemptive);
		
		// hook into action bar
		setHasOptionsMenu(true);

		return v;
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
	    inflater.inflate(R.menu.enter_credentials, menu);
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.next:
			queryServer();
			break;
		default:
			return false;
		}
		return true;
	}

	void queryServer() {
		FragmentTransaction ft = getFragmentManager().beginTransaction();
		
		Bundle args = new Bundle();
		
		String host_path = editBaseURL.getText().toString();
		args.putString(QueryServerDialogFragment.EXTRA_BASE_URL, URIUtils.sanitize(protocol + host_path));
		args.putString(QueryServerDialogFragment.EXTRA_USER_NAME, editUserName.getText().toString());
		args.putString(QueryServerDialogFragment.EXTRA_PASSWORD, editPassword.getText().toString());
		args.putBoolean(QueryServerDialogFragment.EXTRA_AUTH_PREEMPTIVE, checkboxPreemptive.isChecked());
		if (btnKeyFile.isChecked()) {
			File file = new File(textKey.getText().toString());
			if (!file.exists()) {
				Log.e(TAG, "Key file '" + file + "' does not exist.");
				Toast.makeText(getActivity(), getString(R.string.keyfile_notfound), Toast.LENGTH_SHORT).show();
				return;
			}
			byte[] keystore = new byte[(int) file.length()];
			InputStream in = null;
			try {
				in = new BufferedInputStream(new FileInputStream(file));
				in.read(keystore);
			} catch (IOException e) {
				Log.e(TAG, "Could not load key file '" + file + "'", e);
				Toast.makeText(getActivity(), getString(R.string.keyfile_loaderror), Toast.LENGTH_SHORT).show();
				return;
			} finally {
				if (in != null) {
					try {
						in.close();
					} catch (IOException e) {
						Log.e(TAG, "Could not close key file '" + file + "': ", e);
					}
				}
			}
			args.putByteArray(QueryServerDialogFragment.EXTRA_KEYSTORE, keystore);
			Log.d(TAG, "Loaded user key from file '" + file + "'.");
		} else if (btnKeyChain.isChecked()) {
			args.putString(QueryServerDialogFragment.EXTRA_KEYALIAS, textKey.getText().toString());
		}
		
		DialogFragment dialog = new QueryServerDialogFragment();
		dialog.setArguments(args);
	    dialog.show(ft, QueryServerDialogFragment.class.getName());
	}

	
	// input validation
	
	@Override
	public void onPrepareOptionsMenu(Menu menu) {
		boolean ok =
			editUserName.getText().length() > 0 &&
			editPassword.getText().length() > 0;

		// check host name
		try {
			URI uri = new URI(URIUtils.sanitize(protocol + editBaseURL.getText().toString()));
			if (StringUtils.isBlank(uri.getHost()))
				ok = false;
		} catch (URISyntaxException e) {
			ok = false;
		}
			
		MenuItem item = menu.findItem(R.id.next);
		item.setEnabled(ok);
	}

	@Override
	public void beforeTextChanged(CharSequence s, int start, int count, int after) {
	}

	@Override
	public void onTextChanged(CharSequence s, int start, int before, int count) {
		getActivity().invalidateOptionsMenu();
	}

	@Override
	public void afterTextChanged(Editable s) {
	}

	@Override
	public void onClick(View v) {
		if (v.getId() == R.id.keyfile) {
			/* button is toggled before this code */
			if (!btnKeyFile.isChecked()) {
				textKey.setText("");
				return;
			}
			/* Don't check if aborted */
			btnKeyFile.setChecked(false);
			/* Start file chooser */
			Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
		    intent.setType("file/*");
		    intent.addCategory(Intent.CATEGORY_OPENABLE);
			startActivityForResult(Intent.createChooser(intent, getString(R.string.select_keyfile)), INTENT_KEYFILE);
		} else if (v.getId() == R.id.keychain) {
			/* button is toggled before this code */
			if (!btnKeyChain.isChecked()) {
				textKey.setText("");
				return;
			}
			/* Don't check if aborted */
			btnKeyFile.setChecked(false);
			/* Start key chooser */
			KeyChain.choosePrivateKeyAlias(getActivity(), this, null, null, null, -1, null);
		}
	}
	
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == INTENT_KEYFILE && resultCode == Activity.RESULT_OK) {
			textKey.setText(data.getData().getPath());
			btnKeyFile.setChecked(true);
			btnKeyChain.setChecked(false);
		}
	}

	@Override
	public void alias(final String alias) {
		Log.d(TAG, "alias = " + alias);
		if (alias != null) {
			getActivity().runOnUiThread(new Runnable() {
				public void run() {
					textKey.setText(alias);
					btnKeyFile.setChecked(false);
				}
			});
		}
	}
}