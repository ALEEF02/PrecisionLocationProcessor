package plp.filters;

import java.awt.Component;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BoxLayout;
import javax.swing.JPanel;

import plp.filter.Filter;
import plp.location.LocationCell;
import plp.operator.LogicalOperator;
import plp.operator.OperatorFactory;

public class OperatorFilter implements Filter {
	private LogicalOperator operator;
    private final List<Filter> subFilters = new ArrayList<>();
    private List<LocationCell> locations;

    public OperatorFilter() {}

    public void addFilter(Filter filter) {
        subFilters.add(filter);
    }

    @Override
    public void setRequirements(Object requirements) {
    	if (requirements instanceof LogicalOperator) {
            this.operator = (LogicalOperator) requirements;
        } else {
            throw new IllegalArgumentException("Invalid requirement type for LightPollutionFilter");
        }
    }

    @Override
    public void setLocations(List<LocationCell> locations) {
        for (Filter filter : subFilters) {
            filter.setLocations(locations);
        }
        this.locations = locations;
    }

    @Override
    public List<LocationCell> process() {
        if (subFilters.isEmpty()) return List.of();
        
        List<LocationCell> result;
        switch (operator) {
	        case OR -> result = new ArrayList<LocationCell>();
	        case NOT -> result = locations;
	        case XOR -> result = new ArrayList<LocationCell>();
	        default -> throw new IllegalArgumentException("Unsupported operator: " + operator);
	    }

        for (int i = 0; i < subFilters.size(); i++) {
            List<LocationCell> nextResult = subFilters.get(i).process();

            switch (operator) {
                case OR -> result = OperatorFactory.applyOr(result, nextResult);
                case NOT -> result = OperatorFactory.applyNot(locations, nextResult);
                case XOR -> result = OperatorFactory.applyExclusiveOr(result, nextResult);
                default -> throw new IllegalArgumentException("Unsupported operator: " + operator);
            }
        }
        return result;
    }

    @Override
    public JPanel getParameterPanel() {
        JPanel allParametersPanel = new JPanel();
        allParametersPanel.setLayout(new BoxLayout(allParametersPanel, BoxLayout.X_AXIS));
        for (Filter filter : subFilters) {
    		JPanel panel = filter.getParameterPanel();
    		Component[] comps = panel.getComponents();
    		for (Component comp : comps) {
    			comp.setEnabled(false);
    		}
    		allParametersPanel.add(panel);
    	}
        return allParametersPanel;
    }

    @Override
    public String getRequirements() {
    	String filterDetails = "";
    	for (Filter filter : subFilters) {
    		filterDetails += filter.getClass().getSimpleName() + ": " + filter.getRequirements() + ",";
    	}
    	filterDetails = filterDetails.substring(0, filterDetails.length() - 1);
        return operator + "[" + filterDetails + "]";
    }
    
    public LogicalOperator getOperator() {
    	return operator;
    }
}
