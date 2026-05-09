package ring_topology_mutual_exclusion;

import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

class Node extends Thread {
    private final int nodeId;

    // Each node in the ring stores its 1-hop neighbor
    private Node nextNeighbor;

    // Protects queue + token state from concurrent access.
    // Multiple threads may simultaneously call receiveRequest(), receiveToken(),
    // processQueue(), all of which modify hasToken, requestQueue, etc.
    // Without locking: queue corruption, multiple token transfers, or duplicate
    // TR forwarding may occur.
    private final ReentrantLock lock = new ReentrantLock();

    // Global lock representing the actual shared resource / critical section.
    // If this algorithm works correctly, only one thread should ever acquire it at a time.
    static final ReentrantLock csLock = new ReentrantLock();

    // Lets other nodes or itself know if it's currently in CS.
    // Required to prevent concurrent actions such as token passing while in CS.
    private boolean inCS = false;

    // Whether this node currently holds the token
    private boolean hasToken = false;

    // FIFO queue of pending CS requestor IDs.
    // In ring topology the queue travels WITH the token as part of <TKN, E, Q>.
    // Phold owns and manages the queue; when passing the token it hands Q along.
    private final Queue<Integer> requestQueue = new LinkedList<>();

    Node(int nodeId) {
        this.nodeId = nodeId;
    }

    void setNextNeighbor(Node neighbor) {
        this.nextNeighbor = neighbor;
    }

    void setAsTokenHolder() {
        this.hasToken = true;
    }

    // Called when this node receives a token request <TR, requesterId>.
    // If this node is Phold (hasToken == true) it enqueues the requesterId and
    // will serve it (even if currently in CS — processQueue fires after CS exits).
    // Otherwise the TR is forwarded one hop around the ring toward Phold.
    // Since the ring is a closed loop and Phold is always exactly one node,
    // every TR is guaranteed to terminate at Phold within at most N-1 hops.
    void receiveRequest(int requesterId) {
        Node forwardTo = null;
        boolean isPhold = false;

        lock.lock();
        try {
            if (hasToken) {
                // This is Phold — enqueue the requester.
                // processQueue() will serve it once this node exits CS (if in CS).
                System.out.println("Node " + nodeId
                        + " (Phold) enqueued TR from Node " + requesterId);
                requestQueue.add(requesterId);
                isPhold = true;
            } else {
                // Not the token holder; forward TR one hop around the ring toward Phold
                forwardTo = nextNeighbor;
            }
        } finally {
            lock.unlock();
        }

        if (forwardTo != null) {
//            System.out.println("Node " + nodeId
//                    + " forwarding <TR, " + requesterId + "> to Node " + forwardTo.nodeId);
            forwardTo.receiveRequest(requesterId);
        }

        if (isPhold) {
            processQueue();
        }
    }

    // Called when this node receives the token <TKN, targetId, Q>.
    // If this node is the intended recipient (targetId == nodeId) it becomes the new Phold.
    // Otherwise it forwards the token onward around the ring.
    void receiveToken(int targetId, Queue<Integer> q) {
        Node forwardTo = null;
        boolean isTarget = false;

        lock.lock();
        try {
            if (targetId == nodeId) {
                // This node is the intended new Phold
                hasToken = true;

                // Absorb the queue that travels with the token
                requestQueue.addAll(q);

                System.out.println("Node " + nodeId
                        + " received TOKEN <TKN, " + targetId + ", " + q + "> — now Phold");

                isTarget = true;
            } else {
                // Not the target; forward the token to the next neighbor
                forwardTo = nextNeighbor;
            }
        } finally {
            lock.unlock();
        }

        if (forwardTo != null) {
//            System.out.println("Node " + nodeId
//                    + " forwarding <TKN, " + targetId + ", " + q + "> to Node " + forwardTo.nodeId);
            forwardTo.receiveToken(targetId, q);
        }

        if (isTarget) {
            processQueue();
        }
    }

    // Phold processes the request queue after entering/exiting CS or receiving the token.
    // Keeps serving the next requester until the queue is empty, the token is passed,
    // or this node enters CS.
    void processQueue() {
        while (true) {
            int next = -1;

            lock.lock();
            try {
                if (inCS || !hasToken) {
                    return;
                }

                // No pending requests
                if (requestQueue.isEmpty()) {
                    return;
                }

                // Peek instead of poll:
                // the requester remains inside the travelling queue while the token
                // is moving toward it.
                next = requestQueue.peek();

                if (next == nodeId) {
                    // Token reached the requester at front of queue.
                    // Remove self and enter CS.
                    requestQueue.poll();

                    inCS = true;
                }
                // If next != nodeId we will pass the token; mark hasToken = false below
                // outside the lock to avoid deadlock inside receiveToken()
            } finally {
                lock.unlock();
            }

            if (inCS) {
                enterCS();

                lock.lock();
                try {
                    inCS = false;
                } finally {
                    lock.unlock();
                }

                // Continue the loop — maybe more requests are queued for others
            } else {
                // Pass token <TKN, next, remainingQueue> to the ring neighbor
                Queue<Integer> remainingQueue;

                lock.lock();
                try {
                    hasToken = false;

                    // Snapshot the FULL queue to travel with the token.
                    // Important: do NOT remove the target requester here.
                    remainingQueue = new LinkedList<>(requestQueue);

                    requestQueue.clear();
                } finally {
                    lock.unlock();
                }

//                printSystemState(Main.ring,
//                        "STATE BEFORE PASSING TOKEN TO Node " + next);

                System.out.println("Node " + nodeId
                        + " passing <TKN, " + next + ", " + remainingQueue + "> to Node "
                        + nextNeighbor.nodeId);

                nextNeighbor.receiveToken(next, remainingQueue);

                // Token has been passed; stop processing
                return;
            }
        }
    }

    private void enterCS() {
        // This lock simulates actual shared resource access.
        // Only one node should EVER successfully acquire it.
        // If tryLock() fails, the algorithm has violated mutual exclusion.
        if (!csLock.tryLock()) {
            System.out.println("Mutual exclusion VIOLATED while trying to enter CS at Node " + nodeId + "!");
            return;
        }

        try {
            System.out.println(">>> Node " + nodeId + " ENTERING CS");

            // Simulate work inside CS
            Thread.sleep(1000);

            System.out.println("<<< Node " + nodeId + " EXITING CS");

            printSystemState(Main.ring,
                    "STATE AFTER NODE " + nodeId + " COMPLETED CS");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Error: Node " + nodeId + " interrupted during CS.");
        } finally {
            csLock.unlock();
        }
    }

    // A requesting node starts here: it immediately sends a TR to its 1-hop
    // neighbor. The TR travels around the ring until it reaches Phold, which
    // enqueues it. If Phold is this node itself, the TR goes one full loop
    // before arriving back — but in practice we handle this by also letting
    // Phold enqueue its own request directly (see special case below).
    @Override
    public void run() {
        System.out.println("Node " + nodeId + " requesting CS");

        System.out.println("Node " + nodeId +
                " sending <TR, " + nodeId + "> to Node " + nextNeighbor.nodeId);

        nextNeighbor.receiveRequest(nodeId);
    }


    static void printTokenHolder(Node[] ring) {
        System.out.println("\nCurrent Token Holder:");
        for (Node node : ring) {
            if (node.hasToken) {
                System.out.println("Node " + node.nodeId + " holds TOKEN");
                return;
            }
        }
        System.out.println("Token is in transit.");
    }

    static void printRequestQueues(Node[] ring) {
        System.out.println("\nRequest Queue (at Phold):");
        for (Node node : ring) {
            if (node.hasToken) {
                System.out.println("Node " + node.nodeId + " queue -> " + node.getQueueString());
                return;
            }
        }
        System.out.println("(Token in transit — queue travelling with it)");
    }

    static void printSystemState(Node[] ring, String stage) {
        System.out.println("\n=================================");
        System.out.println(stage);
        System.out.println("=================================");
        printTokenHolder(ring);
        printRequestQueues(ring);
        System.out.println("=================================\n");
    }

    String getQueueString() {
        lock.lock();
        try {
            return requestQueue.isEmpty() ? "[]" : requestQueue.toString();
        } finally {
            lock.unlock();
        }
    }

    int getNodeId() {
        return nodeId;
    }

    Node getNextNeighbor() {
        return nextNeighbor;
    }
}

class Main {
    static Node[] ring;

    static void printRingState() {
        System.out.println("Ring Neighbor Structure:");
        for (Node node : ring) {
            System.out.println("Node " + node.getNodeId()
                    + " -> next: Node " + node.getNextNeighbor().getNodeId());
        }
        System.out.println("=================================");
    }

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);

        System.out.print("How many nodes in the ring? ");
        int n = sc.nextInt();

        if (n < 2) {
            System.out.println("At least 2 nodes required.");
            return;
        }

        ring = new Node[n];
        for (int i = 0; i < n; i++) {
            ring[i] = new Node(i);
        }

        // Wire the ring: each node's next neighbor is (i+1) % n
        for (int i = 0; i < n; i++) {
            ring[i].setNextNeighbor(ring[(i + 1) % n]);
        }

        System.out.print("Which node initially holds the token? (0 to " + (n - 1) + "): ");
        int tokenHolder = sc.nextInt();

        if (tokenHolder < 0 || tokenHolder >= n) {
            System.out.println("Invalid node id. Defaulting to Node 0.");
            tokenHolder = 0;
        }
        ring[tokenHolder].setAsTokenHolder();

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

        Node.printSystemState(ring, "INITIAL STATE");
        printRingState();

        // Start all requesting nodes concurrently
        for (int id : requestors) {
            ring[id].start();
        }

        // Wait for all CS requester threads to finish
        for (int id : requestors) {
            try {
                ring[id].join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("Error: Node " + id + " interrupted.");
            }
        }

        System.out.println("All nodes have completed CS.");
    }
}