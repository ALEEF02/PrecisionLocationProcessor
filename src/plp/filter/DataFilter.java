package plp.filter;

import java.util.List;


import plp.filters.BoundingBoxFilter;
import plp.location.LocationCell;

public class DataFilter {
    private final FilterManager filterManager = new FilterManager();
    private final List<LocationCell> allCells;

    public DataFilter(BoundingBoxFilter boundingBoxFilter) {
        allCells = boundingBoxFilter.getValidCells();
        if (allCells.isEmpty()) {
        	throw new IllegalArgumentException("Zero cells in the boundingBoxFilter");
        }
    }
    

    public void addFilter(Filter filter) {
        filterManager.addFilter(filter);
    }

    public List<LocationCell> filterLocations() {
    	System.out.println(allCells.size());
        return filterManager.applyFilters(allCells);
    }
}