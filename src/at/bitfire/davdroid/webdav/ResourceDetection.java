package at.bitfire.davdroid.webdav;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpException;
import org.apache.http.client.ClientProtocolException;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.Record;
import org.xbill.DNS.SRVRecord;
import org.xbill.DNS.TextParseException;
import org.xbill.DNS.Type;

import android.content.Context;
import android.util.Log;
import at.bitfire.davdroid.R;
import at.bitfire.davdroid.syncadapter.ServerInfo;
import at.bitfire.davdroid.webdav.HttpPropfind.Mode;

public class ResourceDetection {
	private final static String TAG = "davdroid.ResourceDetection";
	
	Context context;	// required for exception i18n
	
	
	public ResourceDetection(Context context) {
		this.context = context;
	}
	

	public void detectCollections(ServerInfo serverInfo) throws URISyntaxException, IOException, HttpException {
		//WebDavResource base = new WebDavResource(new URI(baseURL), true);
		
		/*** CalDAV ***/
		WebDavResource baseCalDAV = discoverService(serverInfo, "caldav");
		
		// detect capabilities
		baseCalDAV.options();
		boolean hasCalDAV = baseCalDAV.supportsDAV("calendar-access");
		if (hasCalDAV) {
			checkDavMethods(baseCalDAV);

			baseCalDAV.propfind(Mode.HOME_SETS);
			String pathCalendars = baseCalDAV.getCalendarHomeSet();
			
			if (pathCalendars != null) {
				Log.i(TAG, "Found calendar home set: " + pathCalendars);
				serverInfo.setCalDAV(true);
				
				// find calendars
				WebDavResource homeSetCalendars = new WebDavResource(baseCalDAV, pathCalendars, true);
				homeSetCalendars.propfind(Mode.MEMBERS_COLLECTIONS);
				
				List<ServerInfo.ResourceInfo> calendars = new LinkedList<ServerInfo.ResourceInfo>();
				if (homeSetCalendars.getMembers() != null)
					for (WebDavResource resource : homeSetCalendars.getMembers())
						if (resource.isCalendar()) {
							Log.i(TAG, "Found calendar: " + resource.getLocation().getRawPath());
							if (resource.getSupportedComponents() != null) {
								// CALDAV:supported-calendar-component-set available
								boolean supportsEvents = false;
								for (String supportedComponent : resource.getSupportedComponents())
									if (supportedComponent.equalsIgnoreCase("VEVENT"))
										supportsEvents = true;
								if (!supportsEvents)	// ignore collections without VEVENT support
									continue;
							}
							ServerInfo.ResourceInfo info = new ServerInfo.ResourceInfo(
								ServerInfo.ResourceInfo.Type.CALENDAR,
								resource.getLocation().toString(),
								resource.getDisplayName(),
								resource.getDescription(), resource.getColor()
							);
							info.setTimezone(resource.getTimezone());
							calendars.add(info);
						}
				
				serverInfo.setCalendars(calendars);
			} else
				throw new DavIncapableException(context.getString(R.string.error_home_set_calendars));
		}
		
		
		/*** CardDAV ***/
		WebDavResource baseCardDAV = discoverService(serverInfo, "carddav");
		
		// detect capabilities
		baseCardDAV.options();
		boolean hasCardDAV = baseCardDAV.supportsDAV("addressbook");
		if (hasCardDAV) {
			checkDavMethods(baseCardDAV);
			
			// find home sets
			baseCardDAV.propfind(Mode.HOME_SETS);
			String pathAddressBooks = baseCardDAV.getAddressbookHomeSet();
			
			if (pathAddressBooks != null) {
				Log.i(TAG, "Found address-book home set: " + pathAddressBooks);
				serverInfo.setCardDAV(true);
				
				// find address books
				WebDavResource homeSetAddressBooks = new WebDavResource(baseCardDAV, pathAddressBooks, true);
				homeSetAddressBooks.propfind(Mode.MEMBERS_COLLECTIONS);
				
				List<ServerInfo.ResourceInfo> addressBooks = new LinkedList<ServerInfo.ResourceInfo>();
				if (homeSetAddressBooks.getMembers() != null)
					for (WebDavResource resource : homeSetAddressBooks.getMembers())
						if (resource.isAddressBook()) {
							Log.i(TAG, "Found address book: " + resource.getLocation().getRawPath());
							ServerInfo.ResourceInfo info = new ServerInfo.ResourceInfo(
								ServerInfo.ResourceInfo.Type.ADDRESS_BOOK,
								resource.getLocation().toString(),
								resource.getDisplayName(),
								resource.getDescription(), resource.getColor()
							);
							addressBooks.add(info);
						}
				
				serverInfo.setAddressBooks(addressBooks);
			} else
				throw new DavIncapableException(context.getString(R.string.error_home_set_address_books));
		}
		
		if (!hasCalDAV && !hasCardDAV)
			throw new DavIncapableException(context.getString(R.string.neither_caldav_nor_carddav));
		
		// TODO support different base paths for CalDAV and CardDAV
		serverInfo.setProvidedURL(baseCalDAV.getLocation().toASCIIString());
	}

	// service discovery by RFC 6764
	WebDavResource discoverService(ServerInfo serverInfo, String service) throws DavIncapableException {
		String providedURL = serverInfo.getProvidedURL();
		
		// 1. processing user input
		URL url;
		try {
			url = new URL(providedURL);
		} catch (MalformedURLException e) {
			return null;
		}
		
		// 2. determination of service FQDN and port number
		String protocol = url.getProtocol();
		String host = url.getHost();
		int port = url.getPort(); if (port == -1) port = url.getDefaultPort();
		String contextPath = "/.well-known/" + service;
		
		try {
			String domain = host;
			
			String query = "_" + service + "s._tcp." + domain;
			Log.i(TAG, "Trying to discover service " + query);
			Record[] records = new Lookup(query, Type.SRV).run();
			if (records != null) {
				Log.d(TAG, "Received " + records.length + " SRV records");
				for (Record record : records) {
					SRVRecord srv = (SRVRecord)record;
					protocol = "https";
					host = srv.getTarget().toString(true);
					port = srv.getPort();
					Log.i(TAG, "Found service " + service + " on https://" + host + ":" + port);
					break;
				}
			}
		} catch (TextParseException e) {
			Log.e(TAG, "Service discovery: couldn't parse DNS records");
		}

		// 5. connecting to the service
		WebDavResource principalResource = null;
		try {
			WebDavResource wellKnown = new WebDavResource(
					new URI(protocol, null, host, port, contextPath, null, null),
					serverInfo.getUserName(), serverInfo.getPassword(), serverInfo.isAuthPreemptive(), false);
			principalResource = wellKnown.getPrincipal();
		} catch (Exception e) {
		}

		if (principalResource == null) {
			try {
				String fallbackPath = url.getPath();
				if (StringUtils.isEmpty(fallbackPath))
					fallbackPath = "/";
				
				WebDavResource base = new WebDavResource(
						new URI(protocol, null, host, port, fallbackPath, url.getQuery(), null),
						serverInfo.getUserName(), serverInfo.getPassword(), serverInfo.isAuthPreemptive(), false);
				principalResource = base.getPrincipal();
			} catch (Exception e) {
				throw new DavIncapableException("Couldn't determine context path");
			}
		}
		
		return principalResource;
	}
	
	
	private void checkDavMethods(WebDavResource resource) throws DavIncapableException {
		if (!resource.supportsMethod("GET") ||
			!resource.supportsMethod("PUT") ||
			!resource.supportsMethod("PROPFIND") ||
			!resource.supportsMethod("REPORT"))
			throw new DavIncapableException(context.getString(R.string.exception_incapable_resource));
	}

}
