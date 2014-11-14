package at.bitfire.davdroid.mirakel.resource;

import java.net.MalformedURLException;

import at.bitfire.davdroid.mirakel.webdav.DavMultiget;
import ch.boye.httpclientandroidlib.impl.client.CloseableHttpClient;

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


    public CalDavList(CloseableHttpClient httpClient, String baseURL, String user, String password, boolean preemptiveAuth) throws  MalformedURLException {
        super(httpClient, baseURL, user, password, preemptiveAuth);
    }
}
