package com.example.fingerprintlocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

public class PathFinder {

    // Core method: find the shortest path from startId to endId
    public static List<MapData.Node> findPath(String startId, String endId) {
        // 1. Check input validity
        if (!MapData.nodes.containsKey(startId) || !MapData.nodes.containsKey(endId)) {
            return new ArrayList<>(); // Start or end point does not exist
        }

        // 2. Initialize data structures required for Dijkstra's algorithm
        Map<String, Double> distances = new HashMap<>(); // Store the shortest distance from the start point to each point
        Map<String, String> previous = new HashMap<>();  // Store the predecessor node of the path (for backtracking the path)
        Set<String> visited = new HashSet<>();           // Processed nodes

        // Priority queue, sorted by distance in ascending order
        PriorityQueue<NodeDistance> queue = new PriorityQueue<>(Comparator.comparingDouble(nd -> nd.dist));

        // Initialization
        for (String id : MapData.nodes.keySet()) {
            distances.put(id, Double.MAX_VALUE);
        }
        distances.put(startId, 0.0);
        queue.add(new NodeDistance(startId, 0.0));

        // 3. Start search
        while (!queue.isEmpty()) {
            NodeDistance current = queue.poll();
            String u = current.id;

            if (u.equals(endId)) {
                break; // Found the end point, terminate early
            }

            if (visited.contains(u)) continue;
            visited.add(u);

            // Traverse all neighbors
            List<String> neighbors = getNeighbors(u);
            for (String v : neighbors) {
                if (visited.contains(v)) continue;

                // Calculate distance: here we simply use Euclidean distance as the weight
                double weight = calculateDistance(u, v);
                double newDist = distances.get(u) + weight;

                if (newDist < distances.get(v)) {
                    distances.put(v, newDist);
                    previous.put(v, u);
                    queue.add(new NodeDistance(v, newDist));
                }
            }
        }

        // 4. Backtrack the path (from end back to start)
        List<MapData.Node> path = new ArrayList<>();
        String curr = endId;

        if (!previous.containsKey(curr) && !curr.equals(startId)) {
            return new ArrayList<>(); // Unreachable
        }

        while (curr != null) {
            path.add(MapData.nodes.get(curr));
            curr = previous.get(curr);
        }

        // Reverse the list to be start -> end
        Collections.reverse(path);
        return path;
    }

    // Helper class: Priority queue element
    private static class NodeDistance {
        String id;
        double dist;
        NodeDistance(String id, double dist) {
            this.id = id;
            this.dist = dist;
        }
    }

    // Get all neighbor IDs of a node
    private static List<String> getNeighbors(String nodeId) {
        List<String> neighbors = new ArrayList<>();
        for (MapData.Edge edge : MapData.edges) {
            if (edge.from.equals(nodeId)) {
                neighbors.add(edge.to);
            } else if (edge.to.equals(nodeId)) {
                neighbors.add(edge.from);
            }
        }
        return neighbors;
    }

    // Calculate the Euclidean distance between two points (if on different floors, give a large penalty weight)
    private static double calculateDistance(String id1, String id2) {
        MapData.Node n1 = MapData.nodes.get(id1);
        MapData.Node n2 = MapData.nodes.get(id2);

        if (n1 == null || n2 == null) return Double.MAX_VALUE;

        // If the floors are different, consider it as a staircase connecting them.
        if (n1.floor != n2.floor) {
            return 5000.0; // Huge Penalty
        }
        // Calculate the pixel distance at the same floor
        double dx = n1.x - n2.x;
        double dy = n1.y - n2.y;
        return Math.sqrt(dx * dx + dy * dy);
    }
}