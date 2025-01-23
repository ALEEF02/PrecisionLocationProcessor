package plp.output;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.awt.Desktop;
import java.io.File;

import com.uber.h3core.H3Core;
import com.uber.h3core.util.LatLng;

import de.micromata.opengis.kml.v_2_2_0.Document;
import de.micromata.opengis.kml.v_2_2_0.Kml;
import de.micromata.opengis.kml.v_2_2_0.LinearRing;
import de.micromata.opengis.kml.v_2_2_0.Placemark;
import de.micromata.opengis.kml.v_2_2_0.Polygon;
import plp.location.LocationCell;

public class KMLGenerator {
    public static void generateKML(List<LocationCell> locations, String fileName) {
        System.out.println("Generating KML: " + fileName);
        try {
            H3Core h3 = H3Core.newInstance();
            Kml kml = new Kml();
            Document document = kml.createAndSetDocument().withName("Filtered Hexagons");

            System.out.println("\tAmalgomating...");
            List<List<LatLng>> outerBoundaries = amalgamateHexagons(locations, h3);
            
            // Separate outer rings and holes
            System.out.println("\tAssociating holes...");
            Map<List<LatLng>, List<List<LatLng>>> polygonsWithHoles = detectAndAssociateHoles(outerBoundaries);

            System.out.println("\tDrawing...");
            for (Map.Entry<List<LatLng>, List<List<LatLng>>> entry : polygonsWithHoles.entrySet()) {
                List<LatLng> outerRing = entry.getKey();
                List<List<LatLng>> holes = entry.getValue();

                Placemark placemark = document.createAndAddPlacemark().withName(String.valueOf(outerRing.getFirst()));
                placemark.createAndAddStyle().createAndSetPolyStyle().withColor("aa0000ff"); // Red color in KML (ABGR format)
                Polygon polygon = placemark.createAndSetPolygon();

                // Add outer boundary
                LinearRing outerBoundary = polygon.createAndSetOuterBoundaryIs().createAndSetLinearRing();
                for (LatLng coord : outerRing) {
                    outerBoundary.addToCoordinates(coord.lng, coord.lat, 0);
                }
                // Close the outer ring
                LatLng first = outerRing.get(0);
                outerBoundary.addToCoordinates(first.lng, first.lat, 0);

                // Add holes as inner boundaries
                for (List<LatLng> hole : holes) {
                    LinearRing innerBoundary = polygon.createAndAddInnerBoundaryIs().createAndSetLinearRing();
                    for (LatLng coord : hole) {
                        innerBoundary.addToCoordinates(coord.lng, coord.lat, 0);
                    }
                    // Close the hole ring
                    LatLng holeFirst = hole.get(0);
                    innerBoundary.addToCoordinates(holeFirst.lng, holeFirst.lat, 0);
                }
            }

            kml.marshal(new File(fileName));
            System.out.println("KML file generated: " + fileName);
        } catch (IOException e) {
            System.err.println("Failed to generate KML: " + e.getMessage());
        }
    }
    
    /**
     * Amalgamates connected hexagon cells by merging shared edges and returning outer boundaries.
     *
     * @param hexagonCells List of LocationCells (H3 indexes).
     * @param h3           H3Core instance.
     * @return List of outer boundaries represented as lists of GeoCoords.
     */
    private static List<List<LatLng>> amalgamateHexagons(List<LocationCell> hexagonCells, H3Core h3) {
        // Store the unique edges of all hexagons
        Set<Edge> edgeSet = new HashSet<>();

        for (LocationCell cell : hexagonCells) {
            Long h3Index = cell.getH3Index();
            List<LatLng> boundary = h3.cellToBoundary(h3Index);

            // Loop through vertices to create edges
            for (int i = 0; i < boundary.size(); i++) {
            	LatLng start = boundary.get(i);
            	LatLng end = boundary.get((i + 1) % boundary.size());

                Edge edge = new Edge(start, end);
                if (!edgeSet.remove(edge)) {
                    edgeSet.add(edge);
                }
            }
        }

        // Build polygons from remaining unique edges
        return buildPolygonsFromEdges(edgeSet);
    }

    /**
     * Builds polygons from unique edges by connecting them into rings.
     *
     * @param edges Set of unique edges.
     * @return List of polygons represented as lists of GeoCoords.
     */
    private static List<List<LatLng>> buildPolygonsFromEdges(Set<Edge> edges) {
        List<List<LatLng>> polygons = new ArrayList<>();

        while (!edges.isEmpty()) {
            List<LatLng> polygon = new ArrayList<>();
            Edge currentEdge = edges.iterator().next();
            edges.remove(currentEdge);

            polygon.add(currentEdge.start);
            LatLng nextPoint = currentEdge.end;

            while (!nextPoint.equals(polygon.get(0))) {
                Iterator<Edge> iterator = edges.iterator();
                while (iterator.hasNext()) {
                    Edge edge = iterator.next();
                    if (edge.start.equals(nextPoint)) {
                        polygon.add(edge.start);
                        nextPoint = edge.end;
                        iterator.remove();
                        break;
                    } else if (edge.end.equals(nextPoint)) {
                        polygon.add(edge.end);
                        nextPoint = edge.start;
                        iterator.remove();
                        break;
                    }
                }
            }

            polygons.add(polygon);
        }

        return polygons;
    }
    
    /**
     * Detects and associates holes and islands with their correct parent polygons.
     *
     * @param allBoundaries List of all boundaries (both outer polygons and potential holes).
     * @return A map of outer boundaries to their associated holes and islands.
     */
    private static Map<List<LatLng>, List<List<LatLng>>> detectAndAssociateHoles(List<List<LatLng>> allBoundaries) {
        Map<List<LatLng>, List<List<LatLng>>> result = new LinkedHashMap<>();

        // Step 1: Sort boundaries by size (larger boundaries first)
        allBoundaries.sort((a, b) -> Integer.compare(b.size(), a.size())); // Descending size

        // Step 2: Build a hierarchy of polygons
        List<PolygonNode> nodes = new ArrayList<>();
        for (List<LatLng> boundary : allBoundaries) {
            PolygonNode node = new PolygonNode(boundary);
            for (PolygonNode parent : nodes) {
                if (isPolygonInsidePolygon(node.polygon, parent.polygon)) {
                    parent.children.add(node);
                    node.parent = parent;
                    break;
                }
            }
            if (node.parent == null) { // Top-level outer boundary
                nodes.add(node);
            }
        }

        // Step 3: Convert hierarchy into a map of outer boundaries to holes
        for (PolygonNode node : nodes) {
            result.put(node.polygon, collectHoles(node));
        }

        return result;
    }

    /**
     * Collects all holes (children) under a given polygon node.
     */
    private static List<List<LatLng>> collectHoles(PolygonNode node) {
        List<List<LatLng>> holes = new ArrayList<>();
        for (PolygonNode child : node.children) {
            holes.add(child.polygon); // Child polygons are treated as holes
        }
        return holes;
    }
    
    /**
     * Determines if one polygon is inside another using the point-in-polygon test.
     */
    private static boolean isPolygonInsidePolygon(List<LatLng> inner, List<LatLng> outer) {
        return isPointInsidePolygon(outer, inner.getFirst());
    }

    /**
     * Checks if a point is inside a polygon using the ray-casting algorithm.
     */
    private static boolean isPointInsidePolygon(List<LatLng> polygon, LatLng point) {
        boolean result = false;
        int j = polygon.size() - 1;

        for (int i = 0; i < polygon.size(); i++) {
            if ((polygon.get(i).lat > point.lat) != (polygon.get(j).lat > point.lat) &&
                (point.lng < (polygon.get(j).lng - polygon.get(i).lng) *
                        (point.lat - polygon.get(i).lat) /
                        (polygon.get(j).lat - polygon.get(i).lat) + polygon.get(i).lng)) {
                result = !result;
            }
            j = i;
        }
        return result;
    }

    /**
     * Represents an edge between two GeoCoords, ensuring symmetry.
     */
    private static class Edge {
    	LatLng start, end;

        Edge(LatLng start, LatLng end) {
            this.start = start;
            this.end = end;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            Edge other = (Edge) obj;
            return (start.equals(other.start) && end.equals(other.end)) ||
                   (start.equals(other.end) && end.equals(other.start));
        }

        @Override
        public int hashCode() {
            return start.hashCode() + end.hashCode();
        }
    }
    
    /**
     * A helper class representing a polygon node in the hierarchy.
     */
    private static class PolygonNode {
        List<LatLng> polygon;         // The polygon boundary
        PolygonNode parent;           // Parent polygon
        List<PolygonNode> children;   // Child polygons (holes or islands)

        PolygonNode(List<LatLng> polygon) {
            this.polygon = polygon;
            this.children = new ArrayList<>();
        }
    }
    
    public static void openKMLInGoogleEarth(String fileName) {
        try {
            File kmlFile = new File(fileName);

            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(kmlFile);
                System.out.println("Opening KML file in Google Earth Pro...");
            } else {
                System.out.println("Desktop not supported. Please open " + fileName + " manually.");
            }
        } catch (IOException e) {
            System.err.println("Error opening KML file: " + e.getMessage());
        }
    }
}
