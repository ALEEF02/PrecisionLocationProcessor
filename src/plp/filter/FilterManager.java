package plp.filter;

import java.util.ArrayList;
import java.util.List;

import plp.location.LocationCell;

public class FilterManager {
    private final List<Filter> filters = new ArrayList<>();

    public void addFilter(Filter filter) {
        filters.add(filter);
    }

    public List<LocationCell> applyFilters(List<LocationCell> locations) {
        List<LocationCell> filteredLocations = locations;

        for (Filter filter : filters) {
            filter.setLocations(filteredLocations);
            filteredLocations = filter.process();
        }

        return filteredLocations;
    }
}
