package diffusion_computation;

import java.util.HashSet;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

class Node extends Thread {
    int nodeId;

    // parents are nodes that can send a REQUEST to this node
    // children are nodes to which this node can forward the computation.
    private final Set<Node> parents = new HashSet<>();
    private final Set<Node> children = new HashSet<>();

    // The first parent whose REQUEST is accepted becomes the active parent.
    // Later requests from other parents are non-engaging and do not become part
    // of the reduction tree.
    private Node activeParent = null;

    // Local value contributed by this node.
    // During the return phase, child values are accumulated into this field.
    private int value;

    // Protects activeParent and value because multiple parent/child threads may
    // try to send requests or return values concurrently.
    private final ReentrantLock lock = new ReentrantLock();

    Node(int nodeId, int value) {
        this.nodeId = nodeId;
        this.value = value;
    }

    void addParent(Node parent) {
        parents.add(parent);
    }

    void addChild(Node child) {
        children.add(child);
    }

    void setAsRoot() {
        // The root starts the diffusing computation, so it has no active parent
        // and never returns a value upward.
        parents.clear();
        activeParent = null;
    }

    boolean receiveRequest(Node parent) {

        lock.lock();
        try {

            // Engagement rule:
            // the first REQUEST received by a node is accepted.
            // That sender becomes the active parent in the temporary computation tree.
            if (activeParent == null) {

                activeParent = parent;

                System.out.println("[ACCEPT] Node "
                    + nodeId + " accepts request from Node " + parent.nodeId);
                return true;
            }

            // If this node has already accepted another parent, this REQUEST is
            // non-engaging.
            System.out.println("[NON-ENGAGING] Node "
                + nodeId + " discards request from Node " + parent.nodeId);

            return false;

        } finally {
            lock.unlock();
        }
    }

    @Override
    public void run() {

        System.out.println("\n[START] Node " + nodeId +
            " started with value " + value);

        // Children that accepted this node's REQUEST become engaging children.
        // Only engaging children are started and waited for, because only they
        // belong to the temporary spanning tree of this computation.
        Set<Node> engagingChildren = new HashSet<>();

        // Diffusion phase:
        // this node sends REQUEST messages to all of its graph children.
        // A child accepts only the first REQUEST it receives.
        children.forEach(child -> {
            System.out.println("[FORWARD] Node " + nodeId
                + " -> Node " + child.nodeId + " : REQUEST");

            if (child.receiveRequest(this)) {
                engagingChildren.add(child);
            }
        });

        // Start only the accepted children so the computation continues outward
        // along the engaging edges.
        engagingChildren.forEach(Thread::start);

        // Convergecast/reduction waiting phase:
        // this node waits until every engaging child has completed its own
        // subtree computation and returned its accumulated value.
        engagingChildren.forEach(child -> {
            try {
                child.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();

                System.err.println("Node " + nodeId +
                    " interrupted while waiting.");
            }
        });

        if (activeParent != null) {

            // Return phase:
            // after all engaging children have finished, this node sends its
            // accumulated value to its active parent.
            System.out.println("[RETURN] Node " + nodeId
                + " sends value " + value + " to Node " + activeParent.nodeId);

            activeParent.lock.lock();
            try {

                // Parent accumulates this node's reduced subtree value.
                activeParent.value += value;

                System.out.println("[UPDATE] Node " + activeParent.nodeId
                    + " new value = " + activeParent.value);

            } finally {
                activeParent.lock.unlock();
            }

            // Parents whose requests were not accepted are inactive parents.
            // They receive no contribution from this node because this node
            // already belongs to another branch of the temporary computation tree.
            parents.stream().filter(parent -> parent != activeParent).forEach(inactiveParent -> {
                inactiveParent.lock.lock();
                try {

                    // We intentionally send 0 to the inactive parent
                    // so it will not contribute to the final value
                    inactiveParent.value += 0;

                } finally {
                    inactiveParent.lock.unlock();
                }
            });
        } else {

            // The root has no active parent. When all engaging descendants have
            // returned, its value contains the final reduced result.
            System.out.println(
                "\n[ROOT] Node " + nodeId
                    + " final reduced value = " + value);
        }
    }
}

class Main {

    static Node[] graph;
    static Node root;

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);

        // Ask how many nodes will participate in the diffusing computation.
        System.out.print("How many nodes? ");
        int n = sc.nextInt();

        if (n < 2) {
            System.out.println("At least 2 nodes required.");
            return;
        }

        graph = new Node[n];

        System.out.println("\nEnter value for each node:");

        // Read the initial local value for every node and create Node objects.
        // These values will later be accumulated during the return phase.
        for (int i = 0; i < n; i++) {

            System.out.print(i + ": ");
            int value = sc.nextInt();

            graph[i] = new Node(i, value);
        }

        // Used to ensure the input graph has exactly one root.
        // The root is the node that starts the diffusing computation.
        int rootCount = 0;

        System.out.println("\nEnter parent IDs for each node.");
        System.out.println("Terminate each list with -1.");
        System.out.println(
            "If the first input is -1, the node has no parent (root).");

        // Read parent relationships for every node.
        // For each parent-child relationship:
        //   - the current node stores the parent
        //   - the parent stores the current node as a child
        for (int i = 0; i < n; i++) {

            System.out.print(i + ": ");

            boolean hasParent = false;

            while (true) {

                int parentId = sc.nextInt();

                // -1 marks the end of the parent list for this node.
                if (parentId == -1) break;

                // Reject parent IDs outside the valid node range.
                if (parentId < 0 || parentId >= n) {

                    System.out.println("Invalid parent id: " + parentId);
                    continue;
                }

                // A node cannot be its own parent.
                if (parentId == i) {

                    System.out.println("Node cannot be its own parent.");
                    continue;
                }

                hasParent = true;

                // Build the directed graph connection.
                // The current node records its parent, while the parent records
                // this node as one of its children.
                graph[i].addParent(graph[parentId]);
                graph[parentId].addChild(graph[i]);
            }

            // A node with no parent is treated as a root candidate.
            if (!hasParent) {

                rootCount++;

                // The computation must have only one root.
                if (rootCount > 1) {

                    System.out.println("Error: Multiple roots detected.");
                    return;
                }

                // Store the root and mark it as the computation initiator.
                root = graph[i];
                graph[i].setAsRoot();
            }
        }

        // If no root was entered, the diffusing computation cannot start.
        if (rootCount == 0) {

            System.out.println("Error: No root node specified.");
            return;
        }

        System.out.println("\n==============================");
        System.out.println("Starting Diffusing Computation");
        System.out.println("Root = Node " + root.nodeId);
        System.out.println("==============================");

        // Start the root thread.
        // The root begins the diffusion phase by sending requests to its children.
        root.start();

        try {
            // Wait until the root completes.
            // Completion of the root means all engaging descendants have finished
            // and the final reduced value has been computed.
            root.join();
        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();
            System.err.println("Main thread interrupted.");
        }

    }
}
