package multithreaded_socket;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Scanner;

class ServerThread extends Thread {
    private int clientId;
    private Socket socket;

    ServerThread(int clientId, Socket socket) {
        this.clientId = clientId;
        this.socket = socket;
    }

    public void run() {
        try (Scanner reader = new Scanner(socket.getInputStream());
             PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)) {

            System.out.println("Client #" + clientId + " connected from " + socket.getInetAddress() + ":" + socket.getPort());

            while (reader.hasNextLine()) {
                String text = reader.nextLine();
                System.out.println("Client #" + clientId + " says: " + text);

                if (text.equalsIgnoreCase("bye")) {
                    writer.println("Termination request accepted from Client#" + clientId);
                    break;
                } else {
                    writer.println("Received message from Client#" + clientId);
                }
            }
            
            System.out.println("Client#" + clientId + " disconnected from " + socket.getInetAddress() + ":" + socket.getPort());
            socket.close();
        } catch (IOException e) {
            System.err.println("Error at ServerThread-" + threadId() + " : " + e.getMessage());
        }
    }

}

class Server {
    public static void main(String[] args) {
        int port = 6000;

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server is listening on port " + port);

            int nextClientId = 0;
            while (true) {
                // Waits for a new client here
                Socket socket = serverSocket.accept();

                // New client connected, handle it on another thread
                // Assign a new clientId
                new ServerThread(nextClientId++, socket).start();
            }
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }
    }
}
