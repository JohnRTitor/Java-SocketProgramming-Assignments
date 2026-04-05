package socket_example;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;

public class Server {
    public static void main(String[] args) throws IOException {
        // Create Selector and ServerSocketChannel
        Selector selector = Selector.open();
        try (ServerSocketChannel serverSocket = ServerSocketChannel.open()) {
            serverSocket.bind(new InetSocketAddress("localhost", 9999));
            serverSocket.configureBlocking(false); // Make non-blocking

            // Register server socket to accept connections
            serverSocket.register(selector, SelectionKey.OP_ACCEPT);

            while (true) {
                selector.select(); // Block until events
                Set<SelectionKey> selectedKeys = selector.selectedKeys();
                Iterator<SelectionKey> iter = selectedKeys.iterator();

                while (iter.hasNext()) {
                    SelectionKey key = iter.next();

                    if (key.isAcceptable()) {
                        // Accept new connection
                        SocketChannel client = serverSocket.accept();
                        client.configureBlocking(false);
                        client.register(selector, SelectionKey.OP_READ);
                        System.out.println("Client Connected");
                    }
                    if (key.isReadable()) {
                        // Read data
                        SocketChannel client = (SocketChannel) key.channel();
                        ByteBuffer buffer = ByteBuffer.allocate(256);
                        client.read(buffer);
                        System.out.println("Received: " + new String(buffer.array()).trim());
                        // In real app, write back to client here
                    }
                    iter.remove();
                }
            }
        } catch ()
    }
}