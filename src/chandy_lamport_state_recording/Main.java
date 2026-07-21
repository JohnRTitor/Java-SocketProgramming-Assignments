package chandy_lamport_state_recording;


import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

class Message {
    // Process that sent this message
    Process sender;

    // True if this is a snapshot marker
    boolean isMarker;

    // Payload of a normal message
    int value;

    // Constructor for a normal message
    Message(Process sender, int value) {
        this.sender = sender;
        this.value = value;
        this.isMarker = false;
    }

    // Constructor for a marker message
    Message(Process sender) {
        this.sender = sender;
        this.isMarker = true;
    }
}


class Process extends Thread {
    // Process ID and current local state
    private final int pid;
    private int state;

    // Incoming and outgoing neighbors
    Set<Process> parents = new HashSet<>();
    Set<Process> children = new HashSet<>();

    // Protects shared state from concurrent modification
    private final ReentrantLock lock = new ReentrantLock();

    // Controls whether the thread keeps running
    private volatile boolean running = true;

    // Queue for incoming messages
    BlockingQueue<Message> inbox = new LinkedBlockingQueue<>();

    // Indicates whether this process has already recorded its state
    private boolean hasRecorded = false;

    // Keeps track of which incoming channels have received a marker
    HashSet<Process> markers = new HashSet<>();

    // Stores messages received on each incoming channel during the snapshot
    Map<Process, List<Integer>> channels = new HashMap<>();


    Process(int processId, int state) {
        this.pid = processId;
        this.state = state;
    }

    void addParent(Process parent) {
        parents.add(parent);
    }

    void addChild(Process child) {
        children.add(child);
    }


    // Starts the snapshot at this process
    void recordSnapshot() {
        try {
            lock.lock();

            // Ignore if snapshot has already been recorded
            if (hasRecorded) return;

            // Record local state exactly once
            hasRecorded = true;
            System.out.println("[RECORD] P#" + pid + " recorded local state = " + state);

            // Create an empty recorded state for every incoming channel
            // Messages arriving before the marker will be stored here.
            for (Process p : parents)
                channels.put(p, new ArrayList<>());

            // Inform all neighbours that snapshot recording has started by sending a marker
            for (Process p : children)
                p.inbox.offer(new Message(this));

        } finally {
            lock.unlock();
        }
    }


    // Handles an incoming marker message
    void handleMarker(Process sender) {
        try {
            lock.lock();

            // If this is the first marker, record local state
            if (!hasRecorded) recordSnapshot();

            // Mark that this incoming channel has received its marker
            markers.add(sender);

            System.out.println("P#" + pid + " received marker from P#" + sender.pid);

            // Snapshot is complete when markers have arrived on all incoming channels
            if (markers.size() == parents.size()) {
                System.out.println("P#" + pid + " snapshot complete");
                printSnapshot();
            }

        } finally {
            lock.unlock();
        }
    }

    // Randomly sends a normal message to simulate normal distributed traffic
    void sendRandomMessage() {
        Random r = new Random();

        if (children.isEmpty())
            return;

        if (r.nextInt(5) != 0)
            return;

        List<Process> childList = new ArrayList<>(children);
        Process p = childList.get(r.nextInt(childList.size()));

        try {
            lock.lock();

            // Simulate a state change before sending
            state++;
            p.inbox.offer(new Message(this, state));
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void run() {
        // Main execution loop.
        // Each process repeatedly:
        // 1. Sends normal messages.
        // 2. Receives incoming messages.
        // 3. Processes marker or application messages.

        while (running) {
            sendRandomMessage();

            try {
                // Wait for a message (up to 200 ms) to simulate message arrival
                Message m = inbox.poll(200, TimeUnit.MILLISECONDS);

                // No message received
                if (m == null) continue;

                // Handle marker messages
                if (m.isMarker) {
                    handleMarker(m.sender);
                } else { // handle normal messages
                    lock.lock();
                    try {
                        // Normal message: update local state
                        state += m.value;

                        // Record this message only if:
                        // 1. Local state has already been recorded.
                        // 2. Marker has NOT yet arrived on this channel.
                        if (hasRecorded && !markers.contains(m.sender) && channels.containsKey(m.sender)) {
                            channels.get(m.sender).add(m.value);
                        }
                    } finally {
                        lock.unlock();
                    }
                }
            } catch (InterruptedException e) {
                interrupt();
                System.err.println("Thread#" + this.pid + " interrupted.");
                break;
            }
        }
    }

    void shutdown() {
        running = false;
    }

    void printSnapshot() {
        System.out.println("\n========== Snapshot of P#" + pid + " ==========");

        lock.lock();
        try {
            System.out.println("Local State: " + state);

            System.out.println("Channel States:");

            if (parents.isEmpty()) {
                System.out.println("No incoming channels.");
            } else {
                for (Process parent : parents) {
                    List<Integer> messages = channels.get(parent);

                    System.out.print("P#" + parent.pid + " -> P#" + pid + " : ");

                    if (messages == null || messages.isEmpty()) {
                        System.out.println("[]");
                    } else {
                        System.out.println(messages);
                    }
                }
            }

        } finally {
            lock.unlock();
        }
        System.out.println("====================================\n");
    }
}

class Main {
    static Process[] processes;

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);

        System.out.print("How many processes? ");
        int n = sc.nextInt();

        if (n < 2) {
            System.out.println("At least 2 processes required.");
            return;
        }

        processes = new Process[n];

        System.out.println("\nEnter initial local state for each process:");
        for (int i = 0; i < n; i++) {
            System.out.print(i + ": ");
            processes[i] = new Process(i, sc.nextInt());
        }

        System.out.println("Enter outgoing channels for each process:");
        System.out.println("Terminate each list with -1.");

        for (int i = 0; i < n; i++) {
            System.out.print(i + ": ");
            while (true) {
                int target = sc.nextInt();
                if (target == -1) break;

                if (target < 0 || target >= n) {
                    System.out.println("Invalid process id: " + target);
                } else if (target == i) {
                    System.out.println("A process cannot send to itself.");
                } else {
                    processes[i].addChild(processes[target]);
                    processes[target].addParent(processes[i]);
                }

            }
        }

        System.out.print("Who is the initiator? ");
        int initiatorId = sc.nextInt();

        if (initiatorId < 0 || initiatorId >= n) {
            System.out.println("[ERROR] Invalid initiator id");
            return;
        }

        System.out.println("\n===========================");
        System.out.println("Starting Chandy-Lamport State Recording");
        System.out.println("\n===========================");

        for (Process p : processes) {
            p.start();
        }

        // Let the system generate a few messages before snapshot starts.
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("[ERROR] Interrupted in main thread");
        }

        processes[initiatorId].recordSnapshot();

        // Give markers and messages time to propagate.
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("[ERROR] Interrupted in main thread");

        }

        // Stop all process threads.
        for (Process p : processes) {
            p.shutdown();
        }

        for (Process p : processes) {
            try {
                p.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("[ERROR] Interrupted in main thread");
            }
        }

        System.out.println("Program finished.");
    }
}
