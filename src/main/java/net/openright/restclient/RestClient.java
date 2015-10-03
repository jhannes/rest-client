package net.openright.restclient;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.codahale.metrics.Timer.Context;

import net.openright.restclient.util.IOUtil;
import net.openright.restclient.util.Truncator;

public class RestClient {

	final private Logger log;
	private Timer requestTiming;
	private Meter errorCounter;

	final private String endpointRoot;
	private Map<String, String> headers = new HashMap<>();

	private int payloadLogLength = 100;
	private String endpointName;

    public RestClient(String endpointName, String endpointRoot, MetricRegistry metrics) {
        this.endpointName = endpointName;
		this.endpointRoot = endpointRoot;
        this.errorCounter = metrics.meter(MetricRegistry.name(getClass(), endpointName, "errors"));
        this.requestTiming = metrics.timer(MetricRegistry.name(getClass(), endpointName, "requests"));
        this.log = LoggerFactory.getLogger(getClass().getName() + "." + endpointName);
    }

	public String getUrl() {
		return endpointRoot;
	}

	public String getString(String path) throws IOException {
		return get(path, IOUtil::toString).get();
    }

	public <T> Optional<T> get(String path, IOUtil.ReadingFunction<T> transformer) {
		long startTime = System.currentTimeMillis();
		try (Context context = requestTiming.time()) {
			HttpURLConnection connection = createURLConnection(path);
			Optional<T> result = readResponse(connection, transformer);
			log.debug("GET {} {}ms {} {}",
					connection.getResponseCode(), (System.currentTimeMillis() - startTime), connection.getURL(), truncate(result));
			return result;
		} catch (IOException e) {
			errorCounter.mark();
			throw new RestIOException(endpointName, e, endpointRoot + path);
		} catch (RuntimeException e) {
			errorCounter.mark();
			throw e;
		}
	}

	public Optional<String> postString(String path, String content) throws IOException {
		long startTime = System.currentTimeMillis();
		try (Context context = requestTiming.time()) {
			HttpURLConnection connection = createURLConnection(path);
			connection.setRequestMethod("POST");
			IOUtil.copy(content, connection);
			Optional<String> result = readResponse(connection, IOUtil::toString);
			log.debug("GET {} {}ms {} {}",
					connection.getResponseCode(), (System.currentTimeMillis() - startTime), connection.getURL(), truncate(result));
			return result;
		} catch (IOException e) {
			errorCounter.mark();
			throw new RestIOException(endpointName, e, endpointRoot + path);
		} catch (RuntimeException e) {
			errorCounter.mark();
			throw e;
		}
	}

	private <T> Optional<T> readResponse(HttpURLConnection connection, IOUtil.ReadingFunction<T> transformer) throws IOException {
		int responseCode = connection.getResponseCode();
		if (responseCode >= 400) {
			throw new RestHttpException(endpointName, connection);
		}
		if (responseCode == 204) {
			return Optional.empty();
		}
		try (Reader reader = new InputStreamReader(connection.getInputStream(), getCharset(connection))) {
			return Optional.of(transformer.apply(reader));
		} catch (RuntimeException e) {
			throw new RestParseException(endpointName, e, connection.getURL().toString());
		}
	}

	private Truncator truncate(Optional<?> o) {
		return new Truncator(o, payloadLogLength);
	}


	public void setBasicAuth(String username, String password) {
		String encoded = Base64.getEncoder().encodeToString((username+":"+password).getBytes(StandardCharsets.UTF_8));
		this.setHeader("Authorization", "Basic " + encoded);
	}

	public void setHeader(String headerName, String headerValue) {
		this.headers.put(headerName, headerValue);
	}

	private HttpURLConnection createURLConnection(String path) throws IOException {
		URL url = new URL(new URL(endpointRoot), path);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        for (Entry<String, String> entry : headers.entrySet()) {
			connection.setRequestProperty(entry.getKey(), entry.getValue());
		}
		return connection;
	}

	public Timer getRequestTiming() {
		return requestTiming;
	}

	public Meter getErrorCounter() {
		return errorCounter;
	}

	public void setPayloadLogLength(int payloadLogLength) {
		this.payloadLogLength = payloadLogLength;
	}

	private static Charset getCharset(HttpURLConnection connection) {
		return Optional.ofNullable(connection.getHeaderField("Content-Type"))
				.filter(s -> s.contains("; charset="))
				.map(s -> s.substring(s.indexOf("; charset=") + "; charset=".length()))
				.map(Charset::forName)
				.orElse(StandardCharsets.UTF_8);
	}

}
