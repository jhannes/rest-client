package net.openright.restclient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.jsonbuddy.JsonObject;
import org.jsonbuddy.parse.JsonParser;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.MetricRegistry;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpServer;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.CyclicBufferAppender;
import net.openright.restclient.util.IOUtil;

public class RestClientTest {

    private static HttpServer server;

    @BeforeClass
    public static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.start();
    }

    @AfterClass
    public static void stopServer() throws IOException {
        server.stop(0);
    }

    private MetricRegistry metrics = new MetricRegistry();

    private RestClient restClient = new RestClient("TestEndpoint", "http://localhost:" + server.getAddress().getPort(), metrics);

    @Test
    public void shouldGetString() throws Exception {
        HttpContext context = server.createContext("/test", (exchange) -> {
            exchange.sendResponseHeaders(200, 0);
            IOUtil.copy("This is a test", exchange.getResponseBody());
        });

        assertThat(restClient.getString(context.getPath())).isEqualTo("This is a test");
    }

    @Test
    public void shouldLogResult() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(RestClient.class);
        CyclicBufferAppender<ILoggingEvent> appender = new CyclicBufferAppender<>();
        appender.start();
        logger.addAppender(appender);

        restClient.setPayloadLogLength(12);

        HttpContext context = server.createContext("/logging", (exchange) -> {
            exchange.sendResponseHeaders(206, 0);
            IOUtil.copy("Message with truncated part", exchange.getResponseBody());
        });

        restClient.getString(context.getPath());

        assertThat(appender.getLength()).isEqualTo(1);
        ILoggingEvent loggingEvent = appender.get(0);
        assertThat(loggingEvent.getLevel()).isEqualTo(Level.DEBUG);
        assertThat(loggingEvent.getLoggerName())
            .contains("TestEndpoint");
        assertThat(loggingEvent.getFormattedMessage())
            .contains(restClient.getUrl())
            .contains(context.getPath())
            .contains("206")
            .contains("Message with")
            .doesNotContain("truncated part");
    }

    @Test
    public void shouldLogEmptyResult() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(RestClient.class);
        CyclicBufferAppender<ILoggingEvent> appender = new CyclicBufferAppender<>();
        appender.start();
        logger.addAppender(appender);

        HttpContext context = server.createContext("/loggingEmpty", (exchange) -> {
            exchange.sendResponseHeaders(204, -1);
        });

        restClient.get(context.getPath(), JsonParser::parseToObject);

        assertThat(appender.getLength()).isEqualTo(1);
        ILoggingEvent loggingEvent = appender.get(0);
        assertThat(loggingEvent.getLevel()).isEqualTo(Level.DEBUG);
        assertThat(loggingEvent.getFormattedMessage())
            .contains(restClient.getUrl())
            .contains("204")
            .contains(context.getPath());
    }

    @Test
    public void shouldGetJSON() throws Exception {
        HttpContext context = server.createContext("/testJSON", (exchange) -> {
            exchange.sendResponseHeaders(200, 0);
            IOUtil.copy("{\"foo\":1,\"bar\":2}", exchange.getResponseBody());
        });
        restClient.setHeader("Accept", "application/json");
        JsonObject obj = restClient.get(context.getPath(), JsonParser::parseToObject).get();
        assertThat(obj.longValue("foo").get()).isEqualTo(1);
    }

    @Test
    public void shouldHandleParseException() throws Exception {
        HttpContext context = server.createContext("/parseError", (exchange) -> {
            exchange.sendResponseHeaders(200, 0);
            IOUtil.copy("test", exchange.getResponseBody());
        });
        RestException e = (RestException) catchThrowable(
                () -> restClient.get(context.getPath(), (reader) -> {
                    throw new IllegalArgumentException("Something failed");
                }).get());
        assertThat(e.getUrl()).isEqualTo(restClient.getUrl() + context.getPath());
        assertThat(e)
            .hasMessage("java.lang.IllegalArgumentException: Something failed")
            .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void shouldGetStringWithDifferentEncoding() throws Exception {
        HttpContext context = server.createContext("/test2", (exchange) -> {
            Charset charset = StandardCharsets.ISO_8859_1;
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=" + charset.name());
            exchange.sendResponseHeaders(200, 0);
            IOUtil.copy("זרו", exchange.getResponseBody(), charset);
        });

        assertThat(restClient.getString(context.getPath())).isEqualTo("זרו");
    }

    @Test
    public void shouldGetEmptyString() throws Exception {
        HttpContext context = server.createContext("/shouldGetEmptyString", (exchange) -> {
            exchange.sendResponseHeaders(204, -1);
        });

        assertThat(restClient.get(context.getPath(), IOUtil::toString)).isEmpty();
    }

    @Test
    public void shouldReport4xxErrors() throws Exception {
        HttpContext context = server.createContext("/test3", (exchange) -> {
            exchange.sendResponseHeaders(400, 0);
            IOUtil.copy("This is the error details", exchange.getResponseBody());
        });

        RestHttpException e = (RestHttpException) catchThrowable(() -> restClient.getString(context.getPath()));
        assertThat(e)
            .isInstanceOf(RestHttpException.class)
            .hasMessageContaining("400 Bad Request");
        assertThat(e.getDetailText()).isEqualTo("This is the error details");
        assertThat(e.getResponseCode()).isEqualTo(400);
        assertThat(e.getUrl()).isEqualTo(restClient.getUrl() + context.getPath());
        assertThat(e.getEndpointName()).isEqualTo("TestEndpoint");
    }

    @Test
    public void shouldReport5xxErrors() throws Exception {
        HttpContext context = server.createContext("/test4", (exchange) -> {
            exchange.sendResponseHeaders(500, 0);
            exchange.getResponseBody().close();
        });

        assertThatThrownBy(() -> restClient.getString(context.getPath()))
            .isInstanceOf(RestHttpException.class)
            .hasMessageContaining("500 Internal Server Error");
    }

    @Test
    public void shouldFollow3xxRedirects() throws Exception {
        HttpContext context = server.createContext("/test7", (exchange) -> {
            exchange.sendResponseHeaders(200, 0);
            IOUtil.copy("This is a test", exchange.getResponseBody());
        });
        HttpContext redirectContext = server.createContext("/test6", (exchange) -> {
            exchange.getResponseHeaders().set("Location", restClient.getUrl() + context.getPath());
            exchange.sendResponseHeaders(301, 0);
            exchange.getResponseBody().close();
        });

        assertThat(restClient.getString(redirectContext.getPath())).isEqualTo("This is a test");
    }

    @Test
    public void shouldHandleServerDown() throws Exception {
        restClient = new RestClient("invalid", "http://localhost:222", metrics);
        RestException e = (RestException)
                catchThrowable(() -> restClient.getString("/doesntMatter"));
        assertThat(e).hasMessage("java.net.ConnectException: Connection refused: connect");
        assertThat(e.getUrl()).isEqualTo("http://localhost:222/doesntMatter");
        assertThat(getErrorRate(restClient)).isEqualTo(1.0);
    }

    @Test
    public void shouldHandleServerClosing() throws Exception {
        HttpContext context = server.createContext("/test8", (exchange) -> {
            exchange.getResponseBody().close();
        });

        assertThatThrownBy(() -> restClient.getString(context.getPath()))
            .isInstanceOf(RestException.class)
            .hasMessage("java.net.SocketException: Unexpected end of file from server");
        assertThat(getErrorRate(restClient)).isEqualTo(1.0);
    }

    public double getErrorRate(RestClient restClient) {
        return (double)restClient.getErrorCounter().getCount() /
                (double)restClient.getRequestTiming().getCount();
    }


    private String header;

    @Test
    public void shouldHandleBasicAuth() throws Exception {
        HttpContext context = server.createContext("/test9", (exchange) -> {
            header = exchange.getRequestHeaders().getFirst("Authorization");
            exchange.sendResponseHeaders(204, -1);
        });

        restClient.setBasicAuth("SomeUsername", "SomePassword");
        restClient.get(context.getPath(), IOUtil::toString);

        assertThat(header).isEqualTo("Basic U29tZVVzZXJuYW1lOlNvbWVQYXNzd29yZA==");
    }

    private String payload;
    @Test
    public void shouldPost() throws Exception {
        HttpContext context = server.createContext("/shouldPost", (exchange) -> {
            exchange.sendResponseHeaders(201, 0);
            payload = IOUtil.toString(exchange.getRequestBody());
            IOUtil.copy("This is the content", exchange.getResponseBody());
        });

        assertThat(restClient.postString(context.getPath(), "This is the posted content").get())
            .isEqualTo("This is the content");
        assertThat(payload)
            .isEqualTo("This is the posted content");
    }

}
