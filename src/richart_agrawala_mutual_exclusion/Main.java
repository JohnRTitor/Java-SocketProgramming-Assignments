package richart_agrawala_mutual_exclusion;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

// Each process runs as a separate thread
class Process extends Thread {

    static int totalProcesses;
    static Process[] processes;

    static String lastQueue = "";

    int pid;

    long requestTimestamp = 0;

    boolean requestingCS = false;
    boolean inCriticalSection = false;

    int repliesReceived = 0;

    boolean[] deferredReplies;

    Process(int id) {
        this.pid = id;

        deferredReplies = new boolean[totalProcesses];

        processes[id] = this;

        System.out.println("Thread created with ID: " + pid);
    }

    // Thread execution starts here
    public void run() {
        requestCS();
    }

    void requestCS() {

        if (requestingCS || inCriticalSection) return;

        requestTimestamp = System.currentTimeMillis();

        requestingCS = true;

        repliesReceived = 0;

        System.out.println("\nProcess " + pid +
                " wants to ENTER CS at time " + requestTimestamp);

        printQueue();

        for (int i = 0; i < totalProcesses; i++) {
            if (i != pid) {
                sendRequest(processes[i]);
            }
        }
    }

    void sendRequest(Process other) {

        System.out.println("Process " + pid +
                " sending REQUEST (SMS) to " + other.pid);

        other.receiveRequest(pid, requestTimestamp);
    }

    void receiveRequest(int senderId, long senderTime) {
        boolean sendReply;

        if (!requestingCS && !inCriticalSection) {
            sendReply = true;
        } else if (inCriticalSection) {
            sendReply = false;

            System.out.println("Process " + pid +
                    " is IN CS, " + senderId + " must WAIT");
        } else {

            if (senderTime < requestTimestamp ||
                    (senderTime == requestTimestamp && senderId < pid)) {
                sendReply = true;
            } else {
                sendReply = false;
            }
        }

        if (sendReply) {
            sendReply(senderId);
        } else {
            deferredReplies[senderId] = true;
        }
    }

    void sendReply(int receiverId) {
        System.out.println("Process " + pid +
                " REPLIED to " + receiverId);

        processes[receiverId].receiveReply(pid);
    }

    void receiveReply(int senderId) {

        repliesReceived++;

        System.out.println("Process " + pid +
                " received REPLY from " + senderId +
                " (" + repliesReceived + "/" + (totalProcesses - 1) + ")");

        if (repliesReceived == totalProcesses - 1) {
            enterCS();
        }
    }

    void enterCS() {

        inCriticalSection = true;

        requestingCS = false;

        System.out.println("\n>>> Process " + pid + " ENTER CS");

        try {
            Thread.sleep(3000);
        } catch (Exception e) {
            e.printStackTrace();
        }

        exitCS();
    }

    void exitCS() {

        inCriticalSection = false;

        System.out.println("<<< Process " + pid + " COMPLETED CS");

        printQueue();

        for (int i = 0; i < totalProcesses; i++) {
            if (deferredReplies[i]) {

                System.out.println("Process " + pid +
                        " now replying to deferred " + i);

                sendReply(i);

                deferredReplies[i] = false;
            }
        }
    }

    static void printQueue() {

        List<long[]> queue = new ArrayList<>();

        for (int i = 0; i < totalProcesses; i++) {
            Process p = processes[i];

            if (p.requestingCS) {
                queue.add(new long[]{p.pid, p.requestTimestamp});
            }
        }

        queue.sort((a, b) -> {
            if (a[1] == b[1]) return (int) (a[0] - b[0]);
            return Long.compare(a[1], b[1]);
        });

        StringBuilder current = new StringBuilder();

        for (long[] q : queue) {
            current.append("[P").append(q[0])
                    .append(",T").append(q[1]).append("] ");
        }

        if (!current.toString().equals(lastQueue)) {
            System.out.println("QUEUE: " + current);
            lastQueue = current.toString();
        }
    }
}

class Main {

    public static void main(String[] args) {

        Scanner sc = new Scanner(System.in);

        System.out.print("Enter number of processes: ");
        int n = sc.nextInt();

        Process.totalProcesses = n;
        Process.processes = new Process[n];

        Process[] all = new Process[n];

        for (int i = 0; i < n; i++) {
            all[i] = new Process(i);
        }

        List<Integer> criticalSectionRequestors = new ArrayList<>();

        System.out.println(
                "Which processes should request CS? (0 - " +
                        (n - 1) + ") -1 to stop input"
        );

        while (true) {

            int processId = sc.nextInt();

            if (processId == -1 &&
                    criticalSectionRequestors.isEmpty()) {

                System.out.println(
                        "No process is requesting CS. Terminating..."
                );

                System.exit(0);
            } else if (processId == -1) {
                System.out.println("Continuing execution...");
                break;
            } else if (processId < 0 || processId >= n) {
                System.out.println("Invalid process ID: " + processId);
            } else if (criticalSectionRequestors.contains(processId)) {
                System.out.println(
                        processId + " is already requesting CS."
                );
            } else {
                criticalSectionRequestors.add(processId);
            }
        }

        for (int requestor : criticalSectionRequestors) {
            all[requestor].start();
        }

        sc.close();
    }
}