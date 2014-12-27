package at.bitfire.davdroid.resource;

import java.net.URISyntaxException;

import org.apache.http.impl.client.CloseableHttpClient;

import at.bitfire.davdroid.webdav.DavMultiget;

public class CalDavList extends RemoteCollection<ToDo>{
	@Override
	protected String memberContentType() {
		return "text/calendar";
	}

	@Override
	protected DavMultiget.Type multiGetType() {
		return DavMultiget.Type.CALENDAR;
	}

	@Override
	protected ToDo newResourceSkeleton(String name, String ETag) {
		return new ToDo(name, ETag);
	}


	public CalDavList(CloseableHttpClient httpClient, String baseURL, String user, String password, boolean preemptiveAuth) throws URISyntaxException {
		super(httpClient, baseURL, user, password, preemptiveAuth);
	}
}
