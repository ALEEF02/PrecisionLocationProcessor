package plp;

import java.util.List;

import plp.filter.DataFilter;
import plp.filters.*;
import plp.location.LocationCell;
import plp.operator.LogicalOperator;
import plp.output.KMLGenerator;

public class SystemRunner {
	public static void main(String[] args) {

        // Create Filters
        LightPollutionFilter lightPollutionFilter = new LightPollutionFilter();
        lightPollutionFilter.setRequirements(21.65); // Minimum SQM value
        
        LightPollutionFilter lightPollutionFilterNot = new LightPollutionFilter();
        lightPollutionFilterNot.setRequirements(21.6); // Minimum SQM value

        BoundingBoxFilter boundingBoxFilter = new BoundingBoxFilter();
        boundingBoxFilter.setRequirements(new double[]{33.5, 34.2, -116.5, -115.0}); // Desert bounding box

        // Use DataFilter
        DataFilter dataFilter = new DataFilter(boundingBoxFilter);
        
        // SQM less than 17.9
        OperatorFilter notFilter = new OperatorFilter();
        notFilter.setRequirements(LogicalOperator.NOT);
        notFilter.addFilter(lightPollutionFilterNot);
        
        // SQM less than 17.9 or greater than 18.1
        OperatorFilter orFilter = new OperatorFilter();
        orFilter.setRequirements(LogicalOperator.OR);
        orFilter.addFilter(notFilter);
        orFilter.addFilter(lightPollutionFilter);
        dataFilter.addFilter(orFilter);
        

        // Apply Filters
        List<LocationCell> filteredLocations = dataFilter.filterLocations();

        // Print Results
        System.out.println("Filtered Locations: " + filteredLocations);
        
        // Generate KML file with hexagon boundaries
        String kmlFileName = "filtered_hexagons.kml";
        KMLGenerator.generateKML(filteredLocations, kmlFileName);
        
    }
}
