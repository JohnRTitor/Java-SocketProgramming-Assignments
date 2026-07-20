package graph;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

class GraphReachabilityChecker {
    static int nVertices = 0;
    static int[][] adj = new int[100][100];

    static void readGraph(String filePath) {
        try (Scanner file = new Scanner(new File(filePath))) {
            // in each line of the file: an edge (u, v) is stored like this:
            // 2 4
            // 4 3
            while (file.hasNextInt()) {
                int u = file.nextInt();
                int v = file.nextInt();

                // directed graph, so add edge from u to v
                adj[u][v] = 1;
                // vertices start from the number 0, so add 1 to max of u or v, ie
                // if (4, 5) is found, there must be 5 + 1 = 6 vertices (0, 1, 2, 3, 4, 5)
                nVertices = Math.max(nVertices, Math.max(u, v) + 1);
            }

        } catch (FileNotFoundException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(0);
        }
    }

    static void printAdjacencyMatrix() {
        // Print column headers
        System.out.print("\t");
        for (int i = 0; i < nVertices; i++) {
            System.out.print("v" + i + "\t");
        }
        System.out.println();

        for (int i = 0; i < nVertices; i++) {
            // Print row header
            System.out.print("v" + i + "\t");

            for (int j = 0; j < nVertices; j++) {
                System.out.print(adj[i][j] + "\t");
            }
            System.out.println();
        }
    }

    static void dfsComponent(int v, boolean[] visited, int[] comp, int[] size) {
        visited[v] = true;
        // add the vertex to the comp array
        comp[size[0]++] = v;

        for (int i = 0; i < nVertices; i++) {
            // if there is an edge from vertex v to i or vice versa
            // they are in the same component, and perform DFS recursively
            // if and only if the vertex i is not visited
            if ((adj[v][i] == 1 || adj[i][v] == 1) && !visited[i]) {
                dfsComponent(i, visited, comp, size);
            }
        }
    }

    static void dfsReach(int v, boolean[] visited) {
        visited[v] = true;

        for (int i = 0; i < nVertices; i++) {
            // classic DFS: if there is an edge between vertex v and i
            // and vertex is not visited already, perform DFS recursively
            if (adj[v][i] == 1 && !visited[i]) {
                dfsReach(i, visited);
            }
        }
    }

    static void printComponentVertices(int compNo, int[] comp, int size) {
        System.out.print("Component " + compNo + " vertices: ");

        for (int i = 0; i < size; i++) {
            System.out.print(comp[i]);
            if (i < size - 1) System.out.print(", ");
        }

        System.out.println();
    }

    static void findRoots(int compNo, int[] comp, int size) {
        System.out.print("Vertex/Vertices from which all are reachable in Component "
                + compNo + ": ");

        boolean foundAtleastOne = false;

        for (int i = 0; i < size; i++) {
            int node = comp[i];
            boolean[] visitedComp = new boolean[nVertices];

            dfsReach(node, visitedComp);

            // assume that after dfsReach, we visited all vertices
            boolean canReachAll = true;

            // check the visitedComp array if there were any vertex not visited
            for (int j = 0; j < size; j++) {
                if (!visitedComp[comp[j]]) {
                    // if even any one of the vertices was not visited, disqualify
                    canReachAll = false;
                    break;
                }
            }

            if (canReachAll) {
                if (foundAtleastOne) System.out.print(", ");
                else foundAtleastOne = true;

                System.out.print(node);
            }
        }

        if (!foundAtleastOne) System.out.print("None");

        System.out.println();
    }

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);

        System.out.print("Enter file path: ");
        String filePath = sc.nextLine();

        // Read the edges from the graph and construct an adjacency matrix
        // and put it in the adj[][] 2D array
        // the function assumes that vertices start with number 0
        readGraph(filePath);

        System.out.println("\nAdjacency Matrix:");
        printAdjacencyMatrix();
        System.out.println();

        int compNo = 1;
        boolean[] visitedMain = new boolean[100];

        for (int i = 0; i < nVertices; i++) {
            // for each vertex, from 0 to nVertices - 1
            // if it is not visited
            if (!visitedMain[i]) {
                // initialize new array for storing vertices of the component
                int[] comp = new int[nVertices];
                // since java does not have pointers, this workaround allows us to
                // set the size in a function using `size[0]`
                int[] size = {0};

                // visit them and put them in a component
                dfsComponent(i, visitedMain, comp, size);

                // print the vertices in the component
                printComponentVertices(compNo, comp, size[0]);
                // find the root(s) of the component, ie, a
                // vertex which has
                findRoots(compNo, comp, size[0]);

                System.out.println();
                compNo++;
            }
        }

        sc.close();
    }
}