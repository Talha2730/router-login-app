from pathlib import Path

proxy = Path('universal-router/app/src/main/java/com/universalrouter/connect/LocalRouterProxy.java')
s = proxy.read_text(encoding='utf-8')
original='''                try (Socket upstream = network.getSocketFactory().createSocket()) {
                    upstream.connect(new InetSocketAddress(address, router.port), 2800);
                    upstream.setSoTimeout(30000);
                    outgoing.write("HTTP/1.1 200 Connection Established\\r\\n\\r\\n"
'''
fixed='''                // Consume CONNECT headers before forwarding TLS bytes.
                int connectHeaderBytes = 0;
                while (true) {
                    String header = line(incoming, 8192);
                    if (header == null) throw new IOException("Incomplete CONNECT");
                    connectHeaderBytes += header.length() + 2;
                    if (connectHeaderBytes > MAX_HEADERS)
                        throw new IOException("CONNECT headers too large");
                    if (header.isEmpty()) break;
                }
                try (Socket upstream = network.getSocketFactory().createSocket()) {
                    upstream.connect(new InetSocketAddress(address, router.port), 2800);
                    upstream.setSoTimeout(30000);
                    outgoing.write("HTTP/1.1 200 Connection Established\\r\\n\\r\\n"
'''
assert s.count(original) == 1, 'HTTPS CONNECT patch anchor not found'
s = s.replace(original, fixed)
proxy.write_text(s, encoding='utf-8')
print('Verified HTTPS CONNECT header fix.')
