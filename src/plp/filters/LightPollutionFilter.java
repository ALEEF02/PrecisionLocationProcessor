package plp.filters;

import java.awt.GridLayout;
import java.util.List;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import plp.filter.Filter;
import plp.location.LocationCell;

public class LightPollutionFilter implements Filter {
    private double minSQM;
    private List<LocationCell> locations;
    
	@Override
	public void setRequirements(JPanel modifiedParameterPanel) throws IllegalArgumentException {
		JTextField[] fields = (JTextField[]) modifiedParameterPanel.getClientProperty("fields");
        double minLat = Double.parseDouble(fields[0].getText());
    	setRequirements(minLat);
	}

	@Override
    public void setRequirements(Object requirements) throws IllegalArgumentException {
        if (requirements instanceof Double) {
            this.minSQM = (Double) requirements;
        } else {
            throw new IllegalArgumentException("Invalid requirement type for LightPollutionFilter");
        }
    }

	@Override
    public void setLocations(List<LocationCell> locations) {
        this.locations = locations;
    }

    @Override
    public List<LocationCell> process() {
        // Simulate filtering locations based on light pollution
        return locations.stream()
                .filter(location -> Math.random() * 50 > minSQM) // Mock filtering logic
                .toList();
    }
    
    @Override
    public JPanel getParameterPanel() {
        JPanel panel = new JPanel(new GridLayout(1, 2));
        panel.add(new JLabel("Min SQM:"));
        JTextField minSQMField = new JTextField();
        panel.add(minSQMField);
        panel.putClientProperty("fields", new JTextField[]{minSQMField});
        return panel;
    }

	@Override
	public String getRequirements() {
		return "Minimum SQM: " + String.valueOf(minSQM);
	}
}
