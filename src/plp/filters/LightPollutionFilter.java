package plp.filters;

import java.util.List;

import plp.filter.Filter;
import plp.location.LocationCell;

public class LightPollutionFilter implements Filter {
    private double minSQM;
    private List<LocationCell> locations;

    public void setRequirements(Object requirements) {
        if (requirements instanceof Double) {
            this.minSQM = (Double) requirements;
        } else {
            throw new IllegalArgumentException("Invalid requirement type for LightPollutionFilter");
        }
    }

    public void setLocations(List<LocationCell> locations) {
        this.locations = locations;
    }

    public List<LocationCell> process() {
        // Simulate filtering locations based on light pollution
        return locations.stream()
                .filter(location -> Math.random() * 50 > minSQM) // Mock filtering logic
                .toList();
    }
}
