package com.jazzwifi.local;

import android.net.Network;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * HTTP-only private loopback proxy for the single original host jazz.wifi.
 *
 * WebView keeps the EXACT browser origin http://jazz.wifi and forwards
 * navigation, POST login forms, cookies, CSS, JS and XMLHttpRequests using
 * standard HTTP proxy semantics. Only TCP to a verified private Wi-Fi IP
 * is possible. The original HTTP Host header is always jazz.wifi.
 *
 * Binding 127.0.0.1 prevents access from outside the phone. It neither
 * disables Private DNS nor installs a VPN or intercepts HTTPS.
 */
public final class JazzHostProxy {
    private static final String HOST = "jazz.wifi";
    private static final int MAX_HEADERS = 65536;
    private static final long MAX_BODY = 32L * 1024 * 1024;
    private final ExecutorService clients = Executors.newCachedThreadPool();
    private ServerSocket server;
    private Thread acceptThread;
    private volatile Network targetNetwork;
    private volatile String targetIp;
    private volatile boolean running;

    public synchronized int start() throws IOException {
        if (running && server != null) return server.getLocalPort();
        server = new ServerSocket(0, 32, InetAddress.getByName("127.0.0.1"));
        server.setSoTimeout(1000);
        running = true;
        acceptThread = new Thread(this::acceptLoop, "Jazz-Loopback-Proxy");
        acceptThread.setDaemon(true);
        acceptThread.start();
        return server.getLocalPort();
    }

    public void setTarget(Network network, String ip) {
        if (network == null || !LocalResolver.isPrivateIpv4(ip)) {
            targetNetwork = null;
            targetIp = null;
            return;
        }
        targetNetwork = network;
        targetIp = ip;
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket client = server.accept();
                client.setSoTimeout(12000);
                clients.execute(() -> serve(client));
            } catch (SocketTimeoutException ignored) {
            } catch (IOException e) {
                if (running) {
                    try { Thread.sleep(100); }
                    catch (InterruptedException ignored) { break; }
                }
            } catch (RuntimeException ignored) { }
        }
    }

    private static String readLine(InputStream input, int limit) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int previous = -1;
        while (bytes.size() < limit) {
            int c = input.read();
            if (c < 0) {
                if (bytes.size() == 0) return null;
                throw new IOException("Truncated header");
            }
            if (c == '\n') {
                byte[] line = bytes.toByteArray();
                int length = line.length;
                if (length > 0 && line[length - 1] == '\r') length--;
                return new String(line, 0, length, StandardCharsets.ISO_8859_1);
            }
            bytes.write(c);
            previous = c;
        }
        throw new IOException("Request headers too large");
    }

    private static void copyFixed(InputStream from, OutputStream to, long count)
            throws IOException {
        if (count < 0 || count > MAX_BODY) throw new IOException("Request body too large");
        byte[] buf = new byte[8192];
        while (count > 0) {
            int len = from.read(buf, 0, (int) Math.min(buf.length, count));
            if (len < 0) throw new IOException("Incomplete request body");
            to.write(buf, 0, len);
            count -= len;
        }
    }

    private static void copyChunked(InputStream from, OutputStream to) throws IOException {
        long bytes = 0;
        while (true) {
            String sizeLine = readLine(from, 128);
            if (sizeLine == null) throw new IOException("Incomplete chunked body");
            int semicolon = sizeLine.indexOf(';');
            String hex = semicolon < 0 ? sizeLine : sizeLine.substring(0, semicolon);
            long chunk;
            try { chunk = Long.parseLong(hex.trim(), 16); }
            catch (NumberFormatException e) { throw new IOException("Invalid chunk size", e); }
            if (chunk < 0 || bytes + chunk > MAX_BODY)
                throw new IOException("Chunked request too large");
            to.write((sizeLine + "\r\n").getBytes(StandardCharsets.ISO_8859_1));
            copyFixed(from, to, chunk);
            bytes += chunk;
            if (chunk == 0) {
                while (true) {
                    String trailer = readLine(from, 2048);
                    if (trailer == null) throw new IOException("Truncated trailer");
                    to.write((trailer + "\r\n").getBytes(StandardCharsets.ISO_8859_1));
                    if (trailer.isEmpty()) break;
                }
                break;
            }
            int a = from.read(), b = from.read();
            if (a != '\r' || b != '\n') throw new IOException("Invalid chunk terminator");
            to.write('\r');
            to.write('\n');
        }
    }

    private void serve(Socket client) {
        try (Socket browser = client) {
            InputStream input = new BufferedInputStream(browser.getInputStream());
            OutputStream output = new BufferedOutputStream(browser.getOutputStream());
            String first = readLine(input, 4096);
            if (first == null) return;
            String[] request = first.split(" ", 3);
            if (request.length != 3 || !request[2].startsWith("HTTP/1.")) {
                error(output, 400, "Bad Request");
                return;
            }
            String method = request[0].toUpperCase(Locale.US);
            if (!(method.equals("GET") || method.equals("POST") ||
                  method.equals("HEAD") || method.equals("PUT") ||
                  method.equals("DELETE") || method.equals("OPTIONS") ||
                  method.equals("PATCH"))) {
                error(output, 405, "Method Not Allowed");
                return;
            }
            String path;
            try {
                URI uri = URI.create(request[1]);
                if (uri.isAbsolute()) {
                    if (!"http".equalsIgnoreCase(uri.getScheme()) ||
                          !HOST.equalsIgnoreCase(uri.getHost()) ||
                          (uri.getPort() != -1 && uri.getPort() != 80)) {
                        error(output, 403, "Only jazz.wifi is permitted");
                        return;
                    }
                    path = uri.getRawPath();
                    if (path == null || path.isEmpty()) path = "/";
                    if (uri.getRawQuery() != null) path += "?" + uri.getRawQuery();
                } else {
                    path = request[1];
                    if (!path.startsWith("/") || path.startsWith("//")) {
                        error(output, 403, "Invalid router path");
                        return;
                    }
                }
                if (path.indexOf('\r') >= 0 || path.indexOf('\n') >= 0)
                    throw new IllegalArgumentException();
            } catch (IllegalArgumentException exception) {
                error(output, 400, "Bad URL");
                return;
            }

            List<String> kept = new ArrayList<>();
            boolean chunks = false;
            long length = 0;
            boolean hasLength = false;
            String host = null;
            int count = 0;
            while (true) {
                String line = readLine(input, MAX_HEADERS);
                if (line == null) throw new IOException("Truncated request");
                count += line.length() + 2;
                if (count > MAX_HEADERS) throw new IOException("Too many headers");
                if (line.isEmpty()) break;
                if (line.charAt(0) == ' ' || line.charAt(0) == '\t')
                    throw new IOException("Folded header is forbidden");
                int colon = line.indexOf(':');
                if (colon <= 0) throw new IOException("Malformed header");
                String key = line.substring(0, colon).trim().toLowerCase(Locale.US);
                String value = line.substring(colon + 1).trim();
                if (key.equals("host")) {
                    host = value.toLowerCase(Locale.US);
                } else if (key.equals("content-length")) {
                    try { length = Long.parseLong(value); hasLength = true; }
                    catch (NumberFormatException e) { throw new IOException("Bad content-length"); }
                } else if (key.equals("transfer-encoding")) {
                    if (!value.equalsIgnoreCase("chunked"))
                        throw new IOException("Unsupported transfer encoding");
                    chunks = true;
                } else if (!key.equals("connection") &&
                           !key.equals("proxy-connection") &&
                           !key.equals("keep-alive") &&
                           !key.equals("proxy-authorization") &&
                           !key.equals("upgrade") &&
                           !key.equals("expect") &&
                           !key.equals("accept-encoding") &&
                           !key.equals("content-encoding")) {
                    kept.add(line);
                }
            }
            if (host != null && !host.equals(HOST) && !host.equals(HOST + ":80")) {
                error(output, 403, "Only jazz.wifi is permitted");
                return;
            }
            if (hasLength && chunks) {
                error(output, 400, "Ambiguous request framing");
                return;
            }
            if (hasLength && (length < 0 || length > MAX_BODY)) {
                error(output, 413, "Payload Too Large");
                return;
            }
            Network network = targetNetwork;
            String ip = targetIp;
            if (network == null || !LocalResolver.isPrivateIpv4(ip)) {
                error(output, 503, "Jazz router is unavailable");
                return;
            }
            try (Socket upstream = network.getSocketFactory().createSocket()) {
                upstream.connect(new java.net.InetSocketAddress(ip, 80), 2500);
                upstream.setSoTimeout(16000);
                OutputStream remote = new BufferedOutputStream(upstream.getOutputStream());
                InputStream response = new BufferedInputStream(upstream.getInputStream());
                remote.write((method + " " + path + " HTTP/1.1\r\n" +
                    "Host: " + HOST + "\r\n" +
                    "Connection: close\r\n").getBytes(StandardCharsets.ISO_8859_1));
                for (String header : kept)
                    remote.write((header + "\r\n").getBytes(StandardCharsets.ISO_8859_1));
                if (chunks) {
                    remote.write("Transfer-Encoding: chunked\r\n".getBytes(
                        StandardCharsets.ISO_8859_1));
                } else if (hasLength) {
                    remote.write(("Content-Length: " + length + "\r\n").getBytes(
                        StandardCharsets.ISO_8859_1));
                }
                remote.write("\r\n".getBytes(StandardCharsets.ISO_8859_1));
                if (chunks) copyChunked(input, remote);
                else if (hasLength) copyFixed(input, remote, length);
                remote.flush();

                byte[] buffer = new byte[8192];
                int amount;
                while ((amount = response.read(buffer)) != -1) {
                    output.write(buffer, 0, amount);
                }
                output.flush();
            }
        } catch (IOException ignored) {
            // The UI reports connection errors; do not log request contents or credentials.
        }
    }

    private static void error(OutputStream output, int code, String description)
            throws IOException {
        String body = description + "\n";
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        output.write(("HTTP/1.1 " + code + " " + description + "\r\n" +
            "Content-Type: text/plain; charset=utf-8\r\n" +
            "Cache-Control: no-store\r\n" +
            "Connection: close\r\n" +
            "Content-Length: " + payload.length + "\r\n\r\n")
            .getBytes(StandardCharsets.ISO_8859_1));
        output.write(payload);
        output.flush();
    }

    public synchronized void stop() {
        running = false;
        targetIp = null;
        targetNetwork = null;
        if (server != null) {
            try { server.close(); } catch (IOException ignored) { }
            server = null;
        }
        if (acceptThread != null) acceptThread.interrupt();
        clients.shutdownNow();
    }
}
