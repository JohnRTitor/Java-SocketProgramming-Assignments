package raymond_mutual_exclusion;

import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

class Node extends Thread {
    private final int nodeId;
    // parent of the node in the inverted tree structure, initially null, but
    // should be set, as only root of the tree has no parent
    private Node parent = null;

    // Protects queue + token state from concurrent access
    // As multiple threads may simultaneously call receiveRequest(),
    // receiveToken(), processQueue(), and these methods modify
    // variables such as hasToken, parent, requestQueue, a lock is required
    // Without locking: queue corruption may happen, multiple token transfers
    // may occur, duplicate REQUEST forwarding may happen
    private final ReentrantLock lock = new ReentrantLock();

    // Global lock representing the actual shared resource / critical section
    // If this algorithm works correctly, only one thread should ever
    // acquire this lock at a time
    static final ReentrantLock csLock = new ReentrantLock();

    // Lets other nodes or itself know if it's currently in CS
    // This is required to prevent concurrent actions such as token
    // passing while being in CS
    private boolean inCS = false;

    // Prevents duplicate REQUEST forwarding
    private boolean requestSentToParent = false;

    // FIFO queue of pending token requests, LinkedList is actually a Queue
    private final Queue<Node> requestQueue = new LinkedList<>();

    Node(int nodeId) {
        this.nodeId = nodeId;
    }

    void setParent(Node parent) {
        this.parent = parent;
    }

    void setAsRoot() {
        this.parent = null;
    }

    void receiveRequest(Node requester) {
        Node current = this; // node whose queue we are adding to
        Node toEnqueue = requester; // what goes into that queue

        while (current != null) {
            Node nextNode = null; // next hop in the chain, null means we reached root

            // By locking, we safely add to queue, and update state variables
            current.lock.lock();
            try {
                current.requestQueue.add(toEnqueue);

                // If current node has no token and hasn't already forwarded a
                // request upward, forward now
                if (!current.hasToken() && !current.requestSentToParent) {
                    current.requestSentToParent = true;
                    nextNode = current.parent; // walk one step up
                }
            } finally {
                // Finally unlock, so other threads can modify state variables
                current.lock.unlock();
            }

            if (nextNode != null) {
                System.out.println("Node " + current.nodeId +
                        " forwarding REQUEST to " + nextNode.nodeId);
            }

            // processQueue for the node we just enqueued into. If this node already
            // holds the token, it can handle the request immediately
            current.processQueue();

            // Advance loop: next iteration acts as nextNode.receiveRequest(current)
            toEnqueue = current;
            current = nextNode;
        }
    }

    void receiveToken(Node from) {
        // By locking, we safely update state variables and token ownership
        lock.lock();
        try {
            setAsRoot();
            requestSentToParent = false;
        } finally {
            // Finally unlock, so other threads can modify state variables
            lock.unlock();
        }

        System.out.println("Node " + nodeId + " received TOKEN from " + from.nodeId);
        processQueue();
    }

    void processQueue() {
        // Keep processing requests in the queue until:
        // - queue becomes empty
        // - token is passed away
        // - node enters CS
        while (true) {
            Node next = null;
            Node forwardRequestTo = null;

            // By locking, we safely insert to the queue and modify state variables
            lock.lock();
            try {
                if (inCS || !hasToken() || requestQueue.isEmpty()) {
                    return;
                }

                // FIFO dequeue, it becomes next node to receive token
                next = requestQueue.poll();

                // If another node requested token, pass token ownership to that node
                if (next != this) {
                    // Parent now points to new token holder
                    // meaning THIS node no longer owns token
                    parent = next;

                    if (!requestQueue.isEmpty()) {

                        // Prevent duplicate forwarding
                        requestSentToParent = true;

                        // Remaining requests must now be routed through
                        // the new token holder
                        forwardRequestTo = next;
                    }
                } else {

                    // If next requester is itself,
                    // this node may enter critical section
                    inCS = true;
                }

            } finally {
                // Finally unlock, so other threads can modify state variables
                lock.unlock();
            }

            if (inCS) {
                enterCS();

                // Reset CS state after CS execution is completed
                // By locking we safely update this variable
                lock.lock();
                try {
                    inCS = false;
                } finally {
                    lock.unlock();
                }
            } else {

                // Forward request outside lock to avoid deadlock, recieveRequest() can
                // handle locking on its own
                if (forwardRequestTo != null) {
                    System.out.println("Node " + nodeId + " forwarding pending REQUEST to " + forwardRequestTo.nodeId);

                    forwardRequestTo.receiveRequest(this);
                }

                // Pass token outside lock for the same reason, recieveToken() can
                // handle locking on its own
                System.out.println("Node " + nodeId + " passing TOKEN to " + next.nodeId);

                next.receiveToken(this);

                // Stop processing queue, as we forwarded the token
                return;
            }
        }
    }

    private void enterCS() {
        // This lock simulates actual shared resource access
        // Only one node should EVER successfully acquire it
        // If tryLock() fails, Raymond's algorithm violated mutual exclusion
        if (!csLock.tryLock()) {
            System.out.println("Mutual exclusion VIOLATED while trying to acquire lock for node " + nodeId + "!");
            return;
        }

        try {
            System.out.println(">>> Node " + nodeId + " ENTERING CS");

            // Simulate work inside CS
            Thread.sleep(1000);

            System.out.println("<<< Node " + nodeId + " EXITING CS");

            printSystemState(Main.tree,
                    "STATE AFTER NODE " + nodeId + " COMPLETED CS");
        } catch (InterruptedException e) {

            // Clear interrupted flag, allowing this thread to run again
            Thread.currentThread().interrupt();
            System.err.println("Error: Node " + nodeId + " interrupted during execution.");
        } finally {

            // Always release the CS lock after execution
            // even if an exception occurs
            csLock.unlock();
        }
    }

    // A thread starts executing from run() function
    // It is assumed that only CS requester threads will be started,
    // and will immediately request CS, rest will stay idle
    @Override
    public void run() {

        System.out.println("Node " + nodeId + " requesting CS");

        receiveRequest(this);
    }


    static void printTreeState(Node[] tree) {
        System.out.println("\nParent Structure:");

        for (Node node : tree) {
            int parentId = node.getParentId();

            System.out.println(
                    "Node " + node.nodeId + " : Parent -> " + (parentId == -1 ? "NULL" : parentId)
            );
        }
    }

    static void printTokenHolder(Node[] tree) {
        System.out.print("\nCurrent Token Holder: ");

        for (Node node : tree) {
            if (node.hasToken()) {
                System.out.println(node.nodeId);
                return;
            }
        }

        System.out.println("None");
    }


    static void printRequestQueues(Node[] tree) {
        System.out.println("\nRequest Queues:");

        for (Node node : tree) {
            System.out.println(
                    "Node " + node.nodeId + " queue -> " + node.getQueueString()
            );
        }
    }

    static void printSystemState(Node[] tree, String stage) {
        System.out.println("\n=================================");
        System.out.println(stage);
        System.out.println("=================================");

        printTreeState(tree);
        printTokenHolder(tree);
        printRequestQueues(tree);

        System.out.println("=================================\n");
    }

    // In Raymond's algorithm, token holder is represented as root of tree
    // Root node has no parent
    boolean hasToken() {
        return parent == null;
    }

    int getParentId() {
        return parent == null ? -1 : parent.nodeId;
    }

    String getQueueString() {
        lock.lock();

        try {
            if (requestQueue.isEmpty()) {
                return "[]";
            }

            // Convert the queue of node objects into array of nodeIds for printing
            List<Integer> ids = new ArrayList<>();

            for (Node node : requestQueue) {
                ids.add(node.nodeId);
            }

            return ids.toString();

        } finally {
            lock.unlock();
        }
    }
}

class Main {
    static Node[] tree;

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);

        System.out.print("How many nodes? ");
        int n = sc.nextInt();

        if (n < 2) {
            System.out.println("At least 2 nodes required.");
            return;
        }

        tree = new Node[n];

        for (int i = 0; i < n; i++) {
            tree[i] = new Node(i);
        }

        // Don't allow multiple roots or no root, this must be equal to 1 after all input
        int rootCount = 0;

        System.out.println("Enter parent id for each node (-1 for root, which holds token initially):");

        for (int i = 0; i < n; i++) {

            System.out.print(i + ": ");
            int parentId = sc.nextInt();

            if (parentId == -1) {
                rootCount++;

                if (rootCount > 1) {
                    System.out.println("Only one root allowed. Try again.");

                    i--;
                    rootCount--;
                    continue;
                }

                tree[i].setAsRoot();
            } else if (parentId < 0 || parentId >= n) {
                System.out.println("Invalid parent id. Try again.");
                i--;
            } else {
                tree[i].setParent(tree[parentId]);
            }
        }

        if (rootCount == 0) {
            System.out.println("No root defined. Exiting.");
            System.exit(0);
        }

        List<Integer> requestors = new ArrayList<>();

        System.out.println("Which nodes should request CS? Enter ids one by one, -1 to stop:");

        while (true) {
            int id = sc.nextInt();

            if (id == -1) {
                break;
            }

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

        Node.printSystemState(tree, "INITIAL STATE");

        // Start all requesting nodes concurrently
        for (int id : requestors) {
            tree[id].start();
        }

        // Wait for all CS requester threads to finish
        for (int id : requestors) {
            try {
                tree[id].join();
            } catch (InterruptedException e) {
                // Clear interrupted flag, allowing this thread to run again
                Thread.currentThread().interrupt();
                System.err.println("Error: Node " + id + " interrupted during execution.");
            }
        }

        System.out.println("All nodes have completed CS.");
    }
}