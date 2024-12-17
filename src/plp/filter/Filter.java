package plp.filter;

import java.util.List;

import javax.swing.JPanel;
import javax.swing.JTextField;

import plp.location.LocationCell;

public interface Filter {
    void setRequirements(Object requirements); // Main Handling
    void setRequirements(JTextField[] requirements); // Handle from UI
    void setLocations(List<LocationCell> locations); // Initial locations to filter on
    List<LocationCell> process(); // Action of filtering the locations
    
    // Method to define UI components for filter parameters
    default JPanel getParameterPanel() {
    	JPanel panel = new JPanel();
        panel.putClientProperty("fields", new JTextField[]{});
        return panel;
    }
}
