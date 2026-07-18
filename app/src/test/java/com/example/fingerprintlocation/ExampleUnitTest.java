package com.example.fingerprintlocation;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ExampleUnitTest {
    @Test
    public void sameFloorRouteKeepsRequestedEndpoints() {
        MapData.Node start = MapData.getNearestDoor("t7-301");
        MapData.Node end = MapData.getNearestDoor("t7-306");

        List<MapData.Node> path = PathFinder.findPath(start.id, end.id);

        assertFalse(path.isEmpty());
        assertEquals(start.id, path.get(0).id);
        assertEquals(end.id, path.get(path.size() - 1).id);
    }

    @Test
    public void crossFloorRouteContainsVerticalTransition() {
        MapData.Node start = MapData.getNearestDoor("t6-301");
        MapData.Node end = MapData.getNearestDoor("t6-501");

        List<MapData.Node> path = PathFinder.findPath(start.id, end.id);

        assertFalse(path.isEmpty());
        assertTrue(path.stream().anyMatch(node -> node.floor == 3));
        assertTrue(path.stream().anyMatch(node -> node.floor == 5));
    }

    @Test
    public void missingNodeReturnsEmptyRoute() {
        assertTrue(PathFinder.findPath("missing", "t6-301").isEmpty());
    }
}
