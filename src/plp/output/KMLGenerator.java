package plp.output;

import java.io.IOException;
import java.util.List;
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
        try {
            H3Core h3 = H3Core.newInstance();
            Kml kml = new Kml();
            Document document = kml.createAndSetDocument().withName("Filtered Hexagons");

            for (LocationCell cell : locations) {
                List<LatLng> boundary = h3.cellToBoundary(cell.getH3Index());
                System.out.println(boundary);
                Placemark placemark = document.createAndAddPlacemark().withName(String.valueOf(cell.getH3Index()));
                placemark.createAndAddStyle().createAndSetPolyStyle().withColor("ff0000ff"); // Red color in KML (ABGR format)
                Polygon polygon = placemark.createAndSetPolygon();
                LinearRing ring = polygon.createAndSetOuterBoundaryIs().createAndSetLinearRing();
                for (LatLng coord : boundary) {
                	ring.addToCoordinates(coord.lng, coord.lat, 0);
                }
                // Close the polygon
                LatLng first = boundary.get(0);
            	ring.addToCoordinates(first.lng, first.lat, 0);
            }

            kml.marshal(new File(fileName));
            System.out.println("KML file generated: " + fileName);
        } catch (IOException e) {
            System.err.println("Failed to generate KML: " + e.getMessage());
        }
    }
}
