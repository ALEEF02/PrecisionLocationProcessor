package plp.filter;

import java.util.List;


import plp.filters.BoundingBoxFilter;
import plp.location.LocationCell;

public class DataFilter {
    private final FilterManager filterManager = new FilterManager();
    private final List<LocationCell> allCells;

    public DataFilter(InitialFilter initialFilter) {
        allCells = initialFilter.getValidCells();
        if (allCells.isEmpty()) {
        	throw new IllegalArgumentException("Zero cells in the initialFilter: " + initialFilter.getClass().getSimpleName());
        }
    }
    

    public void addFilter(Filter filter) {
        filterManager.addFilter(filter);
    }

    public List<LocationCell> filterLocations() {
        return filterManager.applyFilters(allCells);
    }
}