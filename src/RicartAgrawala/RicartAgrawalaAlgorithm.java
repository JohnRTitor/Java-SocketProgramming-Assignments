package RicartAgrawala;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Scanner;

// Each process runs as a separate thread
class Process extends Thread {

    // Total number of processes in the system
    static int totalProcesses;

    // Array to store all process objects
    static Process[] processes;

    // Stores last printed queue to avoid duplicate printing
    static String lastQueue = "";

    // Unique process ID
    int pid;

    // Timestamp
    long requestTimestamp = 0;

    // Flags to track process state
    boolean requestingCS = false;
    boolean inCriticalSection = false;

    // Count of replies received from other processes
    int repliesReceived = 0;

    // Stores which processes are waiting for reply (deferred)
    boolean[] deferredReplies;

    // Random generator to simulate random CS requests
    Random random = new Random();

    // Constructor
    Process(int id) {
        this.pid = id;

        // Initialize deferred reply array
        deferredReplies = new boolean[totalProcesses];

        // Store process in global array
        processes[id] = this;

        System.out.println("Thread created with ID: " + pid);
    }

    // Thread execution starts here
    public void run() {
        try {
            while (true) {

                // Wait for some time before next action
                Thread.sleep(1200);

                // Randomly decide whether to request CS
                if (random.nextBoolean()) {
                    requestCS();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    void requestCS() {

        // If already requesting or inside CS, do nothing
        if (requestingCS || inCriticalSection) return;

        // Get system timestamp
        requestTimestamp = System.currentTimeMillis();

        // Mark that this process is requesting CS
        requestingCS = true;

        // Reset reply counter
        repliesReceived = 0;

        System.out.println("\nProcess " + pid +
                " wants to ENTER CS at time " + requestTimestamp);

        // Print current request queue
        printQueue();

        // Send request to all other processes
        for (int i = 0; i < totalProcesses; i++) {
            if (i != pid) {
                sendRequest(processes[i]);
            }
        }
    }

    void sendRequest(Process other) {

        System.out.println("Process " + pid +
                " sending REQUEST (SMS) to " + other.pid);

        // Call receiver's method
        other.receiveRequest(pid, requestTimestamp);
    }

    void receiveRequest(int senderId, long senderTime) {
        boolean sendReply;

        // Case 1: If not requesting and not in CS, reply immediately
        if (!requestingCS && !inCriticalSection) {
            sendReply = true;
        }

        // Case 2: If currently inside CS, do not reply (defer)
        else if (inCriticalSection) {
            sendReply = false;

            System.out.println("Process " + pid +
                    " is IN CS, " + senderId + " must WAIT");
        }

        // Case 3: If also requesting, compare timestamps
        else {
            // Priority based on smaller timestamp or smaller process ID
            if (senderTime < requestTimestamp ||
                    (senderTime == requestTimestamp && senderId < pid)) {
                sendReply = true;
            } else {
                sendReply = false;
            }
        }

        // If allowed, send reply
        if (sendReply) {
            sendReply(senderId);
        } else {
            // Otherwise defer reply
            deferredReplies[senderId] = true;
        }
    }

    void sendReply(int receiverId) {
        System.out.println("Process " + pid +
                " REPLIED to " + receiverId);

        // Send reply to receiver
        processes[receiverId].receiveReply(pid);
    }

    void receiveReply(int senderId) {
        // Increase reply count
        repliesReceived++;

        System.out.println("Process " + pid +
                " received REPLY from " + senderId +
                " (" + repliesReceived + "/" + (totalProcesses - 1) + ")");

        // If all replies received, enter CS
        if (repliesReceived == totalProcesses - 1) {
            enterCS();
        }
    }

    void enterCS() {

        // Mark that process entered CS
        inCriticalSection = true;

        // Reset requesting flag
        requestingCS = false;

        System.out.println("\n>>> Process " + pid + " ENTER CS");

        try {
            // Simulate execution inside CS
            Thread.sleep(1000);
        } catch (Exception e) {
        }

        // Exit CS after execution
        exitCS();
    }

    void exitCS() {

        // Mark exit from CS
        inCriticalSection = false;

        System.out.println("<<< Process " + pid + " COMPLETED CS");

        // Print updated queue
        printQueue();

        // Send replies to all deferred processes
        for (int i = 0; i < totalProcesses; i++) {
            if (deferredReplies[i]) {

                System.out.println("Process " + pid +
                        " now replying to deferred " + i);

                sendReply(i);

                // Reset deferred flag
                deferredReplies[i] = false;
            }
        }
    }

    static void printQueue() {

        // List to store requesting processes
        List<long[]> queue = new ArrayList<>();

        // Collect all processes requesting CS
        for (int i = 0; i < totalProcesses; i++) {
            Process p = processes[i];

            if (p.requestingCS) {
                queue.add(new long[]{p.pid, p.requestTimestamp});
            }
        }

        // Sort queue by timestamp, then process ID
        queue.sort((a, b) -> {
            if (a[1] == b[1]) return (int) (a[0] - b[0]);
            return Long.compare(a[1], b[1]);
        });

        // Build queue string
        StringBuilder current = new StringBuilder();
        for (long[] q : queue) {
            current.append("[P").append(q[0])
                    .append(",T").append(q[1]).append("] ");
        }

        // Print only if queue has changed
        if (!current.toString().equals(lastQueue)) {
            System.out.println("QUEUE: " + current);
            lastQueue = current.toString();
        }
    }
}

public class RicartAgrawalaAlgorithm {

    public static void main(String[] args) {

        Scanner sc = new Scanner(System.in);

        // Take input for number of processes
        System.out.print("Enter number of processes: ");
        int n = sc.nextInt();

        // Initialize static variables
        Process.totalProcesses = n;
        Process.processes = new Process[n];

        Process[] all = new Process[n];

        // Create and start all processes
        for (int i = 0; i < n; i++) {
            all[i] = new Process(i);
            all[i].start();
        }
    }
}
