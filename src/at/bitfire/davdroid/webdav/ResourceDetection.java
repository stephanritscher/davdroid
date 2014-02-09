package at.bitfire.davdroid.webdav;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedList;
import java.util.List;

import org.apache.http.HttpException;

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
		String baseURL = serverInfo.getBaseURL();
		
		WebDavResource base = new WebDavResource(new URI(baseURL), true);

		
		/*** CalDAV ***/
		
		WebDavResource wellKnown = new WebDavResource(base, "/.well-known/caldav");
		String baseCalDavURL = wellKnown.checkRedirection();
		if (baseCalDavURL == null)		// no well-known CalDAV URL
			baseCalDavURL = baseURL;
		
		Log.i(TAG, "CalDAV base URL: " + baseCalDavURL);
		WebDavResource baseCalDAV = new WebDavResource(new URI(baseCalDavURL), serverInfo.getUserName(),
				serverInfo.getPassword(), serverInfo.isAuthPreemptive(), true);
		
		// detect capabilities
		baseCalDAV.options();
		boolean hasCalDAV = baseCalDAV.supportsDAV("calendar-access");
		if (hasCalDAV) {
			checkDavMethods(baseCalDAV);

			// find principal path
			String principalPath = getPrincipalPath(baseCalDAV);
			
			// find home sets
			WebDavResource principal = new WebDavResource(baseCalDAV, principalPath);
			principal.propfind(Mode.HOME_SETS);
			String pathCalendars = principal.getCalendarHomeSet();
			
			if (pathCalendars != null) {
				Log.i(TAG, "Found calendar home set: " + pathCalendars);
				serverInfo.setCalDAV(true);
				
				// find calendars
				WebDavResource homeSetCalendars = new WebDavResource(principal, pathCalendars, true);
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
		
		wellKnown = new WebDavResource(base, "/.well-known/carddav");
		String baseCardDavURL = wellKnown.checkRedirection();
		if (baseCardDavURL == null)		// no well-known CardDAV URL
			baseCardDavURL = baseURL;

		Log.i(TAG, "CardDAV base URL: " + baseCardDavURL);
		WebDavResource baseCardDAV = new WebDavResource(new URI(baseCardDavURL), serverInfo.getUserName(),
				serverInfo.getPassword(), serverInfo.isAuthPreemptive(), true);
		
		// detect capabilities
		baseCardDAV.options();
		boolean hasCardDAV = baseCardDAV.supportsDAV("addressbook");
		if (hasCardDAV) {
			checkDavMethods(baseCardDAV);

			// find principal path
			String principalPath = getPrincipalPath(baseCardDAV);
			
			// find home sets
			WebDavResource principal = new WebDavResource(baseCardDAV, principalPath);
			principal.propfind(Mode.HOME_SETS);
			String pathAddressBooks = principal.getAddressbookHomeSet();
			
			if (pathAddressBooks != null) {
				Log.i(TAG, "Found address-book home set: " + pathAddressBooks);
				serverInfo.setCardDAV(true);
				
				// find address books
				WebDavResource homeSetAddressBooks = new WebDavResource(principal, pathAddressBooks, true);
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
		serverInfo.setBaseURL(baseCalDavURL);
	}
	
	
	private void checkDavMethods(WebDavResource resource) throws DavIncapableException {
		if (!resource.supportsMethod("GET") ||
			!resource.supportsMethod("PUT") ||
			!resource.supportsMethod("PROPFIND") ||
			!resource.supportsMethod("REPORT"))
			throw new DavIncapableException(context.getString(R.string.exception_incapable_resource));
	}
	
	private String getPrincipalPath(WebDavResource base) throws DavException {
		String principalPath = null;
		try {
			base.propfind(Mode.CURRENT_USER_PRINCIPAL);
			principalPath = base.getCurrentUserPrincipal();
		} catch (Exception e) {
		}
		
		if (principalPath != null) {
			Log.i(TAG, "Found principal path: " + principalPath);
			return principalPath;
		} else
			throw new DavIncapableException(context.getString(R.string.error_principal_path));
	}
	
}
