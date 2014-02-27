package at.bitfire.davdroid.webdav;

import lombok.Getter;

import org.apache.http.HttpException;

public class RedirectionException extends HttpException {
	private static final long serialVersionUID = 8151908878098215371L;
	
	@Getter final int code;
	@Getter final String location;

	public RedirectionException(int code, String location) {
		super("Redirection" + code);
		this.code = code;
		this.location = location;
	}
}
