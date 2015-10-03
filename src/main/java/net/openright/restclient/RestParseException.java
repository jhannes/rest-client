package net.openright.restclient;

public class RestParseException extends RestException {

	public RestParseException(String endpointName, RuntimeException e, String url) {
		super(endpointName, e, url);
	}

}
