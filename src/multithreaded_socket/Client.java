package multithreaded_socket;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

class ClientThread extends Thread {
    private String hostname;
    private int port;
    private int clientId;
    private Socket socket = null;
    private PrintWriter writer = null;

    ClientThread(String hostname, int port, int clientId) {
        this.hostname = hostname;
        this.port = port;
        this.clientId = clientId;
    }

    @Override
    public void run() {
        try {
            socket = new Socket(hostname, port);
            System.out.println("Client#" + clientId + " connected.");

            Scanner reader = new Scanner(socket.getInputStream());
            writer = new PrintWriter(socket.getOutputStream(), true);

            while (reader.hasNextLine()) {
                String response = reader.nextLine();
                System.out.println("Server -> Client#" + clientId + ": " + response);
            }

            reader.close();
        } catch (IOException e) {
            System.err.println("Error at ClientThread-" + threadId() + " : " + e.getMessage());
        }
    }

    void sendMessage(String msg) {
        if (isConnected()) {
            writer.println(msg);

            if (msg.equalsIgnoreCase("bye")) {
                try {
                    writer.close();
                    socket.close();

                    writer = null;
                    socket = null;
                } catch (IOException e) {
                    System.err.println("Error at ClientThread-" + threadId() + " : " + e.getMessage());
                }
            }
        } else {
            System.out.println("Client " + clientId + " not ready yet.");
        }
    }

    int getClientId() {
        return clientId;
    }

    boolean isConnected() {
        if (socket == null) {
            return false;
        }

        return socket.isConnected();
    }
}


class Client {
    public static void main(String[] args) {
        String hostname = "localhost";
        int port = 6000;

        Scanner input = new Scanner(System.in);
        List<ClientThread> clients = new ArrayList<>();
        int nextClientId = 0;

        while (true) {
            System.out.println("\n===== MENU =====");
            System.out.println("1. Add new client");
            System.out.println("2. Send message to a client");
            System.out.println("3. List clients");
            System.out.println("4. Exit");
            System.out.print("Choose option: ");

            int choice = input.nextInt();
            input.nextLine(); // clear input buffer

            switch (choice) {
                case 1 -> {
                    ClientThread clientThread = new ClientThread(hostname, port, nextClientId++);
                    clients.add(clientThread);
                    clientThread.start();
                }

                case 2 -> {
                    System.out.print("Enter client ID: ");
                    int id = input.nextInt();
                    input.nextLine();

                    if (id < 0 || id >= nextClientId) {
                        System.err.println("Error: Invalid client id.");
                        break;
                    }

                    if (!clients.get(id).isConnected()) {
                        System.err.println("Error: Client not connected.");
                        break;
                    }

                    System.out.print("Message as Client#" + id + ": ");
                    String line = input.nextLine();
                    clients.get(id).sendMessage(line);
                }

                case 3 -> {
                    System.out.print("Active client IDs: ");
                    for (ClientThread client : clients) {
                        if (client.isConnected()) {
                            System.out.print(client.getClientId() + " ");
                        }
                    }
                    System.out.println();
                }

                case 4 -> {
                    System.out.println("Program exited.");
                    System.exit(0);
                }

                default -> System.err.println("Invalid option.");
            }
        }
    }
}
