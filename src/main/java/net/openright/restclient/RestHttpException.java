package net.openright.restclient;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import net.openright.restclient.util.IOUtil;

public class RestHttpException extends RestException {

    private int responseCode;
    private String responseMessage;
    private String detailText;

    public RestHttpException(String endpointName, HttpURLConnection connection) throws IOException {
        this(endpointName, connection.getResponseCode(), connection.getResponseMessage(), connection.getURL().toString());
        this.detailText = IOUtil.toString(connection.getErrorStream(), StandardCharsets.UTF_8);
    }

    public RestHttpException(String endpointName, int responseCode, String responseMessage, String url) {
        super(endpointName, responseCode + " " + responseMessage, url);
        this.responseCode = responseCode;
        this.responseMessage = responseMessage;
    }

    public int getResponseCode() {
        return responseCode;
    }

    public String getResponseMessage() {
        return responseMessage;
    }

    public String getDetailText() {
        return detailText;
    }

}
