package filter;

import java.util.LinkedList;

public interface Condition {

	public Requirements filterRequirements = new Requirements();
	public LinkedList<Location> locations = new LinkedList<Location>();
	
	/**
	 * Input the Condition's requirements
	 * @return the success of the request
	 */
	public boolean setRequirements(Requirements requirements);
	
	/**
	 * Input the Locations for the Condition to filter on
	 * @param locations
	 * @return the success of the request
	 */
	public boolean setLocations(LinkedList<Location> locations);
	
	/**
	 * Load the required data for the Condition
	 * @return
	 * @throws Exception if there is a complete failure loading the data
	 */
	public boolean loadData() throws Exception;
	
	/**
	 * Process the Condition against the locations
	 * @return The list of locations after the condition has been applied
	 */
	public LinkedList<Location> process();
}
