package richart_agrawala_mutual_exclusion;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.locks.ReentrantLock;

class Node extends Thread {
    private final int nodeId;

    // Timestamp taken from system time when this node wants CS
    // nodeId breaks ties if two requests land in the same nanosecond
    private long requestTimestamp = -1;

    // Protects clock, state, goAheadCount, and deferredList from concurrent access
    // As multiple threads may simultaneously call receiveRequest() and receiveGoAhead()
    // simultaneously, and these methods modify state variables. Without locking:
    // clock skew, duplicate GO_AHEAD sends, and list corruption can all occur
    private final ReentrantLock lock = new ReentrantLock();

    // Global lock representing the actual shared resource / critical section
    // If this algorithm works correctly, only one thread should ever
    // acquire this lock at a time
    static final ReentrantLock csLock = new ReentrantLock();

    // State machine for a requesting node:
    //   IDLE        – not interested in CS
    //   REQUESTING  – broadcast sent, waiting for N-1 GO_AHEAD replies
    //   IN_CS       – currently executing critical section
    private enum State {IDLE, REQUESTING, IN_CS}

    private State state = State.IDLE;

    // Count of GO_AHEAD replies received for the current request
    // Node enters CS once this reaches N-1
    private int goAheadCount = 0;

    // Nodes whose GO_AHEAD we have deferred because we had a higher-priority
    // request. We send GO_AHEAD to all of them after exiting CS
    private final List<Node> deferredList = new ArrayList<>();

    Node(int nodeId) {
        this.nodeId = nodeId;
    }

    // Called by this node's own thread to request CS entry.
    // Broadcasts a REQUEST to every other node for permission to enter CS
    private void requestCS() {
        long ts;

        lock.lock();
        try {
            ts = System.nanoTime(); // Get system time in nanoseconds
            requestTimestamp = ts;
            state = State.REQUESTING;
            goAheadCount = 0;
        } finally {
            lock.unlock();
        }

        System.out.println("Node " + nodeId + " broadcasting REQUEST (ts=" + ts + ")");

        // Send REQUEST to all other nodes (outside lock to avoid deadlock as
        // receiveRequest() handles lock internally)
        for (Node other : Main.nodes) {
            if (other.nodeId != this.nodeId) {
                other.receiveRequest(this, ts);
            }
        }
    }

    // Called by another node (on its thread) when it sends this node a REQUEST

    // GO_AHEAD should be immediately sent unless:
    //  - we are already IN_CS, or
    //  - we are also REQUESTING, and our request has higher priority
    //    (lower timestamp; nodeId breaks ties, if timestamps are equal).
    // If any of these conditions are satisfied, we defer the reply
    // until after we exit CS
    void receiveRequest(Node requester, long requesterTimestamp) {
        boolean sendGoAheadNow;

        lock.lock();
        try {
            boolean higherPriorityRequestExists =
                    state == State.REQUESTING &&
                            (requestTimestamp < requesterTimestamp ||
                                    (requestTimestamp == requesterTimestamp && nodeId < requester.nodeId));

            if (state == State.IN_CS || higherPriorityRequestExists) {
                // Defer: we will send GO_AHEAD after we leave CS
                deferredList.add(requester);
                System.out.println("Node " + nodeId +
                        " will send GO_AHEAD to Node " + requester.nodeId + " later");
                sendGoAheadNow = false;
            } else {
                sendGoAheadNow = true;
            }
        } finally {
            lock.unlock();
        }

        // Send REQUEST to all other nodes (outside lock to avoid deadlock as
        // recieveGoAhead() handles lock internally)
        if (sendGoAheadNow) {
            System.out.println("Node " + nodeId +
                    " sending GO_AHEAD to Node " + requester.nodeId);
            requester.receiveGoAhead(this);
        }
    }

    // Called by another node (on its thread) when it grants this node permission
    void receiveGoAhead(Node sender) {
        lock.lock();
        try {
            goAheadCount++;
            System.out.println("Node " + nodeId +
                    " received GO_AHEAD from Node " + sender.nodeId +
                    " (" + goAheadCount + "/" + (Main.nodes.length - 1) + ")");
        } finally {
            lock.unlock();
        }
        // As run() continuously checks goAheadCount, no explicit signaling is needed
        // it will call enterCS() there when goAheadCount reaches N-1
    }

    private void enterCS() {
        // This lock simulates actual shared resource access
        // Only one node should EVER successfully acquire it
        // If tryLock() fails, the algorithm has violated mutual exclusion
        if (!csLock.tryLock()) {
            System.out.println(
                    "Mutual exclusion VIOLATED while trying to acquire lock for node " + nodeId + "!");
            return;
        }

        try {
            System.out.println(">>> Node " + nodeId + " ENTERING CS");

            // Simulate work inside CS
            Thread.sleep(1000);

            System.out.println("<<< Node " + nodeId + " EXITING CS");

            printSystemState(Main.nodes, "STATE AFTER NODE " + nodeId + " COMPLETED CS");
        } catch (InterruptedException e) {

            // Clear interrupted flag, allowing this thread to run again
            Thread.currentThread().interrupt();
            System.err.println("Error: Node " + nodeId + " interrupted during CS.");
        } finally {

            // Always release the CS lock after execution
            // even if an exception occurs
            csLock.unlock();
        }
    }

    // After leaving CS, transition back to IDLE and send go ahead to every node
    // in the deferred list
    private void releaseCS() {
        List<Node> toNotify;

        lock.lock();
        try {
            state = State.IDLE;
            requestTimestamp = -1;
            // Copy the list of deferred nodes to avoid concurrent modification issues
            toNotify = new ArrayList<>(deferredList);
            deferredList.clear();
        } finally {
            lock.unlock();
        }

        // Send GO_AHEAD to all deferred nodes, outside lock to avoid deadlock
        for (Node deferred : toNotify) {
            System.out.println("Node " + nodeId +
                    " sending deferred GO_AHEAD to Node " + deferred.nodeId);
            deferred.receiveGoAhead(this);
        }
    }

    // Each started node immediately requests CS, waits until it has GO_AHEAD
    // from all nodes, enters it, then releases CS
    @Override
    public void run() {
        System.out.println("Node " + nodeId + " requesting CS");

        requestCS();

        int neededGoAheadCount = Main.nodes.length - 1;

        // Wait until all replies arrive
        while (true) {
            lock.lock();
            try {
                if (goAheadCount >= neededGoAheadCount) {
                    state = State.IN_CS;
                    break;
                }
            } finally {
                lock.unlock();
            }
            // while waiting, yield/free CPU time and other resources to other threads
            Thread.yield();
        }

        enterCS();
        releaseCS();
    }

    static void printNodeStates(Node[] nodes) {
        System.out.println("\nNode States:");
        for (Node node : nodes) {
            node.lock.lock();
            try {
                System.out.println("  Node " + node.nodeId +
                        " | state=" + node.state +
                        " | goAheadCount=" + node.goAheadCount +
                        " | deferred=" + deferredIdsToString(node.deferredList));
            } finally {
                node.lock.unlock();
            }
        }
    }

    private static String deferredIdsToString(List<Node> list) {
        if (list.isEmpty()) return "[]";
        List<Integer> ids = new ArrayList<>();
        for (Node n : list) ids.add(n.nodeId);
        return ids.toString();
    }

    static void printSystemState(Node[] nodes, String stage) {
        System.out.println("\n=================================");
        System.out.println(stage);
        System.out.println("=================================");
        printNodeStates(nodes);
        System.out.println("=================================\n");
    }
}

class Main {
    static Node[] nodes;

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);

        System.out.print("How many nodes? ");
        int n = sc.nextInt();

        if (n < 2) {
            System.out.println("At least 2 nodes required.");
            return;
        }

        nodes = new Node[n];
        for (int i = 0; i < n; i++) {
            nodes[i] = new Node(i);
        }

        List<Integer> requestors = new ArrayList<>();

        System.out.println("Which nodes should request CS? Enter ids one by one, -1 to stop:");
        while (true) {
            int id = sc.nextInt();
            if (id == -1) break;

            if (id < 0 || id >= n) {
                System.out.println("Invalid node id.");
            } else if (requestors.contains(id)) {
                System.out.println("Node " + id + " already added.");
            } else {
                requestors.add(id);
            }
        }

        sc.close();

        if (requestors.isEmpty()) {
            System.out.println("No requestors. Exiting.");
            return;
        }

        Node.printSystemState(nodes, "INITIAL STATE");

        // Start all requesting nodes concurrently
        for (int id : requestors) {
            nodes[id].start();
        }

        // Wait for all CS requester threads to finish
        for (int id : requestors) {
            try {
                nodes[id].join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("Error: Node " + id + " interrupted during join.");
            }
        }

        System.out.println("All nodes have completed CS.");
    }
}