package ho_ramamurthy_deadlock_detection;

import java.util.*;

// Represents a process in the distributed system.
// Each process belongs to exactly one site.
class Process extends Thread {
    // Unique identifier assigned globally to each process
    int processId;

    // Site where this process is located
    Site site;

    // Creates a process with a unique id and assigns it to a site
    Process(int processId, Site site) {
        this.processId = processId;
        this.site = site;
    }

    // Returns a readable name for the process
    @Override
    public String toString() {
        return "Process " + processId;
    }
}

// Represents a resource in the distributed system.
// Each resource belongs to exactly one site.
class Resource {
    // Unique identifier assigned globally to each resource
    final int resourceId;

    // Site where this resource is located
    final Site site;

    // Creates a resource with a unique id and assigns it to a site
    Resource(int resourceId, Site site) {
        this.resourceId = resourceId;
        this.site = site;
    }

    // Returns a readable name for the resource
    @Override
    public String toString() {
        return "Resource " + resourceId;
    }
}

// Represents a site in the distributed system.
// A site maintains local processes, resources, and status tables.
class Site {
    // Unique identifier for this site in the distributed system
    final int siteId;

    // Processes and resources that belong to this site
    Map<Integer, Process> processes = new HashMap<>();
    Map<Integer, Resource> resources = new HashMap<>();

    // Process Status Table:
    // key   -> process id
    // value -> resource ids for which the process is currently blocked
    Map<Integer, Set<Integer>> processStatusTable = new HashMap<>();

    // Resource Status Table:
    // key   -> resource id
    // value -> process ids currently holding that resource
    // Here a resource is expected to be allocated to at most one process
    Map<Integer, Set<Integer>> resourceStatusTable = new HashMap<>();

    // Creates a site with the given id
    Site(int siteId) {
        this.siteId = siteId;
    }

    // Adds a process to this site and initializes its PST entry
    void addProcess(Process p) {
        // Ensure that the process actually belongs to this site
        if (p.site != this)
            throw new IllegalArgumentException(
                "Process " + p.processId + " is not on site " + siteId
            );

        processes.put(p.processId, p);

        // Initialize PST row for this process
        processStatusTable.put(p.processId, new LinkedHashSet<>());
    }

    // Adds a resource to this site and initializes its RST entry
    void addResource(Resource r) {
        // Ensure that the resource actually belongs to this site
        if (r.site != this)
            throw new IllegalArgumentException(
                "Resource " + r.resourceId + " is not on site " + siteId
            );

        resources.put(r.resourceId, r);

        // Initialize RST row for this resource
        resourceStatusTable.put(r.resourceId, new LinkedHashSet<>());
    }

    // Prints the Process Status Table for this site
    void printProcessStatusTable() {
        System.out.println("\n===== PST FOR SITE " + siteId + " =====");

        // Only resources that appear in any PST row are displayed as columns
        Set<Integer> usedResources = new LinkedHashSet<>();

        // Collect all resources that at least one process is waiting for
        processStatusTable.values().forEach(usedResources::addAll);

        // If no process is waiting, there is nothing to display
        if (usedResources.isEmpty()) {
            System.out.println("No process waiting for a blocked resource.");
            return;
        }

        // Print table header using resource ids
        System.out.print("      ");
        usedResources.forEach(rId ->
            System.out.printf("R%-4d", rId)
        );
        System.out.println();

        // Print one row for every process at this site
        processes.forEach((pId, p) -> {
            System.out.printf("P%-4d ", pId);

            // Resources blocked by this process
            Set<Integer> blockedResources = processStatusTable.get(pId);

            // Print 1 if the process is blocked on the resource, otherwise 0
            usedResources.forEach(rId -> {
                int value = blockedResources.contains(rId) ? 1 : 0;

                System.out.printf("%-5d", value);
            });

            System.out.println();
        });
    }

    // Prints the Resource Status Table for this site
    void printResourceStatusTable() {
        System.out.println("\n===== RST FOR SITE " + siteId + " =====");

        // Only processes that appear in any RST row are displayed as columns
        Set<Integer> usedProcesses = new LinkedHashSet<>();

        // Collect all processes that hold at least one resource
        resourceStatusTable.values().forEach(usedProcesses::addAll);

        // If no resource is allocated, there is nothing to display
        if (usedProcesses.isEmpty()) {
            System.out.println("No allocated processes.");
            return;
        }

        // Print table header using process ids
        System.out.print("      ");
        usedProcesses.forEach(pId ->
            System.out.printf("P%-4d", pId)
        );
        System.out.println();

        // Print one row for every resource at this site
        resources.forEach((rId, r) -> {
            System.out.printf("R%-4d ", rId);

            // Processes currently holding this resource
            Set<Integer> allocatedProcesses =
                resourceStatusTable.get(rId);

            // Print 1 if the resource is allocated to the process, otherwise 0
            usedProcesses.forEach(pId -> {
                int value =
                    allocatedProcesses.contains(pId) ? 1 : 0;

                System.out.printf("%-5d", value);
            });

            System.out.println();
        });
    }
}

class Main {
    // Scanner used to read user input from the console
    private static final Scanner sc = new Scanner(System.in);

    // Stores all sites in the distributed system
    static Set<Site> sites = new LinkedHashSet<>();

    // Stores all processes and resources across every site
    static Set<Process> allProcesses = new LinkedHashSet<>();
    static Set<Resource> allResources = new LinkedHashSet<>();

    // Global Wait-For Graph:
    // key   -> process id
    // value -> process ids that the key process is waiting for
    private static Map<Integer, Set<Integer>> waitForGraph = new LinkedHashMap<>();

    // Arrays used during DFS cycle detection
    private static boolean[] visited;
    private static boolean[] inStack;

    public static void main(String[] args) {
        System.out.println("===== HO RAMAMURTHY'S DEADLOCK DETECTION =====");

        // Read number of sites, processes, and resources
        inputSiteProcessResourceCount();

        // Display total number of processes and resources created
        System.out.println("\nTotal processes: " + allProcesses.size());
        System.out.println("Total resources: " + allResources.size());

        // Read PST and RST information from the user
        inputProcessStatusTable();
        inputResourceStatusTable();

        // Print local status tables for every site
        sites.forEach(site -> {
            site.printProcessStatusTable();
            site.printResourceStatusTable();
        });

        // Convert PST and RST information into a global wait-for graph
        buildWaitForGraph();

        // Display the generated wait-for graph
        printWaitForGraph();

        // A cycle in the wait-for graph indicates a deadlock
        if (detectDeadlock()) System.out.println("Deadlock detected.");
        else System.out.println("No deadlock detected.");
    }

    // Reads the number of sites and creates processes/resources for each site
    static void inputSiteProcessResourceCount() {
        System.out.print("How many sites? ");
        int nSites = sc.nextInt();

        System.out.println("Now, for each site, enter the number of processes and resources.");

        // Create each site and assign processes/resources to it
        for (int i = 0; i < nSites; i++) {
            Site currentSite = new Site(i);
            sites.add(currentSite);

            System.out.print("Site " + i + " : ");
            int nProcesses = sc.nextInt();
            int nResources = sc.nextInt();

            // Create processes for the current site
            for (int j = 0; j < nProcesses; j++) {
                Process currentProcess = new Process(allProcesses.size(), currentSite);

                currentSite.addProcess(currentProcess);
                allProcesses.add(currentProcess);
            }

            // Create resources for the current site
            for (int j = 0; j < nResources; j++) {
                Resource currentResource = new Resource(allResources.size(), currentSite);

                currentSite.addResource(currentResource);
                allResources.add(currentResource);
            }
        }
    }

    // Reads which resources each process is waiting for
    static void inputProcessStatusTable() {
        System.out.println("\nNow, enter which processes are blocked on which resources.");
        System.out.println("Enter -1 if the process is not blocked on any resource.");

        // For every process, collect all resources it is blocked on
        allProcesses.forEach(p -> {
            System.out.print("Process " + p.processId + " : ");

            int resourceId;
            while (true) {
                resourceId = sc.nextInt();

                // -1 ends input for the current process
                if (resourceId == -1) break;

                // Ignore invalid resource ids and ask again
                if (!isValidResourceId(resourceId)) {
                    System.out.println("Invalid resource id: " + resourceId + ". Try again.");
                    continue;
                }

                // Store the resource in the PST of the process's site
                p.site.processStatusTable.get(p.processId).add(resourceId);
            }
        });
    }

    // Reads which processes currently hold each resource
    static void inputResourceStatusTable() {
        System.out.println("\nNow, enter which resources are allocated to which processes.");
        System.out.println("Enter -1 if the resource is not allocated to any process.");

        // For every resource, collect all processes it is allocated to
        allResources.forEach(r -> {
            System.out.print("Resource " + r.resourceId + " : ");

            int processId;
            while (true) {
                processId = sc.nextInt();

                // -1 ends input for the current resource
                if (processId == -1) break;

                // Ignore invalid process ids and ask again
                if (!isValidProcessId(processId)) {
                    System.out.println("Invalid process id: " + processId + ". Try again.");
                    continue;
                }

                // Store the process in the RST of the resource's site
                r.site.resourceStatusTable.get(r.resourceId).add(processId);
            }
        });
    }

    // Builds the global wait-for graph using the PST and RST
    static void buildWaitForGraph() {
        // Initialize empty adjacency list for every process
        allProcesses.forEach(p -> waitForGraph.put(p.processId, new LinkedHashSet<>()));

        // Construct WFG
        allProcesses.forEach(p -> {
            int waitingPid = p.processId;

            // Resources the current process is waiting for
            Set<Integer> blockedResources = p.site.processStatusTable.get(waitingPid);

            // For every resource the process is waiting for,
            // find the process currently holding that resource
            for (Integer resourceId : blockedResources) {
                // Search all sites because the resource may belong to any site
                for (Site site : sites) {

                    Set<Integer> allocatedProcessIds =
                        site.resourceStatusTable.get(resourceId);

                    // If the resource is not found in this site, skip it
                    if (allocatedProcessIds == null)
                        continue;

                    // Add edges from the waiting process to the holding process
                    allocatedProcessIds.forEach(pId -> {

                        // Pi -> Pj means Pi is waiting for Pj
                        if (pId != waitingPid) { // Avoid self-loop
                            waitForGraph.get(waitingPid).add(pId);
                        }
                    });
                }
            }
        });
    }

    // Prints the global wait-for graph
    static void printWaitForGraph() {
        System.out.println("\n===== GLOBAL WAIT-FOR GRAPH =====");

        waitForGraph.forEach((pid, neighbors) -> {

            System.out.print("P" + pid + " -> ");

            // If the process is not waiting for any other process
            if (neighbors.isEmpty()) {
                System.out.println("None");
                return;
            }

            // Print all neighboring processes this process is waiting for
            neighbors.forEach(neighbor ->
                System.out.print("P" + neighbor + " ")
            );

            System.out.println();
        });
    }

    // Performs DFS from a given node to check whether a cycle exists
    private static boolean dfsCycle(int node) {

        // Mark current process as visited and part of the current recursion path
        visited[node] = true;
        inStack[node] = true;

        // Traverse all processes that the current process is waiting for
        for (Integer neighbour : waitForGraph.get(node)) {

            // If the neighbor has not been visited, continue DFS
            if (!visited[neighbour]) {

                if (dfsCycle(neighbour))
                    return true;
            }

            // If the neighbor is already in the recursion stack,
            // a back edge exists, which means there is a cycle
            else if (inStack[neighbour]) {
                return true;
            }
        }

        // Remove current process from recursion stack before returning
        inStack[node] = false;

        return false;
    }

    // Detects deadlock by checking for a cycle in the wait-for graph
    private static boolean detectDeadlock() {
        int n = allProcesses.size();

        visited = new boolean[n];
        inStack = new boolean[n];

        // Run DFS from every unvisited process
        for (int i = 0; i < n; i++) {

            if (!visited[i]) {
                if (dfsCycle(i)) return true;
            }
        }

        // No cycle found, so no deadlock exists
        return false;
    }

    // Checks whether a process id exists
    static boolean isValidProcessId(int processId) {
        return processId >= 0 && processId < allProcesses.size();
    }

    // Checks whether a resource id exists
    static boolean isValidResourceId(int resourceId) {
        return resourceId >= 0 && resourceId < allResources.size();
    }
}
