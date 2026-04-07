package graph;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

public class DFSAdjacencyMatrix {
	static int nVertices = 0;
	static int[][] adj = new int[100][100];
	static boolean[] visited = new boolean[100];

	static void readGraph(String filePath) {
		try {
			Scanner file = new Scanner(new File("Graph.txt"));

			while (file.hasNext()) {
				int u = file.nextInt();
				int v = file.nextInt();

				adj[u][v] = 1;
				nVertices = Math.max(nVertices, Math.max(u, v) + 1);
			}

			file.close();
		} catch (FileNotFoundException e) {
			System.out.println("File " + filePath + " not found.");
		}
	}

	static void dfs(int v, int n, int[] comp, int[] size) {
		visited[v] = true;
		comp[size[0]++] = v;

		for (int i = 1; i < n; i++) {
			if (adj[v][i] == 1 && !visited[i]) {
				dfs(i, n, comp, size);
			}
		}
	}

	static void reach(int v, int n, boolean[] vis) {
		vis[v] = true;

		for (int i = 1; i < n; i++) {
			if (adj[v][i] == 1 && !vis[i]) {
				reach(i, n, vis);
			}
		}
	}

	public static void main(String[] args) {
		Scanner scInput = new Scanner(System.in);

		System.out.print("What is the file path? ");
		String filePath = scInput.nextLine();

		readGraph(filePath);

		int compNo = 1;

		for (int i = 1; i < nVertices; i++) {
			if (!visited[i]) {

				int[] comp = new int[100];
				int[] size = { 0 };

				dfs(i, nVertices, comp, size);

				for (int j = 0; j < size[0]; j++) {
					boolean[] vis = new boolean[100];
					int node = comp[j];

					reach(node, nVertices, vis);

					boolean ok = true;

					for (int k = 0; k < size[0]; k++) {
						if (!vis[comp[k]]) {
							ok = false;
							break;
						}
					}

					if (ok) {
						System.out.println("Component " + compNo + " Vertex: " + node);
						break;
					}
				}

				compNo++;
			}
		}

		scInput.close();
	}
}