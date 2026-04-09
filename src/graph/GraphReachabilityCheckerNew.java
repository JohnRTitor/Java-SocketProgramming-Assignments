import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

int nVertices = 0;
int[][] adj = new int[100][100];
boolean[] visited = new boolean[100];

void readGraph(String filePath) {
    try (Scanner file = new Scanner(new File(filePath))) {
        while (file.hasNextInt()) {
            int u = file.nextInt();
            int v = file.nextInt();

            adj[u][v] = 1;
            nVertices = Math.max(nVertices, Math.max(u, v) + 1);
        }

    } catch (FileNotFoundException e) {
        IO.println("File not found.");
        System.exit(0);
    }
}

void printAdjacencyMatrix() {
    IO.println("\nAdjacency Matrix:");

    // Column headers
    IO.print("\t");
    for (int i = 0; i < nVertices; i++) {
        IO.print("v" + i + "\t");
    }
    IO.println();

    for (int i = 0; i < nVertices; i++) {
        IO.print("v" + i + "\t");

        for (int j = 0; j < nVertices; j++) {
            IO.print(adj[i][j] + "\t");
        }
        IO.println();
    }
}

void dfsComponent(int v, int[] comp, int[] size) {
    visited[v] = true;
    comp[size[0]++] = v;

    for (int i = 0; i < nVertices; i++) {
        if ((adj[v][i] == 1 || adj[i][v] == 1) && !visited[i]) {
            dfsComponent(i, comp, size);
        }
    }
}

void reach(int v, boolean[] vis) {
    vis[v] = true;

    for (int i = 0; i < nVertices; i++) {
        if (adj[v][i] == 1 && !vis[i]) {
            reach(i, vis);
        }
    }
}

void printComponentVertices(int compNo, int[] comp, int size) {
    IO.print("Component " + compNo + " vertices: ");

    for (int i = 0; i < size; i++) {
        IO.print(comp[i]);
        if (i < size - 1) IO.print(", ");
    }

    IO.println();
}

void findRoots(int compNo, int[] comp, int size) {
    IO.print("Vertex/Vertices from which all are reachable in Component "
            + compNo + ": ");

    boolean found = false;

    for (int i = 0; i < size; i++) {
        int node = comp[i];
        boolean[] vis = new boolean[100];

        reach(node, vis);

        boolean canReachAll = true;

        for (int j = 0; j < size; j++) {
            if (!vis[comp[j]]) {
                canReachAll = false;
                break;
            }
        }

        if (canReachAll) {
            if (found) IO.print(", ");
            IO.print(node);
            found = true;
        }
    }

    if (!found) {
        IO.print("None");
    }

    IO.println();
}

void main() {
    IO.print("Enter file path: ");
    String filePath = IO.readln();

    readGraph(filePath);
    printAdjacencyMatrix();
    IO.println();

    int compNo = 1;

    for (int i = 0; i < nVertices; i++) {
        if (!visited[i]) {
            int[] comp = new int[100];
            int[] size = {0};

            dfsComponent(i, comp, size);

            printComponentVertices(compNo, comp, size[0]);
            findRoots(compNo, comp, size[0]);

            IO.println();
            compNo++;
        }
    }
}