# rest-client is a very simple Java REST client

It uses HttpURLConnection as the basis for HTTP communication
and has no real dependencies. As a proof-of-concept, it is
implemented to use Dropwizard Metrics, but this dependency
can easily be dropped. And may be in the future

## Features

* Execute GET and POST requests
* Convert response Reader to any format
* Convert exceptions to the URL that caused them

## Example usage


### Get a string from an url

    RestClient restClient = new RestClient("TestEndpoint", "http://hostname/path", metrics);
    String response = restClient.getString(context.getPath());
  
### Get a JSON response from an url

    RestClient restClient = new RestClient("TestEndpoint", "http://hostname/path", metrics);
    restClient.setHeader("Accept", "application/json");
    JsonObject response = restClient.get(context.getPath(), JsonParser::parseObject);

### Authenticate with BASIC authentication

    RestClient restClient = new RestClient("TestEndpoint", "http://hostname/path", metrics);
    restClient.setBasicAuth("SomeUsername", "SomePassword");
    String response = restClient.get(context.getPath(), IOUtil::toString);

### Post request

     RestClient restClient = new RestClient("TestEndpoint", "http://hostname/path", metrics);
     restClient.postString(context.getPath(), "This is the posted content");
