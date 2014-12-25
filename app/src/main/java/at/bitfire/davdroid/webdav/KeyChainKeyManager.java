package at.bitfire.davdroid.webdav;

import java.net.Socket;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Arrays;

import javax.net.ssl.X509KeyManager;

import android.content.Context;
import android.security.KeyChain;
import android.util.Log;


public class KeyChainKeyManager implements X509KeyManager {
	private final static String TAG = "davdroid.KeyChainKeyManager";
	private final String alias;
	private final Context context;
	
	public KeyChainKeyManager(Context context, String alias) {
		/* Access must have been granted earlier by invokation of KeyChain.choosePrivateKeyAlias */
		this.context = context;
		this.alias = alias;
	}
	
	@Override
	public String chooseClientAlias(String[] keyType, Principal[] issuers, Socket socket) {
		Log.d(TAG, "chooseClientAlias(" + Arrays.toString(keyType) + ", " + Arrays.toString(issuers) + ")");		
		return alias;
	}

	@Override
	public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) {
		Log.w(TAG, "Unexpected invokation of chooseServerAlias(" + keyType + ", " + Arrays.toString(issuers) + "), returning null");
		return null;
	}

	@Override
	public X509Certificate[] getCertificateChain(String alias) {
		if (alias.equals(this.alias)) {
			Log.d(TAG, "getCertificateChain(" + alias + ")");
			try {
				return KeyChain.getCertificateChain(context, alias);
			} catch (Exception e) {
				Log.e(TAG, "Could not get cetificate chain for alias " + alias, e);
			}
		} else {
			Log.w(TAG, "Unexpected invokation of getCertificateChain(" + alias + "), returning null");	
		}
		return null;
	}

	@Override
	public String[] getClientAliases(String keyType, Principal[] issuers) {
		Log.d(TAG, "getClientAliases(" + keyType + ", " + Arrays.toString(issuers) + ")");
		return new String[]{alias};
	}

	@Override
	public String[] getServerAliases(String keyType, Principal[] issuers) {
		Log.w(TAG, "Unexpected invokation of getServerAliases(" + keyType + ", " + Arrays.toString(issuers) + "), returning null");
		return null;
	}

	@Override
	public PrivateKey getPrivateKey(String alias) {
		if (alias.equals(this.alias)) {
			Log.d(TAG, "getPrivateKey(" + alias + ")");
			try {
				return KeyChain.getPrivateKey(context, alias);
			} catch (Exception e) {
				Log.e(TAG, "Could not get private key for alias " + alias, e);
			}
		} else {
			Log.w(TAG, "Unexpected invokation of getCertificateChain(" + alias + "), returning null");	
		}
		return null;
	}

}
