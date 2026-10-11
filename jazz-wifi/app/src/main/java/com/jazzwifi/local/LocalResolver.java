package com.jazzwifi.local;

import android.net.LinkProperties;
import android.net.Network;
import android.net.RouteInfo;
import java.io.ByteArrayOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.security.SecureRandom;

/**
 * Finds a private Wi-Fi router IP without consulting Android Private DNS.
 *
 * Reads local DHCP gateway and, when available, directly queries DHCP's
 * private DNS server over UDP port 53 for jazz.wifi (bound to Wi-Fi).
 * No external DNS, cloud proxy, VPN or Android DNS setting changes are used.
 */
public final class LocalResolver {
    private static final SecureRandom RANDOM = new SecureRandom();
    private LocalResolver() { }

    public static boolean isPrivateIpv4(String value) {
        if (value == null) return false;
        String[] parts = value.trim().split("\\.", -1);
        if (parts.length != 4) return false;
        int[] bytes = new int[4];
        for (int i = 0; i < 4; i++) {
            if (parts[i].isEmpty() || parts[i].length() > 3) return false;
            for (int c = 0; c < parts[i].length(); c++) {
                if (!Character.isDigit(parts[i].charAt(c))) return false;
            }
            try {
                bytes[i] = Integer.parseInt(parts[i]);
                if (bytes[i] < 0 || bytes[i] > 255) return false;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return bytes[0] == 10
            || (bytes[0] == 172 && bytes[1] >= 16 && bytes[1] <= 31)
            || (bytes[0] == 192 && bytes[1] == 168)
            || (bytes[0] == 169 && bytes[1] == 254)
            || (bytes[0] == 100 && bytes[1] >= 64 && bytes[1] <= 127);
    }

    private static String gateway(LinkProperties properties) {
        if (properties == null) return null;
        String fallback = null;
        for (RouteInfo route : properties.getRoutes()) {
            InetAddress address = route.getGateway();
            if (address instanceof Inet4Address && isPrivateIpv4(address.getHostAddress())) {
                if (route.isDefaultRoute()) return address.getHostAddress();
                if (fallback == null) fallback = address.getHostAddress();
            }
        }
        return fallback;
    }

    public static List<String> routerCandidates(Network wifi,
                                                LinkProperties properties,
                                                String userIp) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (isPrivateIpv4(userIp)) result.add(userIp);
        String gw = gateway(properties);
        if (wifi != null && properties != null) {
            List<InetAddress> resolvers = new ArrayList<>(properties.getDnsServers());
            if (gw != null) {
                try {
                    InetAddress gatewayAddress = InetAddress.getByName(gw);
                    if (!resolvers.contains(gatewayAddress)) resolvers.add(gatewayAddress);
                } catch (Exception ignored) { }
            }
            for (InetAddress resolver : resolvers) {
                if (!(resolver instanceof Inet4Address) ||
                        !isPrivateIpv4(resolver.getHostAddress())) continue;
                String resolved = directDns(wifi, resolver, "jazz.wifi");
                if (isPrivateIpv4(resolved)) result.add(resolved);
            }
        }
        if (gw != null) result.add(gw);
        // Fallbacks are tried ONLY on the selected Wi-Fi network, never over
        // cellular; a connected device may use any of these gateway addresses.
        result.add("192.168.0.1");
        result.add("192.168.1.1");
        result.add("192.168.8.1");
        return new ArrayList<>(result);
    }

    private static String directDns(Network network, InetAddress resolver,
                                    String hostname) {
        DatagramSocket socket = null;
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            int transaction = RANDOM.nextInt(65536);
            bytes.write((transaction >>> 8) & 0xff);
            bytes.write(transaction & 0xff);
            bytes.write(new byte[]{1, 0, 0, 1, 0, 0, 0, 0, 0, 0});
            for (String label : hostname.toLowerCase(Locale.US).split("\\.")) {
                byte[] part = label.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
                if (part.length > 63) return null;
                bytes.write(part.length);
                bytes.write(part);
            }
            bytes.write(new byte[]{0, 0, 1, 0, 1});
            byte[] query = bytes.toByteArray();

            socket = new DatagramSocket();
            network.bindSocket(socket);
            socket.setSoTimeout(750);
            socket.send(new DatagramPacket(query, query.length, resolver, 53));
            byte[] buffer = new byte[1500];
            DatagramPacket reply = new DatagramPacket(buffer, buffer.length);
            socket.receive(reply);
            return parseAResponse(buffer, reply.getLength(), transaction);
        } catch (Exception ignored) {
            return null;
        } finally {
            if (socket != null) socket.close();
        }
    }

    static String parseAResponse(byte[] bytes, int len, int id) {
        if (bytes == null || len < 12) return null;
        if (u16(bytes, 0) != id || (bytes[2] & 0x80) == 0 ||
                (bytes[3] & 0x0f) != 0) return null;
        int questions = u16(bytes, 4);
        int answers = u16(bytes, 6);
        if (questions < 1 || questions > 8 || answers > 48) return null;
        int position = 12;
        for (int i = 0; i < questions; i++) {
            position = skipName(bytes, len, position);
            if (position < 0 || position + 4 > len) return null;
            position += 4;
        }
        for (int i = 0; i < answers; i++) {
            position = skipName(bytes, len, position);
            if (position < 0 || position + 10 > len) return null;
            int type = u16(bytes, position);
            int dnsClass = u16(bytes, position + 2);
            int recordLength = u16(bytes, position + 8);
            position += 10;
            if (position + recordLength > len) return null;
            if (type == 1 && dnsClass == 1 && recordLength == 4) {
                String ip = String.format(Locale.US, "%d.%d.%d.%d",
                    bytes[position] & 0xff, bytes[position + 1] & 0xff,
                    bytes[position + 2] & 0xff, bytes[position + 3] & 0xff);
                if (isPrivateIpv4(ip)) return ip;
            }
            position += recordLength;
        }
        return null;
    }

    private static int u16(byte[] b, int at) {
        return ((b[at] & 255) << 8) | (b[at + 1] & 255);
    }

    private static int skipName(byte[] bytes, int length, int offset) {
        int end = offset;
        for (int count = 0; count < 128; count++) {
            if (end >= length) return -1;
            int marker = bytes[end] & 0xff;
            if (marker == 0) return end + 1;
            if ((marker & 0xc0) == 0xc0)
                return end + 1 < length ? end + 2 : -1;
            if ((marker & 0xc0) != 0 || marker > 63 ||
                    end + marker + 1 >= length) return -1;
            end += marker + 1;
        }
        return -1;
    }
}
