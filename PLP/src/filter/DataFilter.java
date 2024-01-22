package filter;

import java.util.ArrayList;
import java.util.LinkedList;

public class DataFilter {

	public static LinkedList<Location> locations = new LinkedList<Location>();
	private static ArrayList<Condition> conditions = new ArrayList<Condition>();
	
	public DataFilter() {
		
	}
	
	public void addCondition(Condition condition) {
		conditions.add(condition);
	}
	
	public LinkedList<Location> process() throws Exception {
		for (Condition condition : conditions) {
			condition.setLocations(locations);
			condition.loadData();
			locations = condition.process();
		}
		return locations;
	}
	
	public boolean convertToKML() {
		return false;
	}
	
}
