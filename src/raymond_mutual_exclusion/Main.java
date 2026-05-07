package raymond_mutual_exclusion;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Scanner;
import java.util.concurrent.ConcurrentLinkedQueue;

class Node extends Thread {
    private int nodeId;
    private Node parent = null;
    private Queue<Node> requestQueue = new ConcurrentLinkedQueue<>();

    private boolean hasToken = false;

    Node(int nodeId) {
        this.nodeId = nodeId;
    }

    void setParent(Node parent) {
        // if null is provided as parent, then this node must be the root
        // since the root can only hold token, set hasToken to true
        if (parent == null) {
            hasToken = true;
            this.parent = null;
        } else {
            this.parent = parent;
        }
    }


    void requestCS() {
        // When requesting CS, first add itself to its own requestQueue
        if (!requestQueue.contains(this)) {
            requestQueue.add(this);
        }

        // Parent also receives the request
        if (parent != null) {
            parent.receiveRequest(this);
        }

        processQueue();
    }

    void receiveRequest(Node requester) {
        if (!requestQueue.contains(requester)) {
            requestQueue.add(requester);
        }

        // only forward the request to parent if this is not the root
        if (parent != null) {
            parent.receiveRequest(this);
        }

        processQueue();
    }


    void processQueue() {
        if (!hasToken) return;

        if (!requestQueue.isEmpty()) {
            // The next node to receive the token
            Node nextInLine = requestQueue.poll();
            
            if (nextInLine == null) {
                return;
            }

            if (nextInLine == this) {
                enterCS();
            } else {
                // Relinquish the token and pass it to nextInLine
                hasToken = false;
                nextInLine.hasToken = true;

                // reverse the edge, so nextInLine becomes the parent
                // and nextInLine becomes root
                this.parent = nextInLine;
                nextInLine.parent = null;

                // Transfer remaining queue entries to nextInLine
                while (!requestQueue.isEmpty()) {
                    Node pendingRequest = requestQueue.poll();

                    if (!nextInLine.requestQueue.contains(pendingRequest)) {
                        nextInLine.requestQueue.add(pendingRequest);
                    }
                }


                System.out.println("Token passed from " + nodeId + " to " + nextInLine.nodeId);
                nextInLine.processQueue();
            }
        }
    }

    void enterCS() {

        System.out.println("Node " + getNodeId() + " ENTERING CS");
        try {
            sleep(5000);
        } catch (InterruptedException e) {
            System.err.println("Something went wrong: " + e.getMessage());
            System.exit(1);
        }

        System.out.println("Node" + getNodeId() + " HAS EXITED CS");

        processQueue();
    }

    @Override
    public void run() {
        requestCS();
    }

    int getNodeId() {
        return nodeId;
    }
}

class Main {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        System.out.print("How many threads? ");
        int nThreads = sc.nextInt();
        if (nThreads < 2) {
            System.out.println("At least 2 threads is required.");
            System.exit(0);
        }

        Node[] tree = new Node[nThreads];
        for (int i = 0; i < nThreads; i++) {
            tree[i] = new Node(i);
        }

        System.out.println("Enter parent id for each node: (type -1 for root node)");
        for (int i = 0; i < nThreads; i++) {
            System.out.print(i + ": ");
            int parentId = sc.nextInt();
            if (parentId == -1) { // if -1, then it's the root
                tree[i].setParent(null);
            } else if (parentId > nThreads - 1 || parentId < 0) {
                System.out.println("Invalid threadId for parent: " + parentId);
                i--;
            } else {
                tree[i].setParent(tree[parentId]);
            }
        }

        List<Integer> criticalSectionRequestors = new ArrayList<>();

        System.out.println("Which threads should request CS? (0 - " + (nThreads - 1) + ") -1 to stop input");
        while (true) {
            int threadId = sc.nextInt();
            if (threadId == -1 && criticalSectionRequestors.isEmpty()) {
                System.out.println("No thread is requesting CS. Terminating...");
                System.exit(0);
            } else if (threadId == -1) {
                System.out.println("Continuing execution...");
                break;
            } else if (threadId > nThreads - 1 || threadId < 0) {
                System.out.println("Invalid threadId: " + threadId);
            } else if (criticalSectionRequestors.contains(threadId)) {
                System.out.println(threadId + " already is requesting CS.");
            } else {
                criticalSectionRequestors.add(threadId);
            }
        }

        for (int requestor : criticalSectionRequestors) {
            tree[requestor].start();
        }

        sc.close();
    }
}
