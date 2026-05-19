package mitchell_merritt_algorithm;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

class Node {

    // Unique id of the node/process
    final int id;

    // Public label shared with other nodes
    // This value can be propagated through the transmit rule
    int u;

    // Private label local to the node
    // Updated only during the block rule
    int v;

    Node(int id, int u, int v) {
        this.id = id;
        this.u = u;
        this.v = v;
    }

    @Override
    public String toString() {
        return "Node " + id + " -> public(u): " + u + "  private(v): " + v;
    }
}

class Main {

    // Stores all nodes in the system
    private static final List<Node> nodes = new ArrayList<>();

    // Adjacency matrix representation of dependencies
    // adjMatrix[i][j] = 1 means Node i is waiting for Node j
    private static int[][] adjMatrix;

    // Total number of nodes currently in the system
    private static int numNodes;

    // Scanner object for user input
    private static final Scanner sc = new Scanner(System.in);

    public static void main(String[] args) {

        System.out.println("=================================");
        System.out.println("Mitchell-Merritt deadlock detection");
        System.out.println("=================================");

        // Read number of nodes from user
        numNodes = readNodeCount();

        adjMatrix = new int[numNodes][numNodes];

        initNodes();

        // Show initial node states
        printAllStates("INITIAL STATE");

        // Main menu loop
        while (true) {

            System.out.println("\n=================================");
            System.out.println("Dynamic request section");
            System.out.println("=================================");
            System.out.println("\n1. Add dependency edge");
            System.out.println("2. Add new node");
            System.out.println("3. Exit");
            System.out.print("\nEnter choice: ");

            int choice = sc.nextInt();

            // Exit program
            if (choice == 3) break;

            // Perform action based on user choice
            switch (choice) {
                case 1 -> addNewEdge();
                case 2 -> addNewNode();
                default -> System.out.println("\nInvalid choice.");
            }
        }

        // Print final state before ending program
        printAllStates("FINAL STATE");
        printMatrix();
    }

    private static int readNodeCount() {

        System.out.print("\nHow many initial nodes? ");
        int n = sc.nextInt();

        // Prevent invalid node count
        if (n < 1) {
            System.out.println("Need at least 1 node. Defaulting to 1.");
            return 1;
        }

        return n;
    }

    private static void initNodes() {

        System.out.println("Enter public (u) and private (v) labels for each node: ");

        // Read initial labels for every node
        for (int i = 0; i < numNodes; i++) {

            System.out.print("Node " + i + ": ");

            int u = sc.nextInt();
            int v = sc.nextInt();

            // Create and store node
            nodes.add(new Node(i, u, v));
        }
    }

    private static void addNewEdge() {

        System.out.println("\n=================================");
        System.out.println("Add dependency edge");
        System.out.println("=================================");

        // blocked = process waiting
        // blocking = process being waited on
        System.out.print("Enter blocked and blocking process ids: ");

        int blocked = sc.nextInt();
        int blocking = sc.nextInt();

        // Validate node ids
        if (isInvalidId(blocked) || isInvalidId(blocking)) {
            System.out.println("\nInvalid node id.");
            return;
        }

        // Create dependency edge
        adjMatrix[blocked][blocking] = 1;

        System.out.println("\nNew edge added: Node " + blocked + " -> Node " + blocking);

        // Show updated dependency matrix
        printMatrix();

        // Apply Mitchell-Merritt rules in order
        applyBlockRule(blocked, blocking);
        applyTransmitRule();
        detectDeadlock();
    }

    private static void addNewNode() {

        System.out.println("\n=================================");
        System.out.println("Add new node");
        System.out.println("=================================");

        // New node id is equal to current node count
        int newId = numNodes;

        System.out.println("New node id: " + newId);

        System.out.print("\nEnter public (u) and private (v): ");

        int u = sc.nextInt();
        int v = sc.nextInt();

        // Add node to list
        nodes.add(new Node(newId, u, v));

        // Increase total node count
        numNodes++;

        // Create larger adjacency matrix
        int[][] grown = new int[numNodes][numNodes];

        // Copy old matrix values into new matrix
        for (int i = 0; i < numNodes - 1; i++)
            for (int j = 0; j < numNodes - 1; j++)
                grown[i][j] = adjMatrix[i][j];

        // Replace old matrix with expanded matrix
        adjMatrix = grown;

        System.out.println("\nNode " + newId + " added successfully.");

        // Print updated states and matrix
        printAllStates("STATE AFTER ADDING NODE " + newId);
        printMatrix();
    }

    //  Block rule
    //  If blocked.u < blocking.u, set blocked.u = blocked.v
    //                                  = max(blocked.u, blocking.u) + 1
    private static void applyBlockRule(int blockedId, int blockingId) {

        System.out.println("\n=================================");
        System.out.println("Block rule execution");
        System.out.println("=================================");

        // Get both nodes involved in dependency
        Node blocked = nodes.get(blockedId);
        Node blocking = nodes.get(blockingId);

        System.out.println("\nNode " + blocked.id + " (blocked) waits for Node " + blocking.id + " (blocking)");

        System.out.println("\nBefore block rule:");
        System.out.println("  blocked  -> " + blocked);
        System.out.println("  blocking -> " + blocking);

        // Apply block rule only if blocked.u is smaller
        if (blocked.u < blocking.u) {

            // Generate next label value
            int k = Math.max(blocked.u, blocking.u) + 1;

            // Update both public and private labels
            blocked.u = k;
            blocked.v = k;

            System.out.println("\nAfter block rule:");
            System.out.println("  updated  -> " + blocked);

        } else {

            // No update needed
            System.out.println("\nNo update required (blocked.u >= blocking.u).");
        }

        printAllStates("STATE AFTER BLOCK RULE");
    }

    //  Transmit rule
    //  For every edge i -> j, if blocking.u > blocked.u,
    //  propagate blocking's public label back to blocked (opposite dir).
    //  Repeat until no change occurs.
    private static void applyTransmitRule() {

        System.out.println("\n=================================");
        System.out.println("Transmit rule execution");
        System.out.println("=================================");

        // Tracks whether any update happened in current iteration
        boolean changedThisPass;

        // Tracks whether at least one update happened overall
        boolean changedAtLeastOnce = false;

        // Continue until no more propagation occurs
        do {

            changedThisPass = false;

            // Traverse adjacency matrix
            for (int i = 0; i < nodes.size(); i++) {
                for (int j = 0; j < nodes.size(); j++) {

                    // Skip if dependency does not exist
                    if (adjMatrix[i][j] != 1) continue;

                    Node blocked = nodes.get(i);
                    Node blocking = nodes.get(j);

                    // Propagate larger public label backward
                    if (blocking.u > blocked.u) {

                        System.out.println("\nTransmit applied:");

                        System.out.println(
                            "  Node " + blocking.id +
                                " (blocking) propagates public label "
                                + blocking.u +
                                " to Node " + blocked.id +
                                " (blocked)"
                        );

                        // Update blocked node's public label
                        blocked.u = blocking.u;

                        System.out.println("  updated -> " + blocked);

                        printAllStates("STATE AFTER TRANSMIT");

                        changedThisPass = true;
                        changedAtLeastOnce = true;
                    }
                }
            }

        } while (changedThisPass);

        // No propagation occurred
        if (!changedAtLeastOnce)
            System.out.println("\nNo update required (no blocking.u > blocked.u across all edges).");
    }

    //  Detection rule                                                      //
    //  For every edge i -> j, deadlock exists if:                         //
    //      blocked.u == blocked.v  AND  blocked.u == blocking.u           //
    private static void detectDeadlock() {

        System.out.println("\n=================================");
        System.out.println("Deadlock detection");
        System.out.println("=================================");

        // Tracks whether deadlock was found
        boolean detected = false;

        System.out.println("\nAll dependency edges:");

        // Print all dependency edges
        for (int i = 0; i < nodes.size(); i++)
            for (int j = 0; j < nodes.size(); j++)
                if (adjMatrix[i][j] == 1)
                    System.out.println("  Node " + i + " -> Node " + j);

        // Check every dependency edge
        for (int i = 0; i < nodes.size(); i++) {
            for (int j = 0; j < nodes.size(); j++) {

                // Skip if edge does not exist
                if (adjMatrix[i][j] != 1) continue;

                Node blocked = nodes.get(i);
                Node blocking = nodes.get(j);

                System.out.println("\nChecking edge: Node " + i + " -> Node " + j);

                // Mitchell-Merritt deadlock condition
                if (blocked.u == blocked.v && blocked.u == blocking.u) {

                    System.out.println("  >>> DEADLOCK DETECTED on Node " + blocked.id + " <<<");

                    System.out.println(
                        "  blocked  Node " + blocked.id +
                            " -> u = v = " + blocked.u
                    );

                    System.out.println(
                        "  blocking Node " + blocking.id +
                            " -> u = " + blocking.u
                    );

                    detected = true;

                } else {

                    System.out.println("  No deadlock on this edge.");
                }
            }
        }

        // No deadlock anywhere in the graph
        if (!detected)
            System.out.println("\nNo deadlock found in system.");
    }

    private static void printAllStates(String stage) {

        System.out.println("\n=================================");
        System.out.println(stage);
        System.out.println("=================================");

        // Print every node state
        for (Node node : nodes)
            System.out.println("  " + node);

        System.out.println("=================================");
    }

    private static void printMatrix() {

        System.out.println("\n=================================");
        System.out.println("Dependency matrix");
        System.out.println("=================================");

        // Print adjacency matrix row by row
        for (int i = 0; i < numNodes; i++) {

            for (int j = 0; j < numNodes; j++)
                System.out.print(adjMatrix[i][j] + " ");

            System.out.println();
        }
    }

    private static boolean isInvalidId(int id) {

        // Valid ids must be within current node range
        return id < 0 || id >= numNodes;
    }
}
