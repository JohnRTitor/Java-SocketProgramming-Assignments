package chandy_mishra_deadlock_detection;

import java.util.HashSet;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

class Process extends Thread {
    private final int pid;

    // Set of processes this process is waiting for
    private final Set<Process> waitsFor = new HashSet<>();

    // Ensures that a process accepts only the first probe
    // (Assumes a single initiator and one execution)
    private boolean alreadyReceivedRequest = false;

    // Protects alreadyReceivedRequest variable from concurrent access
    private final ReentrantLock lock = new ReentrantLock();

    Process(int nodeId) {
        this.pid = nodeId;
    }

    // Add an edge to the wait-for graph
    void addWaitFor(Process process) {
        waitsFor.add(process);
    }

    // Receives a probe (initiator, sender, receiver)
    // If the probe reaches the initiator again, deadlock has occurred
    void receiveRequest(Process initiator, Process sender) {
        System.out.println("[PROBE] P#" + initiator.pid + ", P#"
            + sender.pid + ", P#" + this.pid
        );

        // We reached initiator, deadlock detected
        if (this == initiator) {
            Main.deadlockFound = true;
            System.out.println("[STOP] Deadlock detected. Sender P#" + sender.pid + ", Receiver P#" + this.pid);
            return;
        }

        try {
            lock.lock();

            // Ignore duplicate probes from other paths
            if (alreadyReceivedRequest) {
                System.out.println("[NON ENGAGING] Non engaging request from P#" + sender.pid);
                return;
            }

            // Mark the node as received requests
            alreadyReceivedRequest = true;
        } finally {
            lock.unlock();
        }

        // Forward the probe to every process this process is waiting for
        for (Process blockingProcess : waitsFor) {
            blockingProcess.receiveRequest(initiator, this);
        }
    }

    public void run() {
        System.out.println("\n[INITIATE] P#" + pid + " sending initial probes");

        for (Process p : waitsFor) {
            p.receiveRequest(this, this);
        }
    }
}

class Main {
    static Process[] processes;
    static boolean deadlockFound = false;

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);

        System.out.print("How many processes? ");
        int n = sc.nextInt();

        if (n < 2) {
            System.out.println("[ERROR] At least two processes required");
            return;
        }

        // Initiate all nodes
        processes = new Process[n];

        for (int i = 0; i < n; i++) {
            processes[i] = new Process(i);
        }

        // Construct the wait for graph
        System.out.println("Enter blocking processes for each processes: ");
        System.out.println("Terminate each list with -1.");

        for (int i = 0; i < n; i++) {
            System.out.print(i + ": ");

            while (true) {
                int blockingId = sc.nextInt();

                // -1 marks the end of the current process' wait for graph list
                if (blockingId == -1) break;

                if (blockingId < 0 || blockingId >= n) {
                    System.out.println("[ERROR] Process#" + i + " is out of range");
                } else if (blockingId == i) {
                    System.out.println("[ERROR] Process#" + i + " can not block itself");
                } else {
                    processes[i].addWaitFor(processes[blockingId]);
                }
            }
        }

        // Choose the blocked process that initiates detection
        System.out.print("Who is the initiator? ");
        int initiatorId = sc.nextInt();

        if (initiatorId < 0 || initiatorId >= n) {
            System.out.println("[ERROR] Invalid initiator id");
            return;
        }

        System.out.println("\n===========================");
        System.out.println("Starting Chandy-Mishra Deadlock Detection");
        System.out.println("\n===========================");

        // Start the initiator thread
        processes[initiatorId].start();

        // Wait until the detection completes
        try {
            processes[initiatorId].join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("[ERROR] Interrupted in main thread");
        }

        System.out.println("\n===========================");
        if (deadlockFound) {
            System.out.println("[RESULT] Deadlock found");
        } else {
            System.out.println("[RESULT] No deadlock detected");
        }
        System.out.println("\n===========================");
    }
}
