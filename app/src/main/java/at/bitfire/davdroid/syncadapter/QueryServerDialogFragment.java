/*******************************************************************************
 * Copyright (c) 2014 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 ******************************************************************************/
package at.bitfire.davdroid.syncadapter;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedList;
import java.util.List;

import android.app.DialogFragment;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.AsyncTaskLoader;
import android.content.Context;
import android.content.Loader;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.Toast;

import org.apache.http.HttpException;

import at.bitfire.davdroid.R;
import at.bitfire.davdroid.resource.DavResourceFinder;
import at.bitfire.davdroid.resource.ServerInfo;
import at.bitfire.davdroid.webdav.DavException;
import at.bitfire.davdroid.webdav.DavHttpClient;
import at.bitfire.davdroid.webdav.DavIncapableException;
import at.bitfire.davdroid.webdav.HttpPropfind.Mode;
import at.bitfire.davdroid.webdav.TlsSniSocketFactory;
import at.bitfire.davdroid.webdav.WebDavResource;
import org.apache.http.impl.client.CloseableHttpClient;
import lombok.Cleanup;

public class QueryServerDialogFragment extends DialogFragment implements LoaderCallbacks<ServerInfo> {
	private static final String TAG = "davdroid.QueryServerDialogFragment";
	public static final String
		EXTRA_BASE_URI = "base_uri",
		EXTRA_USER_NAME = "user_name",
		EXTRA_PASSWORD = "password",
		EXTRA_AUTH_PREEMPTIVE = "auth_preemptive",
		EXTRA_KEYSTORE = "keystore",
		EXTRA_KEYALIAS = "alias";
	
	ProgressBar progressBar;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setStyle(DialogFragment.STYLE_NO_TITLE, android.R.style.Theme_Holo_Light_Dialog);
		setCancelable(false);

		Loader<ServerInfo> loader = getLoaderManager().initLoader(0, getArguments(), this);
		if (savedInstanceState == null)		// http://code.google.com/p/android/issues/detail?id=14944
			loader.forceLoad();
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		return inflater.inflate(R.layout.query_server, container, false);
	}

	@Override
	public Loader<ServerInfo> onCreateLoader(int id, Bundle args) {
		Log.i(TAG, "onCreateLoader");
		return new ServerInfoLoader(getActivity(), args);
	}

	@Override
	public void onLoadFinished(Loader<ServerInfo> loader, ServerInfo serverInfo) {
		if (serverInfo.getErrorMessage() != null)
			Toast.makeText(getActivity(), serverInfo.getErrorMessage(), Toast.LENGTH_LONG).show();
		else {
			SelectCollectionsFragment selectCollections = new SelectCollectionsFragment();
			Bundle arguments = new Bundle();
			arguments.putSerializable(SelectCollectionsFragment.KEY_SERVER_INFO, serverInfo);
			selectCollections.setArguments(arguments);
			
			getFragmentManager().beginTransaction()
				.replace(R.id.fragment_container, selectCollections)
				.addToBackStack(null)
				.commitAllowingStateLoss();
		}
		
		getDialog().dismiss();
	}

	@Override
	public void onLoaderReset(Loader<ServerInfo> arg0) {
	}
	
	
	static class ServerInfoLoader extends AsyncTaskLoader<ServerInfo> {
		private static final String TAG = "davdroid.ServerInfoLoader";
		final Bundle args;
		final Context context;
		
		public ServerInfoLoader(Context context, Bundle args) {
			super(context);
			this.context = context;
			this.args = args;
		}

		@Override
		public ServerInfo loadInBackground() {
			ServerInfo serverInfo = new ServerInfo(
				URI.create(args.getString(EXTRA_BASE_URI)),
				args.getString(EXTRA_USER_NAME),
				args.getString(EXTRA_PASSWORD),
				args.getBoolean(EXTRA_AUTH_PREEMPTIVE),
				args.getByteArray(EXTRA_KEYSTORE),
				args.getString(EXTRA_KEYALIAS)
			);
			
			// disable compression and enable network logging for debugging purposes 
			if (serverInfo.getKeyStore() != null) {
				TlsSniSocketFactory.INSTANCE.setKeyManager(serverInfo.getKeyStore(), serverInfo.getPassword());
			} else if (serverInfo.getKeyAlias() != null) {
				TlsSniSocketFactory.INSTANCE.setKeyManager(serverInfo.getKeyAlias(), context);
			}
			
			try {
				@Cleanup DavResourceFinder finder = new DavResourceFinder(context);
				finder.findResources(serverInfo);
			} catch (URISyntaxException e) {
				serverInfo.setErrorMessage(getContext().getString(R.string.exception_uri_syntax, e.getMessage()));
			}  catch (IOException e) {
				serverInfo.setErrorMessage(getContext().getString(R.string.exception_io, e.getLocalizedMessage()));
			} catch (HttpException e) {
				Log.e(TAG, "HTTP error while querying server info", e);
				serverInfo.setErrorMessage(getContext().getString(R.string.exception_http, e.getLocalizedMessage()));
			} catch (DavException e) {
				Log.e(TAG, "DAV error while querying server info", e);
				serverInfo.setErrorMessage(getContext().getString(R.string.exception_incapable_resource, e.getLocalizedMessage()));
			}
			
			return serverInfo;
		}
		
	}
}
