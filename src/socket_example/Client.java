package socket_example;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;

class Client {
    public static void main(String[] args) {
        String hostname = "localhost";
        int port = 6000;

        System.out.println("Connecting to " + hostname + ":" + port + "...");
        try (Socket socket = new Socket(hostname, port)) {
            System.out.println("Connected!");

            Scanner reader = new Scanner(socket.getInputStream());
            PrintWriter writer = new PrintWriter(socket.getOutputStream(), true); // true auto flushes

            Scanner input = new Scanner(System.in);
            System.out.println("Send messages to the server!");
            System.out.println("Type your messages below!");

            while (true) {
                String msg = input.nextLine();
                writer.println(msg);

                String response = reader.nextLine();
                System.out.println("Server Response: " + response);

                if (msg.equalsIgnoreCase("bye")) {
                    break;
                }
            }

            reader.close();
            writer.close();
            input.close();
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }
    }
}