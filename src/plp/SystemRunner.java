package plp;

import java.util.List;

import plp.filter.DataFilter;
import plp.filters.*;
import plp.location.LocationCell;
import plp.output.KMLGenerator;

public class SystemRunner {
	public static void main(String[] args) {

        // Create Filters
        LightPollutionFilter lightPollutionFilter = new LightPollutionFilter();
        lightPollutionFilter.setRequirements(20.0); // Minimum SQM value

        BoundingBoxFilter boundingBoxFilter = new BoundingBoxFilter();
        boundingBoxFilter.setRequirements(new double[]{37.7749, 37.8049, -122.4194, -122.3994}); // San Francisco bounding box

        // Use DataFilter
        DataFilter dataFilter = new DataFilter(boundingBoxFilter);
        dataFilter.addFilter(lightPollutionFilter);

        // Apply Filters
        List<LocationCell> filteredLocations = dataFilter.filterLocations();

        // Print Results
        System.out.println("Filtered Locations: " + filteredLocations);
        
        // Generate KML file with hexagon boundaries
        String kmlFileName = "filtered_hexagons.kml";
        KMLGenerator.generateKML(filteredLocations, kmlFileName);
        
    }
}
