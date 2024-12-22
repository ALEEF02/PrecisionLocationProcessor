package plp.ui;

import org.reflections.Reflections;

import plp.filter.DataFilter;
import plp.filter.Filter;
import plp.filters.BoundingBoxFilter;
import plp.filters.OperatorFilter;
import plp.location.LocationCell;
import plp.operator.LogicalOperator;
import plp.output.KMLGenerator;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;

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

    public FilterUI() throws Exception {
        super("PrecisionLocationProcessor");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        setSize(800, 600);

        loadAvailableFilters(); // Load all filters dynamically

        // UI Components
        filterSelectionBox = new JComboBox<>(availableFilters.keySet().toArray(new String[0]));
        parameterPanel = new JPanel();
        filterListModel = new DefaultListModel<>();
        JList<String> filterList = new JList<>(filterListModel);
        addedFilters = new ArrayList<>();

        JButton addButton = new JButton("Add Filter");
        JButton runButton = new JButton("Run Filters");
        JButton addCompositeButton = new JButton("Add Composite Filter"); // Button to add composite filters
        JButton removeButton = new JButton("Remove Filter");

        // Layout: Top Panel - Filter Selection
        JPanel topPanel = new JPanel();
        topPanel.add(new JLabel("Select Filter:"));
        topPanel.add(filterSelectionBox);
        topPanel.add(addButton);
        topPanel.add(addCompositeButton);
        topPanel.add(removeButton);
        
        // Layout: Center Panel - Parameter input and filter list
        add(topPanel, BorderLayout.NORTH);
        add(parameterPanel, BorderLayout.CENTER);
        add(new JScrollPane(filterList), BorderLayout.EAST);
        add(runButton, BorderLayout.SOUTH);
    	
        filterSelectionBox.addActionListener(e -> {
			try {
				updateParameterPanel();
			} catch (Exception e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
		}); // Load parameter panel dynamically
        
        addButton.addActionListener(e -> {
			try {
				addFilter();
			} catch (Exception e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
		}); // Add filter to the list

        removeButton.addActionListener(e -> {
            int selectedIndex = filterList.getSelectedIndex();
            if (selectedIndex != -1) {
                filterListModel.remove(selectedIndex);
                addedFilters.remove(selectedIndex);
            } else {
                JOptionPane.showMessageDialog(this, "No filter selected to remove.");
            }
        });
        
        addCompositeButton.addActionListener(e -> addCompositeFilter()); // Add composite filters
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
            	if (filterClass.equals(OperatorFilter.class)) continue;
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
     * @throws SecurityException 
     * @throws NoSuchMethodException 
     * @throws InvocationTargetException 
     * @throws IllegalArgumentException 
     * @throws IllegalAccessException 
     * @throws InstantiationException 
     */
    private void updateParameterPanel() throws Exception {
        parameterPanel.removeAll(); // Clear existing components
        String selectedFilter = (String) filterSelectionBox.getSelectedItem();
        Filter filter = availableFilters.get(selectedFilter).getClass().getDeclaredConstructor().newInstance();
        if (filter != null) {
            parameterPanel.add(filter.getParameterPanel()); // Add the filter's parameter UI
        }
        parameterPanel.revalidate();
        parameterPanel.repaint();
    }

    /**
     * Adds a filter instance with user-specified parameters to the pipeline.
     * Validates user input and displays the filter in the configured filter list.
     * @throws SecurityException 
     * @throws NoSuchMethodException 
     * @throws InvocationTargetException 
     * @throws IllegalArgumentException 
     * @throws IllegalAccessException 
     * @throws InstantiationException 
     * 
     */
    private void addFilter() throws Exception {
    		
        String selectedFilter = (String) filterSelectionBox.getSelectedItem();
        Filter filter = availableFilters.get(selectedFilter).getClass().getDeclaredConstructor().newInstance();

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
     * Adds a composite filter with logical operators and sub-filters.
     * Allows the user to set parameters for sub-filters dynamically.
     */
    private void addCompositeFilter() {
        createCompositeFilter().thenAccept(compositeFilter -> {
            if (compositeFilter != null) {
                addedFilters.add(compositeFilter);
                filterListModel.addElement(compositeFilter.getRequirements());
            }
        }).exceptionally(ex -> {
            ex.printStackTrace(); // Log errors
            return null;
        });
    }
    
    /**
     * Adds a composite filter with logical operators and sub-filters.
     * Allows the user to set parameters for sub-filters dynamically.
     */
    private CompletableFuture<OperatorFilter> createCompositeFilter() {
    	
        CompletableFuture<OperatorFilter> future = new CompletableFuture<>();
        
        // Create a dialog to select sub-filters and operators
    	JFrame dialog = new JFrame("Create Composite Filter");
        dialog.setSize(700, 400);
        dialog.setLayout(new BorderLayout());
        dialog.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        DefaultListModel<String> selectedFiltersModel = new DefaultListModel<>();

        // Get operators dynamically from LogicalOperator enum
        JComboBox<String> operatorBox = new JComboBox<>(Arrays.stream(LogicalOperator.values())
                .map(Enum::name).toArray(String[]::new));

        JPanel parameterContainer = new JPanel();
        parameterContainer.setLayout(new BoxLayout(parameterContainer, BoxLayout.Y_AXIS));

        JButton addSubFilterButton = new JButton("Add Sub-Filter");
        addSubFilterButton.addActionListener(e -> {
        	try {
	            String selectedFilter = (String) filterSelectionBox.getSelectedItem();
	            selectedFiltersModel.addElement(selectedFilter);
	            Filter filter = availableFilters.get(selectedFilter).getClass().getDeclaredConstructor().newInstance();
	            if (filter != null) {
	                JPanel subFilterPanel = filter.getParameterPanel();
	                subFilterPanel.setBorder(BorderFactory.createTitledBorder(selectedFilter));
	                parameterContainer.add(subFilterPanel);
	                parameterContainer.revalidate();
	                parameterContainer.repaint();
	            }
        	} catch (Exception ex) {
        		JOptionPane.showMessageDialog(this, "Exception Adding new sub-filter: " + ex.getMessage());
        	}
        });
        
        JButton addNestedCompositeButton = new JButton("Add Nested Composite Filter");
        addNestedCompositeButton.addActionListener(e -> {
        	createCompositeFilter().thenAccept(nestedComposite -> {
                if (nestedComposite != null) {
                    selectedFiltersModel.addElement("Nested Composite Filter (" + nestedComposite.getOperator().toString() + ")");
                    JPanel nestedPanel = nestedComposite.getParameterPanel();
                    nestedPanel.setBorder(BorderFactory.createTitledBorder("Nested Composite Filter (" + nestedComposite.getOperator().toString() + ")"));
                    nestedPanel.putClientProperty("filter", nestedComposite);
                    parameterContainer.add(nestedPanel);
                    parameterContainer.revalidate();
                    parameterContainer.repaint();
                }
            });
        });
        
        JButton createButton = new JButton("Create Composite Filter");
        createButton.addActionListener(e -> {
            String operator = (String) operatorBox.getSelectedItem();
            OperatorFilter compositeFilter = new OperatorFilter();
            compositeFilter.setRequirements(LogicalOperator.valueOf(operator));

            for (Component component : parameterContainer.getComponents()) {
                if (component instanceof JPanel panel) {
                    Object filterProperty = panel.getClientProperty("filter");

                    if (filterProperty instanceof OperatorFilter nestedComposite) {
                        compositeFilter.addFilter(nestedComposite);
                    } else {
                    	try {
	                        String filterName = selectedFiltersModel.getElementAt(parameterContainer.getComponentZOrder(panel));
	                        Filter subFilter = availableFilters.get(filterName).getClass().getDeclaredConstructor().newInstance();
	
	                        if (subFilter != null) {
	                            try {
	                                subFilter.setRequirements(panel);
	                                compositeFilter.addFilter(subFilter);
	                            } catch (Exception ex) {
	                                JOptionPane.showMessageDialog(this, "Invalid input for sub-filter " + filterName + ": " + ex.getMessage());
	                                future.completeExceptionally(ex); // Handle exceptions
	                                return;
	                            }
	                        }
                    	} catch (Exception ex) {
                            JOptionPane.showMessageDialog(this, "Exception creating composite filter: " + ex.getMessage());
                    	}
                    }
	            }
            }

            future.complete(compositeFilter); // Complete the future
            dialog.dispose();
        });

        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> {
            future.complete(null); // Signal cancellation
            dialog.dispose();
        });

        JPanel buttonPanel = new JPanel();
        buttonPanel.add(addSubFilterButton);
        buttonPanel.add(addNestedCompositeButton);
        buttonPanel.add(createButton);
        buttonPanel.add(cancelButton);

        dialog.add(operatorBox, BorderLayout.NORTH);
        dialog.add(new JScrollPane(parameterContainer), BorderLayout.CENTER);
        dialog.add(buttonPanel, BorderLayout.SOUTH);

        dialog.setVisible(true);
        return future; // Return the constructed composite filter
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
        SwingUtilities.invokeLater(() -> {
			try {
				new FilterUI().setVisible(true);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		});
    }
}

