package com.example.fingerprintlocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Map Data Repository
 * Stores all points (Nodes) and connections (Edges)
 * and the pixels/meter scale for each floor.
 */
public class MapData {

    // Stores all points: Key=name (e.g., "T7-301"), Value=node object
    public static Map<String, Node> nodes = new HashMap<>();

    // Stores all edges: defines the road network connectivity
    public static List<Edge> edges = new ArrayList<>();

    // Stores the scale for each floor: Key=floor(3), Value=pixels/meter (e.g., 30.0f)
    // Used for PDR step length conversion: how many pixels on the map for 1 meter in reality
    public static Map<Integer, Float> floorScales = new HashMap<>();

    // Static block: automatically initializes data when the class is loaded
    static {
        initScales();
        initNodes();
        initEdges();
    }

    /**
     * * Step 1: Define the scale for each floor (Pixels per Meter)
     * * Method: Measure how many pixels a 20-meter long corridor is on the map in Photoshop, then divide by 20.
     * Resolution data:
     * F3: 1462 x 1096 (Baseline)
     * F4: 1178 x 884  (Scaled down to approx. 0.8x)
     * F5: 1496 x 1122 (Scaled up to approx. 1.02x)
     */
    private static void initScales() {
        // === 1. Set the baseline (you need to calibrate this 12.04f based on the actual size of the 3rd floor) ===
        // If the 3rd-floor map width of 1462px corresponds to a real building width of 50 meters, then fill in 1462/50 = 29.24f here
        float baseScale = 12.04f;

        // === 2. Automatically calculate for other floors (maintaining physical distance consistency) ===

        // Floor 3
        floorScales.put(3, baseScale);

        // Floor 4: Because the map is smaller, the corresponding pixels per meter should also be less
        // Formula: baseline * (F4_width / F3_width)
        floorScales.put(4, baseScale * (1178f / 1462f));

        // Floor 5: similar to the 3rd floor
        floorScales.put(5, baseScale * (1496f / 1462f));
    }

    /**
     * Step 2: Enter coordinates.
     * Note: The last parameter is the floor (3, 4, or 5)
     */


    public static void initNodes() {
        nodes.clear();
        // ==========================================
        // Floor 3 (3F)
        // ==========================================

        // --- Green network nodes (recommended to be identical to what you entered during fingerprint scanning) ---

        nodes.put("t6-30A", new Node("t6-30A", 1176, 791, 3));
        nodes.put("t7-30A", new Node("t7-30A", 636, 404, 3));

        nodes.put("t6-30B", new Node("t6-30B", 899, 941, 3));
        nodes.put("t7-30B", new Node("t7-30B", 399, 540, 3));

        nodes.put("t6-30C", new Node("t6-30C", 1309, 815, 3));
        nodes.put("t7-30C", new Node("t7-30C", 751, 420, 3));

        nodes.put("t6-30D", new Node("t6-30D", 757, 791, 3));

        nodes.put("t6-30E", new Node("t6-30E", 1164, 919, 3));
        nodes.put("t7-30E", new Node("t7-30E", 666, 515, 3));
        // --- Red terminal nodes (classroom centers) ---
        // Must be in lowercase, same as the input in AdminActivity
        nodes.put("t6-301", new Node("t6-301", 772, 961, 3));
        // nodes.put("t6-302", new Node("t6-302", 0, 0, 3));
        nodes.put("t6-303", new Node("t6-303", 970, 923, 3));
        nodes.put("t6-304", new Node("t6-304", 1075, 890, 3));

        nodes.put("t7-301", new Node("t7-301", 279, 563, 3));
        nodes.put("t7-302", new Node("t7-302", 189, 314, 3));
        nodes.put("t7-303", new Node("t7-303", 170, 195, 3));
        nodes.put("t7-304", new Node("t7-304", 464, 520, 3));
        nodes.put("t7-305", new Node("t7-305", 561, 499, 3));
        nodes.put("t7-306", new Node("t7-306", 719, 143, 3));

        // --- Virtual Door Nodes ---
        // These are defined by you; for consistency, it's recommended to use lowercase as well
        nodes.put("door_t6-301", new Node("door_t6-301", 787, 887, 3));
        nodes.put("door_t6-303", new Node("door_t6-303", 985, 848, 3));
        nodes.put("door_t6-304", new Node("door_t6-304", 1117, 818, 3));

        nodes.put("door_t7-301", new Node("door_t7-301", 328, 491, 3));
        nodes.put("door_t7-302", new Node("door_t7-302", 273, 330, 3));
        nodes.put("door_t7-303", new Node("door_t7-303", 250, 213, 3));
        nodes.put("door_t7-304", new Node("door_t7-304", 432, 462, 3));
        nodes.put("door_t7-305", new Node("door_t7-305", 576, 430, 3));
        nodes.put("door_t7-306", new Node("door_t7-306", 731, 226, 3));

        // --- Floor 3 ---
        // Add a corridor connection point near t7-30B (399, 540)
        nodes.put("door_t7-30B", new Node("door_t7-30B", 384, 475, 3));

        // --- Floor 4 ---
        // Add a corridor connection point near t7-40B (315, 460)
        nodes.put("door_t7-40B", new Node("door_t7-40B", 303, 407, 4));
        // ==========================================
        // Floor 4 (4F)
        // ==========================================

        nodes.put("t6-40A", new Node("t6-40A", 951, 663, 4));
        nodes.put("t7-40A", new Node("t7-40A", 513, 350, 4));

        nodes.put("t6-40B", new Node("t6-40B", 750, 775, 4));
        nodes.put("t7-40B", new Node("t7-40B", 315, 460, 4));

        nodes.put("t6-40C", new Node("t6-40C", 1051, 681, 4));
        nodes.put("t7-40C", new Node("t7-40C", 611, 361, 4));

        nodes.put("t6-40D", new Node("t6-40D", 636, 656, 4));

        nodes.put("t6-40E", new Node("t6-40E", 943, 761, 4));
        nodes.put("t7-40E", new Node("t7-40E", 510, 443, 4));

        nodes.put("t6-401", new Node("t6-401", 619, 800, 4));
        nodes.put("t6-402", new Node("t6-402", 688, 785, 4));
        // nodes.put("t6-403", new Node("t6-403", 0, 0, 4));
        nodes.put("t6-404", new Node("t6-404", 819, 756, 4));
        nodes.put("t6-405", new Node("t6-405", 900, 735, 4));

        nodes.put("t7-401", new Node("t7-401", 183, 487, 4));
        nodes.put("t7-402", new Node("t7-402", 258, 472, 4));
        nodes.put("t7-403", new Node("t7-403", 363, 447, 4));
        nodes.put("t7-404", new Node("t7-404", 400, 437, 4));
        nodes.put("t7-405", new Node("t7-405", 441, 425, 4));
        nodes.put("t7-406", new Node("t7-406", 477, 417, 4));
        nodes.put("t7-407", new Node("t7-407", 664, 353, 4));

        // --- Virtual Door Nodes ---
        // These are defined by you; for consistency, it's recommended to use lowercase as well
        nodes.put("door_t6-401", new Node("door_t6-401", 638, 744, 4));
        nodes.put("door_t6-402", new Node("door_t6-402", 704, 729, 4));
        // nodes.put("t6-403", new Node("t6-403", 0, 0, 4));
        nodes.put("door_t6-404", new Node("door_t6-404", 850, 694, 4));
        nodes.put("door_t6-405", new Node("door_t6-405", 905, 683, 4));

        nodes.put("door_t7-401", new Node("door_t7-401", 207, 428, 4));
        nodes.put("door_t7-402", new Node("door_t7-402", 272, 415, 4));
        nodes.put("door_t7-403", new Node("door_t7-403", 336, 400, 4));
        nodes.put("door_t7-404", new Node("door_t7-404", 383, 391, 4));
        nodes.put("door_t7-405", new Node("door_t7-405", 437, 377, 4));
        nodes.put("door_t7-406", new Node("door_t7-406", 474, 369, 4));
        nodes.put("door_t7-407", new Node("door_t7-407", 693, 387, 4));

        // ==========================================
        // Floor 5 (5F)
        // ==========================================

        nodes.put("t6-50A", new Node("t6-50A", 1157, 749, 5));
        nodes.put("t7-50A", new Node("t7-50A", 628, 450, 5));

        nodes.put("t6-50B", new Node("t6-50B", 899, 893, 5));
        nodes.put("t7-50B", new Node("t7-50B", 392, 568, 5));

        nodes.put("t6-50C", new Node("t6-50C", 1284, 773, 5));
        nodes.put("t7-50C", new Node("t7-50C", 749, 471, 5));

        nodes.put("t6-50D", new Node("t6-50D", 747, 785, 5));

        nodes.put("t6-50E", new Node("t6-50E", 1147, 874, 5));
        nodes.put("t7-50E", new Node("t7-50E", 624, 570, 5));

        nodes.put("t6-501", new Node("t6-501", 738, 926, 5));
        nodes.put("t6-502", new Node("t6-502", 834, 905, 5));
        nodes.put("t6-503", new Node("t6-503", 998, 874, 5));
        nodes.put("t6-504", new Node("t6-504", 1090, 847, 5));
        nodes.put("t6-505", new Node("t6-505", 1347, 754, 5));

        nodes.put("t7-501", new Node("t7-501", 229, 599, 5));
        nodes.put("t7-502", new Node("t7-502", 324, 581, 5));
        nodes.put("t7-503", new Node("t7-503", 479, 551, 5));
        nodes.put("t7-504", new Node("t7-504", 577, 534, 5));
        nodes.put("t7-505", new Node("t7-505", 813, 456, 5));

        // --- Virtual Door Nodes ---
        // These are defined by you; for consistency, it's recommended to use lowercase as well
        nodes.put("door_t6-501", new Node("door_t6-501", 766, 854, 5));
        nodes.put("door_t6-502", new Node("door_t6-502", 848, 835, 5));
        nodes.put("door_t6-503", new Node("door_t6-503", 1030, 792, 5));
        nodes.put("door_t6-504", new Node("door_t6-504", 1107, 781, 5));
        nodes.put("door_t6-505", new Node("door_t6-505", 1380, 800, 5));

        nodes.put("door_t7-501", new Node("door_t7-501", 263, 527, 5));
        nodes.put("door_t7-502", new Node("door_t7-502", 342, 515, 5));
        nodes.put("door_t7-503", new Node("door_t7-503", 512, 484, 5));
        nodes.put("door_t7-504", new Node("door_t7-504", 582, 473, 5));
        nodes.put("door_t7-505", new Node("door_t7-505", 844, 507, 5));

    }
    // 3. [Key Helper Method] Get the "door node" corresponding to a room
    // MapData.java

    public static Node getNearestDoor(String roomName) {
        if (roomName == null) return null;
        String target = roomName.toLowerCase().trim();

        // Strategy modification: prioritize finding the door (door_xxx) instead of the original name (xxx)

        // 1. If the user input already starts with "door_", it means they are an expert, so search directly
        if (target.startsWith("door_")) {
            if (nodes.containsKey(target)) {
                return nodes.get(target);
            }
        }

        // 2. [Core Modification] First, try to prepend "door_" to search
        // e.g., for input "t6-301", we first check if "door_t6-301" exists
        String doorName = "door_" + target;
        if (nodes.containsKey(doorName)) {
            // Found the door! Return the door node
            return nodes.get(doorName);
        }

        // 3. [Fallback] If there is no door (e.g., for an intersection point like t6-30A), return the node itself
        if (nodes.containsKey(target)) {
            return nodes.get(target);
        }

        return null;
    }
    /**
     * Step 3: Define connections (defines where paths exist; unconnected means it's a wall)
     */
    private static void initEdges() {
        // ==========================================
        // Floor 3 (3F) Connections
        // ==========================================

        // 1. Room <-> Door
        connect("t6-301", "door_t6-301");
        connect("t6-303", "door_t6-303");
        connect("t6-304", "door_t6-304");

        connect("t7-301", "door_t7-301");
        connect("t7-302", "door_t7-302");
        connect("t7-303", "door_t7-303");
        connect("t7-304", "door_t7-304");
        connect("t7-305", "door_t7-305");
        connect("t7-306", "door_t7-306");

        // 2. Door <-> Network
        // Inferred nearest connection points based on coordinates and hand-drawn map

        // T6-T7 route
        connect("t6-30C", "t6-30E");
        connect("t6-30A", "t6-30E");
        connect("t6-30A", "door_t6-304");
        connect("door_t6-304", "door_t6-303");
        connect("door_t6-303", "t6-30B");
        connect("door_t6-301", "t6-30B");
        connect("door_t6-303", "door_t6-301");
        connect("door_t6-301", "t6-30D");
        connect("t6-30D", "t7-30E");
        connect("t7-30E", "t7-30C");
        connect("t7-30E", "t7-30A");
        connect("t7-30A", "door_t7-306");
        connect("t7-30A", "door_t7-305");
        connect("door_t7-304", "door_t7-305");
        connect("door_t7-304", "door_t7-301");
        connect("door_t7-301", "door_t7-30B");
        connect("door_t7-304", "door_t7-30B");
        connect("door_t7-30B", "t7-30B");
        connect("door_t7-302", "door_t7-301");
        connect("door_t7-302", "door_t7-303");


        // ==========================================
        // Floor 4 (4F) Connections
        // ==========================================

        // 1. Room <-> Door
        connect("t6-401", "door_t6-401");
        connect("t6-402", "door_t6-402");
        connect("t6-404", "door_t6-404");
        connect("t6-405", "door_t6-405");

        connect("t7-401", "door_t7-401");
        connect("t7-402", "door_t7-402");
        connect("t7-403", "door_t7-403");
        connect("t7-404", "door_t7-404");
        connect("t7-405", "door_t7-405");
        connect("t7-406", "door_t7-406");
        connect("t7-407", "door_t7-407");

        // 2. Door <-> Network
        // Inferred nearest connection points based on coordinates and hand-drawn map

        // T6-T7 route
        connect("t6-40C", "t6-40A");
        connect("t6-40A", "t6-40E");
        connect("t6-40A", "door_t6-405");
        connect("door_t6-404", "door_t6-405");
        connect("door_t6-404", "t6-40B");
        connect("door_t6-402", "t6-40B");
        connect("door_t6-404", "door_t6-402");
        connect("door_t6-401", "door_t6-402");
        connect("door_t6-401", "t6-40D");
        connect("t6-40D", "t7-40E");
        connect("t7-40E", "t7-40C");
        connect("door_t7-407", "t7-40E");
        connect("t7-40E", "t7-40A");
        connect("t7-40A", "door_t7-406");
        connect("door_t7-406", "door_t7-405");
        connect("door_t7-404", "door_t7-405");
        connect("door_t7-404", "door_t7-403");
        connect("door_t7-403", "door_t7-40B");
        connect("door_t7-402", "door_t7-40B");
        connect("door_t7-40B", "t7-40B");
        connect("door_t7-403", "door_t7-402");
        connect("door_t7-402", "door_t7-401");

        // ==========================================
        // Floor 5 (5F) Connections
        // ==========================================

        // 1. Room <-> Door
        connect("t6-501", "door_t6-501");
        connect("t6-502", "door_t6-502");
        connect("t6-503", "door_t6-503");
        connect("t6-504", "door_t6-504");
        connect("t6-505", "door_t6-505");

        connect("t7-501", "door_t7-501");
        connect("t7-502", "door_t7-502");
        connect("t7-503", "door_t7-503");
        connect("t7-504", "door_t7-504");
        connect("t7-505", "door_t7-505");

        // 2. Door <-> Network
        // Inferred nearest connection points based on coordinates and hand-drawn map
        // T6-T7 route
        connect("t6-50E", "door_t6-505");
        connect("t6-50C", "t6-50E");
        connect("t6-50A", "t6-50E");
        connect("t6-50A", "door_t6-504");
        connect("door_t6-503", "door_t6-504");
        connect("door_t6-503", "t6-50B");
        connect("door_t6-502", "t6-50B");
        connect("door_t6-503", "door_t6-502");
        connect("door_t6-501", "door_t6-502");
        connect("door_t6-501", "t6-50D");
        connect("t6-50D", "t7-50E");
        connect("t7-50E", "t7-50C");
        connect("door_t7-505", "t7-50E");
        connect("t7-50E", "t7-50A");
        connect("t7-50A", "door_t7-504");
        connect("door_t7-504", "door_t7-503");
        connect("door_t7-503", "t7-50B");
        connect("door_t7-502", "t7-50B");
        connect("door_t7-503", "door_t7-502");
        connect("door_t7-503", "door_t7-501");


        // ==========================================
        // Cross-floor connections (Stairs) - Vertical traffic
        // ==========================================
        // Assume stairs with the same number (e.g., t6-30B and t6-40B) are in the same stairwell

        // T6 Stairs B
        connect("t6-30B", "t6-40B");
        connect("t6-40B", "t6-50B");

        // T6 Stairs C
        connect("t6-30C", "t6-40C");
        connect("t6-40C", "t6-50C");

        // T7 Stairs B
        connect("t7-30B", "t7-40B");
        connect("t7-40B", "t7-50B");

        // T7 Stairs C
        connect("t7-30C", "t7-40C");
        connect("t7-40C", "t7-50C");
    }

    // Helper function: bidirectional connection
    private static void connect(String id1, String id2) {
        // To prevent crashes from typos in IDs, add a check
        if (!nodes.containsKey(id1) || !nodes.containsKey(id2)) {
            // Warn in the Log, or throw an exception to remind yourself
            System.err.println("MapData Error: Cannot connect " + id1 + " and " + id2 + ", node not found.");
            return;
        }
        edges.add(new Edge(id1, id2));
        edges.add(new Edge(id2, id1));
    }

    // ================= Data Structure Definitions =================

    public static class Node {
        public String id;
        public float x;
        public float y;
        public int floor; // 3, 4, 5

        public Node(String id, float x, float y, int floor) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.floor = floor;
        }
    }

    public static class Edge {
        public String from;
        public String to;
        public float weight; // Default is distance; can be set higher for stairs

        public Edge(String from, String to) {
            this.from = from;
            this.to = to;
            this.weight = 1.0f; // Simple version, temporarily set to 1, or calculate based on distance
        }
    }
}