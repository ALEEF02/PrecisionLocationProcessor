package plp.filters;

import java.awt.GridLayout;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import com.uber.h3core.H3Core;
import com.uber.h3core.util.LatLng;

import plp.Config;
import plp.filter.Filter;
import plp.location.LocationCell;

public class BoundingBoxFilter implements Filter {
    private double minLatitude;
    private double maxLatitude;
    private double minLongitude;
    private double maxLongitude;
    private List<Long> validCells;
    private List<LocationCell> locations;
    private H3Core h3;

    public BoundingBoxFilter() {
        try {
            h3 = H3Core.newInstance();
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize H3 library", e);
        }
    }
    
	@Override
	public void setRequirements(JTextField[] requirements) {
        double minLat = Double.parseDouble(requirements[0].getText());
        double maxLat = Double.parseDouble(requirements[1].getText());
        double minLon = Double.parseDouble(requirements[2].getText());
        double maxLon = Double.parseDouble(requirements[3].getText());
    	setRequirements(new double[]{minLat, maxLat, minLon, maxLon});
	}

	@Override
    public void setRequirements(Object requirements) {
        if (requirements instanceof double[]) {
            double[] bounds = (double[]) requirements;
            if (bounds.length == 4) {
                validateBounds(bounds);
                this.minLatitude = bounds[0];
                this.maxLatitude = bounds[1];
                this.minLongitude = bounds[2];
                this.maxLongitude = bounds[3];
                
                validCells = h3.polygonToCells(Arrays.asList(
                		new LatLng(minLatitude, minLongitude),
                		new LatLng(minLatitude, maxLongitude),
            			new LatLng(maxLatitude, maxLongitude),
        				new LatLng(maxLatitude, minLongitude)),
                        null, Config.H3_RESOLUTION);
            } else {
                throw new IllegalArgumentException("Bounding box requires exactly 4 values: [minLat, maxLat, minLon, maxLon]");
            }
        } else {
            throw new IllegalArgumentException("Invalid requirement type for BoundingBoxFilter");
        }
    }
    
    private void validateBounds(double[] bounds) {
        double minLat = bounds[0];
        double maxLat = bounds[1];
        double minLon = bounds[2];
        double maxLon = bounds[3];

        if (minLat < -90 || maxLat > 90 || minLat > maxLat) {
            throw new IllegalArgumentException("Latitude values must be between -90 and 90, with minLat <= maxLat.");
        }

        if (minLon < -180 || maxLon > 180 || minLon > maxLon) {
            throw new IllegalArgumentException("Longitude values must be between -180 and 180, with minLon <= maxLon.");
        }
    }

    public void setLocations(List<LocationCell> locations) {
        this.locations = locations;
    } 

    public List<LocationCell> process() {
        return locations.stream()
                .filter(cell -> validCells.contains(cell.getH3Index()))
                .toList();
    }
    
    /**
     * Get the valid cells of the bounding box
     * @return List of Cell h3 indexes
     */
    public List<LocationCell> getValidCells() {
    	return validCells.stream()
                .map(LocationCell::new)
                .toList();
    }
    
    @Override
    public JPanel getParameterPanel() {
        JPanel panel = new JPanel(new GridLayout(0, 2));
        
        panel.add(new JLabel("Min Latitude:"));
        JTextField minLatField = new JTextField("37.7749");
        panel.add(minLatField);
        
        panel.add(new JLabel("Max Latitude:"));
        JTextField maxLatField = new JTextField("37.8049");
        panel.add(maxLatField);
        
        panel.add(new JLabel("Min Longitude:"));
        JTextField minLonField = new JTextField("-122.4194");
        panel.add(minLonField);
        
        panel.add(new JLabel("Max Longitude:"));
        JTextField maxLonField = new JTextField("-122.3994");
        panel.add(maxLonField);

        panel.putClientProperty("fields", new JTextField[]{minLatField, maxLatField, minLonField, maxLonField});
        return panel;
    }
}