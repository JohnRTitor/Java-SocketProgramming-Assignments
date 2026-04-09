package graph;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

class GraphReachabilityChecker {
	static int nVertices = 0;
	static int[][] adj = new int[100][100];
	static boolean[] visited = new boolean[100];

	static void readGraph(String filePath) {
		try {
			Scanner file = new Scanner(new File(filePath));

			while (file.hasNextInt()) {
				int u = file.nextInt();
				int v = file.nextInt();

				adj[u][v] = 1;
				nVertices = Math.max(nVertices, Math.max(u, v) + 1);
			}

			file.close();
		} catch (FileNotFoundException e) {
			System.out.println("File not found.");
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

	static void dfsComponent(int v, int[] comp, int[] size) {
		visited[v] = true;
		comp[size[0]++] = v;

		for (int i = 0; i < nVertices; i++) {

			if ((adj[v][i] == 1 || adj[i][v] == 1) && !visited[i]) {
				dfsComponent(i, comp, size);
			}
		}
	}


	static void reach(int v, boolean[] vis) {
		vis[v] = true;

		for (int i = 0; i < nVertices; i++) {
			if (adj[v][i] == 1 && !vis[i]) {
				reach(i, vis);
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
				if (found) System.out.print(", ");
				System.out.print(node);
				found = true;
			}
		}

		if (!found) {
			System.out.print("None");
		}

		System.out.println();
	}

	public static void main(String[] args) {
		Scanner sc = new Scanner(System.in);

		System.out.print("Enter file path: ");
		String filePath = sc.nextLine();

		readGraph(filePath);

		System.out.println("\nAdjacency Matrix:");
		printAdjacencyMatrix();
		System.out.println();

		int compNo = 1;

		for (int i = 0; i < nVertices; i++) {
			if (!visited[i]) {
				int[] comp = new int[100];
				int[] size = {0};


				dfsComponent(i, comp, size);

				printComponentVertices(compNo, comp, size[0]);
				findRoots(compNo, comp, size[0]);

				System.out.println();
				compNo++;
			}
		}

		sc.close();
	}
}