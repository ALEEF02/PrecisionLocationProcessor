package plp.filter;

import java.util.List;

import plp.location.LocationCell;

/*
 * This type of filter can be used at the beginning of a sequence to get points from nothing.
 */
public interface InitialFilter extends Filter {
	
	/**
     * Get the valid cells of the filter.
     * Must not require {@link plp.filter.Filter#setLocations(List)} to be called first.
     * @return List of Cell h3 indexes
     */
    List<LocationCell> getValidCells();
}
