package plp.ui;

import org.reflections.Reflections;

import plp.filter.DataFilter;
import plp.filter.Filter;
import plp.filters.BoundingBoxFilter;
import plp.location.LocationCell;
import plp.output.KMLGenerator;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.List;

/**
 * FilterUI - A dynamic UI for managing and configuring filters.
 * 
 * This class provides a Swing-based graphical user interface that dynamically loads
 * filters from the "filters" package, renders appropriate parameter input components,
 * allows users to configure the filters, and executes the filter pipeline to generate
 * a KML file.
 * 
 * Key Features:
 * - Dynamic filter discovery using reflection.
 * - Custom parameter input components for each filter.
 * - Visual list of configured filters.
 * - Execution of filters and KML generation.
 */
public class FilterUI extends JFrame {
    private JComboBox<String> filterSelectionBox;
    private JPanel parameterPanel;
    private DefaultListModel<String> filterListModel;
    private ArrayList<Filter> addedFilters;
    private Map<String, Filter> availableFilters;

    public FilterUI() {
        super("Location Filter System");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        setSize(600, 400);

        loadAvailableFilters(); // Load all filters dynamically

        // UI Components
        filterSelectionBox = new JComboBox<>(availableFilters.keySet().toArray(new String[0]));
        parameterPanel = new JPanel();
        filterListModel = new DefaultListModel<>();
        JList<String> filterList = new JList<>(filterListModel);
        addedFilters = new ArrayList<>();

        JButton addButton = new JButton("Add Filter");
        JButton runButton = new JButton("Run Filters");

        // Layout: Top Panel - Filter Selection
        JPanel topPanel = new JPanel();
        topPanel.add(new JLabel("Select Filter:"));
        topPanel.add(filterSelectionBox);
        topPanel.add(addButton);
        
        // Layout: Center Panel - Parameter input and filter list
        add(topPanel, BorderLayout.NORTH);
        add(parameterPanel, BorderLayout.CENTER);
        add(new JScrollPane(filterList), BorderLayout.EAST);
        add(runButton, BorderLayout.SOUTH);
    	
        filterSelectionBox.addActionListener(e -> updateParameterPanel()); // Load parameter panel dynamically
        addButton.addActionListener(e -> addFilter()); // Add filter to the list
        runButton.addActionListener(e -> runFilters()); // Execute pipeline and generate KML

        updateParameterPanel(); // Initialize with the first filter's parameters
    }

    /**
     * Dynamically loads all classes in the "filters" package that implement the Filter interface.
     * Populates the availableFilters map with filter names and instances.
     */
    private void loadAvailableFilters() {
        availableFilters = new HashMap<>();
        Reflections reflections = new Reflections("plp.filters");
        Set<Class<? extends Filter>> classes = reflections.getSubTypesOf(Filter.class);

        for (Class<? extends Filter> filterClass : classes) {
            try {
                if (!Modifier.isAbstract(filterClass.getModifiers())) {
                    Filter filter = filterClass.getDeclaredConstructor().newInstance();
                    availableFilters.put(filterClass.getSimpleName(), filter);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Updates the parameter input panel to match the selected filter.
     * This dynamically renders the input fields defined by the selected filter.
     */
    private void updateParameterPanel() {
        parameterPanel.removeAll(); // Clear existing components
        String selectedFilter = (String) filterSelectionBox.getSelectedItem();
        Filter filter = availableFilters.get(selectedFilter);
        if (filter != null) {
            parameterPanel.add(filter.getParameterPanel()); // Add the filter's parameter UI
        }
        parameterPanel.revalidate();
        parameterPanel.repaint();
    }

    /**
     * Adds a filter instance with user-specified parameters to the pipeline.
     * Validates user input and displays the filter in the configured filter list.
     * 
     */
    private void addFilter() {
    		
        String selectedFilter = (String) filterSelectionBox.getSelectedItem();
        Filter filter = availableFilters.get(selectedFilter);

        if (filter != null) {
            try {
            	JPanel filterParameterPanel = (JPanel) parameterPanel.getComponent(0);
                filter.setRequirements(filterParameterPanel); // Set the requirements dynamically
                addedFilters.add(filter); // Add filter instance to the list
                filterListModel.addElement(selectedFilter + ": " + filter.getRequirements());
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(rootPane, "Invalid input: " + ex.getMessage());
            }
        }
    }

    /**
     * Executes the filter pipeline, applies all configured filters, and generates a KML file.
     * Displays a success message upon completion.
     */
    private void runFilters() {
    	DataFilter dataFilter = null;
    	BoundingBoxFilter initialBounds = null;

        // Find the first BoundingBoxFilter in the list
        for (Filter filter : addedFilters) {
            if (filter instanceof BoundingBoxFilter) {
            	initialBounds = (BoundingBoxFilter) filter;
                dataFilter = new DataFilter(initialBounds);
                break;
            }
        }

        if (dataFilter == null) {
            JOptionPane.showMessageDialog(this, "Error: A BoundingBoxFilter is required to start the pipeline.",
                    "Missing BoundingBoxFilter", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Add all configured filters to the pipeline
        for (Filter filter : addedFilters) {
        	if (filter.equals(initialBounds)) continue;
            dataFilter.addFilter(filter);
        }
        
        // Run the filters and generate KML
        List<LocationCell> filteredLocations = dataFilter.filterLocations();
        KMLGenerator.generateKML(filteredLocations, "ui_filtered_hexagons.kml");
        KMLGenerator.openKMLInGoogleEarth("ui_filtered_hexagons.kml");
        JOptionPane.showMessageDialog(this, "Filters applied! KML file generated: ui_filtered_hexagons.kml");
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new FilterUI().setVisible(true));
    }
}

