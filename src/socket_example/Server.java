package socket_example;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Scanner;

class Server {
    public static void main(String[] args) {
        int port = 6000;

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server is listening on port " + port);
            System.out.println("Waiting for client...");

            try (Socket socket = serverSocket.accept()) {
                System.out.println("Client connected from: " + socket.getInetAddress() + ":" + socket.getPort());

                Scanner reader = new Scanner(socket.getInputStream());
                PrintWriter writer = new PrintWriter(socket.getOutputStream(), true); // true autoflushes

                while (reader.hasNextLine()) {
                    String text = reader.nextLine();
                    System.out.println("Client message: " + text);

                    if (text.equalsIgnoreCase("bye")) {
                        writer.println("Termination request accepted.");
                        break;
                    } else {
                        writer.println("Received!");
                    }
                }

                reader.close();
                writer.close();
            }
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }
    }
}