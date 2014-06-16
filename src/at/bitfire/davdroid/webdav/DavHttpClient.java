/*******************************************************************************
 * Copyright (c) 2014 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 ******************************************************************************/
package at.bitfire.davdroid.webdav;

import java.io.ByteArrayInputStream;
import java.security.Key;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.Collections;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;

import android.util.Log;
import at.bitfire.davdroid.Constants;
import ch.boye.httpclientandroidlib.client.config.RequestConfig;
import ch.boye.httpclientandroidlib.config.Registry;
import ch.boye.httpclientandroidlib.config.RegistryBuilder;
import ch.boye.httpclientandroidlib.conn.socket.ConnectionSocketFactory;
import ch.boye.httpclientandroidlib.conn.socket.PlainConnectionSocketFactory;
import ch.boye.httpclientandroidlib.impl.client.CloseableHttpClient;
import ch.boye.httpclientandroidlib.impl.client.HttpClientBuilder;
import ch.boye.httpclientandroidlib.impl.client.HttpClients;
import ch.boye.httpclientandroidlib.impl.conn.ManagedHttpClientConnectionFactory;
import ch.boye.httpclientandroidlib.impl.conn.PoolingHttpClientConnectionManager;
import edu.emory.mathcs.backport.java.util.Arrays;

public class DavHttpClient {
	private final static String TAG = "davdroid.DavHttpClient";
	
	private final static RequestConfig defaultRqConfig;
	private final static Registry<ConnectionSocketFactory> socketFactoryRegistry;
		
	static {
		socketFactoryRegistry =	RegistryBuilder.<ConnectionSocketFactory> create()
				.register("http", PlainConnectionSocketFactory.getSocketFactory())
				.register("https", TlsSniSocketFactory.INSTANCE)
				.build();
		
		// use request defaults from AndroidHttpClient
		defaultRqConfig = RequestConfig.copy(RequestConfig.DEFAULT)
				.setConnectTimeout(20*1000)
				.setSocketTimeout(45*1000)
				.setStaleConnectionCheckEnabled(false)
				.build();
	}


	public static CloseableHttpClient create(boolean disableCompression, boolean logTraffic, byte[] keystore, String password) {
		try {
			KeyManager[] km = null;
			if (keystore != null) {
				KeyStore keyStore = KeyStore.getInstance("BKS");
				keyStore.load(new ByteArrayInputStream(keystore), password.toCharArray());
				Log.d(TAG, "Loaded keys with aliases " + Arrays.toString(Collections.list(keyStore.aliases()).toArray()) + " from keystore of type " + keyStore.getType());
				for (String alias : Collections.list(keyStore.aliases())) {
					X509Certificate c = (X509Certificate) keyStore.getCertificate(alias);
					Key k = keyStore.getKey(alias, password.toCharArray());
					Log.d(TAG, alias + ": dn = " + c.getSubjectDN() + ", issuer = " + c.getIssuerDN() + ", key = " + (k != null ? k.getAlgorithm() : "null"));
				}
				KeyManagerFactory factory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
				factory.init(keyStore, password.toCharArray());
				km = factory.getKeyManagers();
			}
			TlsSniSocketFactory.INSTANCE.setKeyManagers(km);
			Log.d(TAG, "Initialized ssl socket factory with " + (km == null ? 0 : km.length) + " keys.");
		} catch (Exception e) {
			Log.e(TAG, "Could not set keystore in ssl socket factory: " + e);
		}
		
		PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager(socketFactoryRegistry);
		// limits per DavHttpClient (= per DavSyncAdapter extends AbstractThreadedSyncAdapter)
		connectionManager.setMaxTotal(3);				// max.  3 connections in total
		connectionManager.setDefaultMaxPerRoute(2);		// max.  2 connections per host
		
		HttpClientBuilder builder = HttpClients.custom()
				.useSystemProperties()
				.setConnectionManager(connectionManager)
				.setDefaultRequestConfig(defaultRqConfig)
				.setRetryHandler(DavHttpRequestRetryHandler.INSTANCE)
				.setUserAgent("DAVdroid/" + Constants.APP_VERSION)
				.disableCookieManagement();
		
		if (disableCompression) {
			Log.d(TAG, "Disabling compression for debugging purposes");
			builder = builder.disableContentCompression();
		}

		if (logTraffic)
			Log.d(TAG, "Logging network traffic for debugging purposes");
		ManagedHttpClientConnectionFactory.INSTANCE.wirelog.enableDebug(logTraffic);
		ManagedHttpClientConnectionFactory.INSTANCE.log.enableDebug(logTraffic);

		return builder.build();
	}

}
