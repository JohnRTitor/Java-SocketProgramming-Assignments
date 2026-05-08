package raymond_mutual_exclusion;

import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

class Node extends Thread {
    private final int nodeId;
    private Node parent = null;

    // True only for current token holder
    private boolean hasToken = false;

    // Protects queue + token state from concurrent access
    // As multiple threads may simultaneously call receiveRequest(),
    // receiveToken(), processQueue(), and these methods modify
    // variables such as hasToken, parent, requestQueue, a lock is required
    // Without locking: queue corruption may happen, multiple token transfers
    // may occur, duplicate REQUEST forwarding may happen
    private final ReentrantLock lock = new ReentrantLock();

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
        this.hasToken = true;
    }

    void receiveRequest(Node requester) {

        Node forwardTo = null;

        // By locking, we safely insert to the queue and modify state variables
        lock.lock();
        try {
            requestQueue.add(requester);

            if (!hasToken && !requestSentToParent) {
                requestSentToParent = true;
                forwardTo = parent;
            }

        } finally {
            // Finally unlock, so other threads can modify state variables
            lock.unlock();
        }

        // Forward request outside lock to avoid deadlock, recieveRequest() can
        // handle locking on its own
        if (forwardTo != null) {
            System.out.println("Node " + nodeId + " forwarding REQUEST to " + forwardTo.nodeId);
            forwardTo.receiveRequest(this);
        }

        processQueue();
    }

    void receiveToken(Node from) {
        // By locking, we safely update state variables and token ownership
        lock.lock();
        try {
            setAsRoot();
            parent = from;
            requestSentToParent = false;
        } finally {
            // Finally unlock, so other threads can modify state variables
            lock.unlock();
        }

        System.out.println("Node " + nodeId + " received TOKEN from " + from.nodeId);
        processQueue();
    }

    void processQueue() {
        while (true) {
            Node next = null;
            Node forwardRequestTo = null;

            // By locking, we safely insert to the queue and modify state variables
            lock.lock();
            try {
                if (!hasToken || requestQueue.isEmpty()) {
                    return;
                }

                // Dequeue from the queue
                next = requestQueue.poll();

                if (next != this) {
                    hasToken = false;
                    parent = next;

                    if (!requestQueue.isEmpty()) {
                        requestSentToParent = true;
                        forwardRequestTo = next;
                    }
                }

            } finally {
                // Finally unlock, so other threads can modify state variables
                lock.unlock();
            }

            if (next == this) {
                enterCS();
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

                return;
            }
        }
    }

    private void enterCS() {
        System.out.println(">>> Node " + nodeId + " ENTERING CS");

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            System.err.println("Error: Node " + nodeId + " interrupted during execution.");
            System.exit(1);
        }

        System.out.println("<<< Node " + nodeId + " EXITING CS");

        printSystemState(Main.tree,
                "STATE AFTER NODE " + nodeId + " COMPLETED CS");
    }

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
        System.out.println("\nCurrent Token Holder:");

        for (Node node : tree) {
            if (node.hasToken) {
                System.out.println("Node " + node.nodeId + " holds TOKEN");
                return;
            }
        }

        System.out.println("No token holder found.");
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

        System.out.println("Enter parent id for each node (-1 for root/token holder):");

        for (int i = 0; i < n; i++) {

            System.out.print("Parent of node " + i + ": ");
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
                System.err.println("Error: Node " + id + " interrupted during execution.");
                System.exit(1);
            }
        }

        System.out.println("All nodes have completed CS.");
    }
}