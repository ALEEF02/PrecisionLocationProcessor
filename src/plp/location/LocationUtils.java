package plp.location;

import java.io.IOException;

import com.uber.h3core.H3Core;
import com.uber.h3core.util.LatLng;

public class LocationUtils {
	private static H3Core h3;
	
	/**
     * Initializes the Util with H3
     */
    public static void initialize() {
    	if (h3 != null) return; 
    	try {
			h3 = H3Core.newInstance();
		} catch (IOException e) {
            throw new RuntimeException("Failed to initialize H3 library", e);
		}
    }
    
    /**
     * Get the latitude and longitude of the center of a cell
     * @param LocationCell cell
     * @return The LatLng H3 object for the position of the cell
     */
    public static LatLng getLatLng(LocationCell cell) {
    	if (h3 == null) initialize();
    	return h3.cellToLatLng(cell.getH3Index());
    }
}
