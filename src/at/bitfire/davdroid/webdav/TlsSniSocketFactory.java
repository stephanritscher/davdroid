/*******************************************************************************
 * Copyright (c) 2014 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 ******************************************************************************/
package at.bitfire.davdroid.webdav;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.Collections;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.X509KeyManager;

import android.annotation.TargetApi;
import android.content.Context;
import android.net.SSLCertificateSocketFactory;
import android.os.Build;
import android.util.Log;
import ch.boye.httpclientandroidlib.HttpHost;
import ch.boye.httpclientandroidlib.conn.socket.LayeredConnectionSocketFactory;
import ch.boye.httpclientandroidlib.conn.ssl.BrowserCompatHostnameVerifier;
import ch.boye.httpclientandroidlib.protocol.HttpContext;

public class TlsSniSocketFactory implements LayeredConnectionSocketFactory {
	private static final String TAG = "davdroid.SNISocketFactory";
	
	public final static TlsSniSocketFactory INSTANCE = new TlsSniSocketFactory();
	
	private final static SSLCertificateSocketFactory sslSocketFactory =
			(SSLCertificateSocketFactory)SSLCertificateSocketFactory.getDefault(0);
	private final static HostnameVerifier hostnameVerifier = new BrowserCompatHostnameVerifier();

	private X509KeyManager keyManager = null;
	
	/*
	For SSL connections without HTTP(S) proxy:
	   1) createSocket() is called
	   2) connectSocket() is called which creates a new SSL connection
	   2a) SNI is set up, and then
	   2b) the connection is established, hands are shaken and certificate/host name are verified    	 
	
	Layered sockets are used with HTTP(S) proxies:
	   1) a new plain socket is created by the HTTP library
	   2) the plain socket is connected to http://proxy:8080
	   3) a CONNECT request is sent to the proxy and the response is parsed
	   4) now, createLayeredSocket() is called which wraps an SSL socket around the proxy connection,
	      doing all the set-up and verfication
	   4a) Because SSLSocket.createSocket(socket, ...) always does a handshake without allowing
	       to set up SNI before, *** SNI is not available for layered connections *** (unless
	       active by Android's defaults, which it isn't at the moment).
	*/


	@Override
	public Socket createSocket(HttpContext context) throws IOException {
		return sslSocketFactory.createSocket();
	}

	@Override
	public Socket connectSocket(int timeout, Socket plain, HttpHost host, InetSocketAddress remoteAddr, InetSocketAddress localAddr, HttpContext context) throws IOException {
		Log.d(TAG, "Preparing direct SSL connection (without proxy) to " + host);
		
		// we'll rather use an SSLSocket directly
		plain.close();

		// create a plain SSL socket, but don't do hostname/certificate verification yet
		SSLSocket ssl = (SSLSocket)sslSocketFactory.createSocket(remoteAddr.getAddress(), host.getPort());
		
		// For some reason the KeyManager of the sslParameters might be the default KeyManager instead of the one we specified.
		// Observed on a Galaxy Note 3 with Android 4.4
		if (keyManager != null) {
			try {
				Object sp = null;
				for (Field f : ssl.getClass().getDeclaredFields()) {
					if (f.getName().equals("sslParameters")) {
						f.setAccessible(true);
						Log.d(TAG, ssl + "." + f.getName() + " = " + f.get(ssl));
						sp = f.get(ssl);
					}
				}
				if (sp != null) {
					for (Field f : sp.getClass().getDeclaredFields()) {
						if (f.getName().equals("keyManager")) {
							f.setAccessible(true);
							Log.d(TAG, sp + "." + f.getName() + " = " + f.get(sp) + "  ---  should be " + keyManager);
							f.set(sp, keyManager);
						}
					}
				}
			} catch (Exception e) {
				Log.e(TAG, "Could not apply workaround for setting keymanager", e);
			}
		}
		
		// connect, set SNI, shake hands, verify, print connection info
		connectWithSNI(ssl, host.getHostName());

		return ssl;
	}

	@Override
	public Socket createLayeredSocket(Socket plain, String host, int port, HttpContext context) throws IOException, UnknownHostException {
		Log.d(TAG, "Preparing layered SSL connection (over proxy) to " + host);
		
		// create a layered SSL socket, but don't do hostname/certificate verification yet
		SSLSocket ssl = (SSLSocket)sslSocketFactory.createSocket(plain, host, port, true);

		// already connected, but verify host name again and print some connection info
		Log.w(TAG, "Setting SNI/TLSv1.2 will silently fail because the handshake is already done");
		connectWithSNI(ssl, host);

		return ssl;
	}
	
	
	@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
	private void connectWithSNI(SSLSocket ssl, String host) throws SSLPeerUnverifiedException {
		// set reasonable SSL/TLS settings before the handshake:
		// - enable all supported protocols (enables TLSv1.1 and TLSv1.2 on Android <4.4.3, if available)
		ssl.setEnabledProtocols(ssl.getSupportedProtocols());
		
		// - set SNI host name
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
			Log.d(TAG, "Using documented SNI with host name " + host);
			sslSocketFactory.setHostname(ssl, host);
		} else {
			Log.d(TAG, "No documented SNI support on Android <4.2, trying with reflection");
			try {
				java.lang.reflect.Method setHostnameMethod = ssl.getClass().getMethod("setHostname", String.class);
				setHostnameMethod.invoke(ssl, host);
			} catch (Exception e) {
				Log.w(TAG, "SNI not useable", e);
			}
		}
		
		// verify hostname and certificate
		SSLSession session = ssl.getSession();
		if (!hostnameVerifier.verify(host, session))
			throw new SSLPeerUnverifiedException("Cannot verify hostname: " + host);

		Log.i(TAG, "Established " + session.getProtocol() + " connection with " + session.getPeerHost() +
				" using " + session.getCipherSuite());
	}
	
	public void setKeyManager(byte[] keystore, String password) {
		try {
			KeyStore ks = KeyStore.getInstance("BKS");
			ks.load(new ByteArrayInputStream(keystore), password.toCharArray());
			KeyManagerFactory factory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
			factory.init(ks, password.toCharArray());
			boolean found = false;
			for (KeyManager km : factory.getKeyManagers()) {
				if (km instanceof X509KeyManager) {
					setKeyManager((X509KeyManager) km);
					found = true;
					Log.d(TAG, "Using user-specified keys with aliases " + Arrays.toString(Collections.list(ks.aliases()).toArray()) + ".");
				}
				break;
			}
			if (!found) {
				Log.e(TAG, "No X509 compatible key manager was found.");
			}
		} catch (Exception e) {
			Log.e(TAG, "Could not set key managers", e);
		}
	}

	public void setKeyManager(String alias, Context context) {
		setKeyManager(new KeyChainKeyManager(context, alias));
		Log.d(TAG, "Using key from keychain with alias " + alias + ".");
	}
	
	public void setKeyManager(X509KeyManager keyManager) {
		this.keyManager = keyManager;
		sslSocketFactory.setKeyManagers(new KeyManager[]{keyManager});
	}
}
