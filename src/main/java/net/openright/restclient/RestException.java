package net.openright.restclient;

public abstract class RestException extends RuntimeException {

    private final String url;
    private final String endpointName;

    protected RestException(String endpointName, String message, String url) {
        super(message);
        this.endpointName = endpointName;
        this.url = url;
    }

    protected RestException(String endpointName, Exception e, String url) {
        super(e);
        this.endpointName = endpointName;
        this.url = url;
    }

    public String getUrl() {
        return url;
    }

    public String getEndpointName() {
        return endpointName;
    }
}
