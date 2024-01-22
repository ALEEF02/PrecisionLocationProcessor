package filter;

public class Location {

	public double lat = 0;
	public double lon = 0;
	public double alt = 0;
	
	/**
	 * Instantiate a new, empty Location object
	 */
	public Location() {}
	
	/**
	 * Instantiate a new Location object
	 * @param lat The latitude
	 * @param lon The longitude
	 * @param alt The altitude
	 */
	public Location(double lat, double lon, double alt) {
		this.lat = lat;
		this.lon = lon;
		this.alt = alt;
	}
	
	/**
	 * Instantiate a new Location object
	 * @param lat The latitude
	 * @param lon The longitude
	 */
	public Location(double lat, double lon) {
		this.lat = lat;
		this.lon = lon;
	}
	
}
