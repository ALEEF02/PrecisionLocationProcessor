package plp.filter;

import java.util.Arrays;
import java.util.List;

import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

import plp.location.LocationCell;

public interface Filter {
	
	/**
	 * Set the requirements from the CLI or other source.
	 * Must detect the wanted Object type and handle accordingly.
	 * @param requirements The filter's requirements in it's wanted type.
	 * @throws IllegalArgumentException If object is of wrong type or arguments are invalid
	 */
    void setRequirements(Object requirements) throws IllegalArgumentException; // Main Handling
    
    /**
     * A String representation of the Filter's currently inputed requirements
     * @return A user-friendly string
     */
    String getRequirements();
    
    /**
     * Set the initial locations to filter upon
     * @param Previous {@link plp.location.LocationCell LocationCells} to work with
     */
    void setLocations(List<LocationCell> locations);
    
    /**
     * The action of filtering the locations.
     * Should handle loading data in and caching, if necessary.
     * @return All matching {@link plp.location.LocationCell LocationCells}
     */
    List<LocationCell> process(); // Action of filtering the locations
    
    /**
     * Accept requirements from the Parameter Panel that this filter provides
     * @param modifiedParameterPanel The panel from {@link #getParameterPanel(int, int) getParameterPanel}, modified with the user's input.
	 * @throws IllegalArgumentException If arguments are invalid.
     */
    default void setRequirements(JPanel modifiedParameterPanel) throws IllegalArgumentException { // Handle from UI
    	JTextField[] fields = (JTextField[]) modifiedParameterPanel.getClientProperty("fields"); // Get the first component (the filter's parameter panel), Extract input fields

        try {
        	String[] inputValues = new String[fields.length];
            for (int i = 0; i < fields.length; i++) {
                // Explicitly focus out of each field to ensure the latest value is captured
                fields[i].transferFocus();
                inputValues[i] = fields[i].getText();
            }

            System.out.println(Arrays.toString(inputValues));
            setRequirements(fields); // Set the requirements dynamically
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(modifiedParameterPanel, "Invalid input: " + ex.getMessage());
        }
    }
    
    /**
     * Construct a JPanel for the UI so that the user can input the requirements for this filter.
     * @return A panel with fields, such as {@link javax.swing.JTextField JTextFields}
     */
    default JPanel getParameterPanel() {
    	JPanel panel = new JPanel();
        panel.putClientProperty("fields", new JTextField[]{});
        return panel;
    }
}
