package net.openright.restclient;

import java.io.IOException;

public class RestIOException extends RestException {

    public RestIOException(String endpointName, IOException e, String url) {
        super(endpointName, e, url);
    }

}
