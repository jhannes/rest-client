package net.openright.restclient.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class IOUtil {
    public interface ReadingFunction<T> {
        T apply(Reader reader) throws IOException;
    }

    public static String toString(InputStream in) throws IOException {
        return toString(in, StandardCharsets.UTF_8);
    }

    public static String toString(InputStream in, Charset charset) throws IOException {
        return toString(new InputStreamReader(in, charset));
    }

    public static String toString(Reader reader) throws IOException {
        try {
            char[] arr = new char[8 * 1024];
            StringBuilder buffer = new StringBuilder();
            int numCharsRead;
            while ((numCharsRead = reader.read(arr, 0, arr.length)) != -1) {
                buffer.append(arr, 0, numCharsRead);
            }
            return buffer.toString();
        } finally {
            reader.close();
        }
    }

    public static void copy(String content, HttpURLConnection connection) throws IOException {
        connection.setDoOutput(true);
        copy(content, connection.getOutputStream());
    }

    public static void copy(String content, OutputStream out) throws IOException {
        copy(content, out, StandardCharsets.UTF_8);
    }

    public static void copy(String content, OutputStream out, Charset charset) throws IOException {
        copy(content, new OutputStreamWriter(out, charset));
    }

    public static void copy(String content, Writer writer) throws IOException {
        try {
            writer.write(content);
        } finally {
            writer.close();
        }
    }



}
