package ho_ramamurthy_deadlock_detection;

import java.util.*;

class Process extends Thread {
    // Unique identifier assigned globally to each process
    int processId;

    // Site where this process is located
    Site site;

    Process(int processId, Site site) {
        this.processId = processId;
        this.site = site;
    }

    @Override
    public String toString() {
        return "Process " + processId;
    }
}

class Resource {
    // Unique identifier assigned globally to each resource
    final int resourceId;

    // Site where this resource is located
    final Site site;

    Resource(int resourceId, Site site) {
        this.resourceId = resourceId;
        this.site = site;
    }

    @Override
    public String toString() {
        return "Resource " + resourceId;
    }
}

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

    Site(int siteId) {
        this.siteId = siteId;
    }

    void addProcess(Process p) {
        if (p.site != this)
            throw new IllegalArgumentException(
                "Process " + p.processId + " is not on site " + siteId
            );

        processes.put(p.processId, p);

        // initialize PST row
        processStatusTable.put(p.processId, new LinkedHashSet<>());
    }

    void addResource(Resource r) {
        if (r.site != this)
            throw new IllegalArgumentException(
                "Resource " + r.resourceId + " is not on site " + siteId
            );

        resources.put(r.resourceId, r);

        // initialize RST row
        resourceStatusTable.put(r.resourceId, new LinkedHashSet<>());
    }

    void printProcessStatusTable() {
        System.out.println("\n===== PST FOR SITE " + siteId + " =====");

        // Only resources that appear in any PST row
        Set<Integer> usedResources = new LinkedHashSet<>();

        processStatusTable.values().forEach(usedResources::addAll);

        // Fallback
        if (usedResources.isEmpty()) {
            System.out.println("No process waiting for a blocked resource.");
            return;
        }

        // Header
        System.out.print("      ");
        usedResources.forEach(rId ->
            System.out.printf("R%-4d", rId)
        );
        System.out.println();

        // Rows
        processes.forEach((pId, p) -> {
            System.out.printf("P%-4d ", pId);

            Set<Integer> blockedResources = processStatusTable.get(pId);

            usedResources.forEach(rId -> {
                int value = blockedResources.contains(rId) ? 1 : 0;

                System.out.printf("%-5d", value);
            });

            System.out.println();
        });
    }

    void printResourceStatusTable() {
        System.out.println("\n===== RST FOR SITE " + siteId + " =====");

        // Only processes that appear in any RST row
        Set<Integer> usedProcesses = new LinkedHashSet<>();

        resourceStatusTable.values().forEach(usedProcesses::addAll);

        // Fallback
        if (usedProcesses.isEmpty()) {
            System.out.println("No allocated processes.");
            return;
        }

        // Header
        System.out.print("      ");
        usedProcesses.forEach(pId ->
            System.out.printf("P%-4d", pId)
        );
        System.out.println();

        // Rows
        resources.forEach((rId, r) -> {
            System.out.printf("R%-4d ", rId);

            Set<Integer> allocatedProcesses =
                resourceStatusTable.get(rId);

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
    private static final Scanner sc = new Scanner(System.in);

    static Set<Site> sites = new LinkedHashSet<>();

    static Set<Process> allProcesses = new LinkedHashSet<>();
    static Set<Resource> allResources = new LinkedHashSet<>();

    private static Map<Integer, Set<Integer>> waitForGraph = new LinkedHashMap<>();

    private static boolean[] visited;
    private static boolean[] inStack;

    public static void main(String[] args) {
        System.out.println("===== HO RAMAMURTHY'S DEADLOCK DETECTION =====");

        inputSiteProcessResourceCount();

        System.out.println("\nTotal processes: " + allProcesses.size());
        System.out.println("Total resources: " + allResources.size());

        inputProcessStatusTable();
        inputResourceStatusTable();
        
        sites.forEach(site -> {
            site.printProcessStatusTable();
            site.printResourceStatusTable();
        });

        buildWaitForGraph();
        printWaitForGraph();

        if (detectDeadlock()) System.out.println("Deadlock detected.");
        else System.out.println("No deadlock detected.");
    }

    static void inputSiteProcessResourceCount() {
        System.out.print("How many sites? ");
        int nSites = sc.nextInt();

        System.out.println("Now, for each site, enter the number of processes and resources.");
        for (int i = 0; i < nSites; i++) {
            Site currentSite = new Site(i);
            sites.add(currentSite);

            System.out.print("Site " + i + " : ");
            int nProcesses = sc.nextInt();
            int nResources = sc.nextInt();

            for (int j = 0; j < nProcesses; j++) {
                Process currentProcess = new Process(allProcesses.size(), currentSite);

                currentSite.addProcess(currentProcess);
                allProcesses.add(currentProcess);
            }

            for (int j = 0; j < nResources; j++) {
                Resource currentResource = new Resource(allResources.size(), currentSite);

                currentSite.addResource(currentResource);
                allResources.add(currentResource);
            }
        }
    }

    static void inputProcessStatusTable() {
        System.out.println("\nNow, enter which processes are blocked on which resources.");
        System.out.println("Enter -1 if the process is not blocked on any resource.");

        allProcesses.forEach(p -> {
            System.out.print("Process " + p.processId + " : ");

            int resourceId;
            while (true) {
                resourceId = sc.nextInt();
                if (resourceId == -1) break;

                if (!isValidResourceId(resourceId)) {
                    System.out.println("Invalid resource id: " + resourceId + ". Try again.");
                    continue;
                }

                p.site.processStatusTable.get(p.processId).add(resourceId);
            }
        });
    }

    static void inputResourceStatusTable() {
        System.out.println("\nNow, enter which resources are allocated to which processes.");
        System.out.println("Enter -1 if the resource is not allocated to any process.");

        allResources.forEach(r -> {
            System.out.print("Resource " + r.resourceId + " : ");

            int processId;
            while (true) {
                processId = sc.nextInt();
                if (processId == -1) break;

                if (!isValidProcessId(processId)) {
                    System.out.println("Invalid process id: " + processId + ". Try again.");
                    continue;
                }

                r.site.resourceStatusTable.get(r.resourceId).add(processId);
            }
        });
    }

    static void buildWaitForGraph() {
        // initialize empty adjacency list
        allProcesses.forEach(p -> waitForGraph.put(p.processId, new LinkedHashSet<>()));

        // Construct WFG
        allProcesses.forEach(p -> {
            int waitingPid = p.processId;

            // Resources current process is waiting for
            Set<Integer> blockedResources = p.site.processStatusTable.get(waitingPid);

            for (Integer resourceId : blockedResources) {
                // Find who owns this resource
                for (Site site : sites) {

                    Set<Integer> allocatedProcessIds =
                        site.resourceStatusTable.get(resourceId);

                    if (allocatedProcessIds == null)
                        continue;

                    allocatedProcessIds.forEach(pId -> {

                        // Pi -> Pj
                        if (pId != waitingPid) { // avoid self-loop
                            waitForGraph.get(waitingPid).add(pId);
                        }
                    });
                }
            }
        });

    }

    static void printWaitForGraph() {
        System.out.println("\n===== GLOBAL WAIT-FOR GRAPH =====");

        waitForGraph.forEach((pid, neighbors) -> {

            System.out.print("P" + pid + " -> ");

            if (neighbors.isEmpty()) {
                System.out.println("None");
                return;
            }

            neighbors.forEach(neighbor ->
                System.out.print("P" + neighbor + " ")
            );

            System.out.println();
        });
    }


    private static boolean dfsCycle(int node) {

        visited[node] = true;
        inStack[node] = true;

        // Traverse adjacency list
        for (Integer neighbour : waitForGraph.get(node)) {

            // Unvisited neighbor
            if (!visited[neighbour]) {

                if (dfsCycle(neighbour))
                    return true;
            }

            // Back edge found -> cycle
            else if (inStack[neighbour]) {
                return true;
            }
        }

        // Remove from recursion stack
        inStack[node] = false;

        return false;
    }

    private static boolean detectDeadlock() {
        int n = allProcesses.size();

        visited = new boolean[n];
        inStack = new boolean[n];

        for (int i = 0; i < n; i++) {

            if (!visited[i]) {
                if (dfsCycle(i)) return true;
            }
        }

        return false;
    }


    static boolean isValidProcessId(int processId) {
        return processId >= 0 && processId < allProcesses.size();
    }

    static boolean isValidResourceId(int resourceId) {
        return resourceId >= 0 && resourceId < allResources.size();
    }
}
