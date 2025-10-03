package plp.ui;

import org.reflections.Reflections;

import com.kaaz.configuration.ConfigurationBuilder;

import plp.config.Config;
import plp.config.ConfigurationManager;
import plp.filter.DataFilter;
import plp.filter.Filter;
import plp.filter.InitialFilter;
import plp.filters.OperatorFilter;
import plp.location.LocationCell;
import plp.operator.LogicalOperator;
import plp.output.KMLGenerator;

import javax.swing.*;
import com.formdev.flatlaf.FlatLightLaf;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.File;
import java.lang.reflect.Field;
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
    private JLabel statusLabel;

    public FilterUI() throws Exception {
        super("PrecisionLocationProcessor");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        setSize(1000, 700);
        setMinimumSize(new Dimension(800, 600));
        setLocationRelativeTo(null); // Center on screen
        
        // Progress bar setup
        JProgressBar progressBar = new JProgressBar();
        progressBar.setStringPainted(true);
        progressBar.setString("Loading filters...");
        add(progressBar, BorderLayout.SOUTH);

        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                loadAvailableFilters(progressBar); // Load all filters dynamically
                return null;
            }

            @Override
            protected void done() {
                remove(progressBar);
                initializeUIComponents();
                revalidate();
                repaint();
            }
        };

        worker.execute();

        
    }
    
    private void initializeUIComponents() {
    	// UI Components
        filterSelectionBox = new JComboBox<>(availableFilters.keySet().toArray(new String[0]));
        parameterPanel = new JPanel(new BorderLayout());
        parameterPanel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        filterListModel = new DefaultListModel<>();
        JList<String> filterList = new JList<>(filterListModel) {
        	@Override
        	public boolean getScrollableTracksViewportWidth() {
        		return true; // Make the list width track the viewport width so wrapping updates on resize
        	}
        };
        filterList.setVisibleRowCount(-1);
        filterList.setCellRenderer(new ListCellRenderer<String>() {
        	private final JTextArea textArea;
        	{
        		textArea = new JTextArea();
        		textArea.setLineWrap(true);
        		textArea.setWrapStyleWord(true);
        		textArea.setOpaque(true);
        		textArea.setFont(new Font("Dialog", Font.PLAIN, 11));
        		textArea.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        	}
            @Override
            public Component getListCellRendererComponent(JList<? extends String> list, String value, int index, boolean isSelected, boolean cellHasFocus) {
                textArea.setText(value == null ? "" : value);
                int width = list.getWidth();
                if (width > 0) {
                	textArea.setSize(width, Short.MAX_VALUE);
                }
                if (isSelected) {
                	textArea.setBackground(list.getSelectionBackground());
                	textArea.setForeground(list.getSelectionForeground());
                } else {
                	textArea.setBackground(list.getBackground());
                	textArea.setForeground(list.getForeground());
                }
                return textArea;
            }
        });

        // Force height recomputation on resize so wrapping updates immediately
        filterList.addComponentListener(new ComponentAdapter() {
        	@Override
        	public void componentResized(ComponentEvent e) {
        		int old = filterList.getFixedCellHeight();
        		filterList.setFixedCellHeight(1);
        		filterList.setFixedCellHeight(old);
        		filterList.revalidate();
        		filterList.repaint();
        	}
        });
        
        addedFilters = new ArrayList<>();

        JButton addButton = new JButton("Add");
        JButton addCompositeButton = new JButton("Add Composite");
        JButton removeButton = new JButton("Remove");
        JButton settingsButton = new JButton("\u2699"); // Unicode for gear symbol
        settingsButton.setFont(new Font("Dialog", Font.PLAIN, 16));
        settingsButton.setToolTipText("Open Settings");
        JButton runButton = new JButton("Run");

        // Toolbar (North)
        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);
        toolBar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(226, 232, 240)),
            BorderFactory.createEmptyBorder(8, 12, 8, 12)
        ));
        
        JLabel filterLabel = new JLabel("Filter:");
        filterLabel.setFont(filterLabel.getFont().deriveFont(Font.BOLD, 12));
        toolBar.add(filterLabel);
        toolBar.add(Box.createHorizontalStrut(8));
        toolBar.add(filterSelectionBox);
        toolBar.add(Box.createHorizontalStrut(8));
        toolBar.addSeparator();
        toolBar.add(Box.createHorizontalStrut(8));
        toolBar.add(addButton);
        toolBar.add(Box.createHorizontalStrut(4));
        toolBar.add(addCompositeButton);
        toolBar.add(Box.createHorizontalStrut(4));
        toolBar.add(removeButton);
        toolBar.add(Box.createHorizontalGlue());
        toolBar.add(settingsButton);
        add(toolBar, BorderLayout.NORTH);

        // Right sidebar with fixed preferred width
        JScrollPane listScroll = new JScrollPane(filterList) {
        	@Override
        	public Dimension getPreferredSize() {
        		// Keep a sensible default width while allowing expansion
        		Dimension d = super.getPreferredSize();
        		if (d.width < 260) d.width = 260;
        		return d;
        	}
        };
        listScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        listScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        JPanel addedFiltersPanel = new JPanel(new BorderLayout());
        addedFiltersPanel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(new Color(226, 232, 240), 1),
            "Added Filters",
            0, 0, new Font("Dialog", Font.BOLD, 12)
        ));
        addedFiltersPanel.add(listScroll, BorderLayout.CENTER);
        addedFiltersPanel.setPreferredSize(new Dimension(260, 0));
        addedFiltersPanel.setMinimumSize(new Dimension(260, 0));
        addedFiltersPanel.setMaximumSize(new Dimension(260, Integer.MAX_VALUE));

        // Split pane between parameter center and right sidebar
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        split.setLeftComponent(parameterPanel);
        split.setRightComponent(addedFiltersPanel);
        split.setResizeWeight(1.0);
        split.setDividerSize(6);
        add(split, BorderLayout.CENTER);

        // Status bar (South)
        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(226, 232, 240)),
            BorderFactory.createEmptyBorder(8, 12, 8, 12)
        ));
        statusLabel = new JLabel("Ready");
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, 11));
        statusBar.add(statusLabel, BorderLayout.WEST);
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        runButton.setFont(runButton.getFont().deriveFont(Font.BOLD, 12));
        actionPanel.add(runButton);
        statusBar.add(actionPanel, BorderLayout.EAST);
        add(statusBar, BorderLayout.SOUTH);
    	
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

        runButton.addActionListener(e -> { // Execute pipeline and generate KML
        	statusLabel.setText("Running filters...");
        	runFilters();
        	statusLabel.setText("Completed. KML generated.");
        });
        
        settingsButton.addActionListener(e -> {
            JDialog settingsDialog = new JDialog(this, "Settings", true);
            settingsDialog.setSize(400, 300);
            settingsDialog.setMinimumSize(new Dimension(400, 300));
            settingsDialog.setLayout(new BorderLayout());

            JPanel configPanel = new JPanel();
            configPanel.setLayout(new BoxLayout(configPanel, BoxLayout.Y_AXIS));

            // Use reflection to get all Config variables
            Field[] fields = Config.class.getDeclaredFields();
            Map<Field, JTextField> fieldInputs = new HashMap<>();
            
            for (Field field : fields) {
                if (Modifier.isStatic(field.getModifiers())) {
                    JLabel fieldLabel = new JLabel(field.getName() + ":");
                    JTextField valueField = new JTextField();

                    // Pre-fill the current value
                    try {
                        field.setAccessible(true);
                        Object value = field.get(null);
                        valueField.setText(String.valueOf(value));
                    } catch (IllegalAccessException ex) {
                        valueField.setText("Error");
                    }

                    JPanel fieldPanel = new JPanel(new BorderLayout());
                    fieldPanel.add(fieldLabel, BorderLayout.WEST);
                    fieldPanel.add(valueField, BorderLayout.CENTER);

                    configPanel.add(fieldPanel);
                    fieldInputs.put(field, valueField);
                }
            }

            JScrollPane scrollPane = new JScrollPane(configPanel);
            settingsDialog.add(scrollPane, BorderLayout.CENTER);

            JPanel buttonPanel = new JPanel();
            JButton discardButton = new JButton("Discard");
            discardButton.addActionListener(evt -> settingsDialog.dispose());

            JButton saveButton = new JButton("Save");
            saveButton.addActionListener(evt -> {
                try {
                    for (Map.Entry<Field, JTextField> entry : fieldInputs.entrySet()) {
                        Field field = entry.getKey();
                        JTextField valueField = entry.getValue();
                        String textValue = valueField.getText();

                        if (field.getType() == int.class) {
                            field.setInt(null, Integer.parseInt(textValue));
                        } else if (field.getType() == double.class) {
                            field.setDouble(null, Double.parseDouble(textValue));
                        } else if (field.getType() == boolean.class) {
                            field.setBoolean(null, Boolean.parseBoolean(textValue));
                        } else {
                            field.set(null, textValue);
                        }
                    }
                    refreshInitialFilters();
                    try {
                        new ConfigurationManager(Config.class, new File("application.cfg")).write();
                    } catch (Exception e2) {
                        JOptionPane.showMessageDialog(settingsDialog, "Failed to write configuration: " + e2.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                    }
                    JOptionPane.showMessageDialog(settingsDialog, "Settings saved successfully.", "Success", JOptionPane.INFORMATION_MESSAGE);
                    settingsDialog.dispose();
                } catch (IllegalAccessException | IllegalArgumentException ex) {
                    JOptionPane.showMessageDialog(settingsDialog, "Failed to save settings: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            });

            buttonPanel.add(discardButton);
            buttonPanel.add(saveButton);

            settingsDialog.add(buttonPanel, BorderLayout.SOUTH);
            settingsDialog.setLocationRelativeTo(this);
            settingsDialog.setVisible(true);
        });

        try {
			updateParameterPanel();
		} catch (Exception e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} // Initialize with the first filter's parameters
    }

    /**
     * Dynamically loads all classes in the "filters" package that implement the Filter interface.
     * Populates the availableFilters map with filter names and instances.
     */
    private void loadAvailableFilters(JProgressBar progressBar) {
        availableFilters = new HashMap<>();
        Reflections reflections = new Reflections("plp.filters");
        Set<Class<? extends Filter>> classes = reflections.getSubTypesOf(Filter.class);
        classes.removeIf(filterClass -> filterClass.equals(OperatorFilter.class) || Modifier.isAbstract(filterClass.getModifiers()));

        int totalClasses = classes.size();
        int progress = 0;

        for (Class<? extends Filter> filterClass : classes) {
            progressBar.setString("Loading filters... " + (progress+1) + "/" + totalClasses + "  " + filterClass.getSimpleName());
            try {
                Filter filter = filterClass.getDeclaredConstructor().newInstance();
                availableFilters.put(filterClass.getSimpleName(), filter);
            } catch (Exception e) {
                e.printStackTrace();
            }

            progress++;
            int progressPercentage = (int) ((progress / (double) totalClasses) * 100);
            progressBar.setValue(progressPercentage);
        }

        progressBar.setIndeterminate(false);
        progressBar.setValue(100);
        progressBar.setString("Filters loaded.");
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
            JPanel filterPanel = filter.getParameterPanel(); // Get the filter's supplied parameter panel
            // Apply consistent styling to filter parameter panels
            filterPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(
                    BorderFactory.createLineBorder(new Color(226, 232, 240), 1),
                    filter.getClass().getSimpleName(),
                    0, 0, new Font("Dialog", Font.BOLD, 12)
                ),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)
            ));
            parameterPanel.add(filterPanel); // Add the filter's parameter UI
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
                JOptionPane.showMessageDialog(rootPane, "Invalid input: " + ex.getMessage(), null, JOptionPane.WARNING_MESSAGE);
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
     * Finish creating the composite filter with logical operators and sub-filters.
     * Allows the user to set parameters for sub-filters dynamically.
     */
    private CompletableFuture<OperatorFilter> createCompositeFilter() {
    	
        CompletableFuture<OperatorFilter> future = new CompletableFuture<>();
        
        // Create a dialog to select sub-filters and operators
    	JFrame dialog = new JFrame("Create Composite Filter");
        dialog.setSize(700, 800);
        dialog.setMinimumSize(new Dimension(600, 600));
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
       
    /*
     * Refresh valid cells for initial filters.
     * Necessary when H3 resolution changes.
     */
    private void refreshInitialFilters() {
    	for (Filter filter : addedFilters) {
            if (filter instanceof InitialFilter) {
            	((InitialFilter) filter).refreshValidCells();
            }
        }
    }

    /**
     * Executes the filter pipeline, applies all configured filters, and generates a KML file.
     * Displays a success message upon completion.
     */
    private void runFilters() {
    	DataFilter dataFilter = null;
    	InitialFilter initialBounds = null;

        // Find the first InitialFilter in the list
        for (Filter filter : addedFilters) {
            if (filter instanceof InitialFilter) {
            	initialBounds = (InitialFilter) filter;
                dataFilter = new DataFilter(initialBounds);
                break;
            }
        }

        if (dataFilter == null) {
            JOptionPane.showMessageDialog(this, "Error: An InitialFilter is required to start the pipeline.",
                    "Missing InitialFilter", JOptionPane.ERROR_MESSAGE);
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
    	
    	// Load the env variables from the config. This will also build a new cfg file if none exists.
        try {
			new ConfigurationBuilder(Config.class, new File("application.cfg")).build(true);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	    // Apply Technozen-inspired modern Look & Feel
	    try {
	    	// Technozen palette: white, matte silver, light blue, light green, black
	    	UIManager.put( "Component.arc", 12 );
	    	UIManager.put( "Button.arc", 14 );
	    	UIManager.put( "TextComponent.arc", 12 );
	    	UIManager.put( "ScrollBar.showButtons", true );
	    	UIManager.put( "Button.focusedBackground", new Color(235, 245, 241));
	    	UIManager.put( "Button.background", new Color(248, 250, 252));
	    	UIManager.put( "Button.foreground", new Color(51, 65, 85));
	    	UIManager.put( "Panel.background", new Color(255, 255, 255));
	    	UIManager.put( "ToolBar.background", new Color(248, 250, 252));
	    	UIManager.put( "ToolBar.borderColor", new Color(226, 232, 240));
	    	UIManager.put( "SplitPane.background", new Color(248, 250, 252));
	    	UIManager.put( "List.background", new Color(255, 255, 255));
	    	UIManager.put( "List.selectionBackground", new Color(219, 234, 254));
	    	UIManager.put( "List.selectionForeground", new Color(30, 64, 175));
	    	
	    	// Cross-platform font considerations
	    	UIManager.put( "defaultFont", new Font("Dialog", Font.PLAIN, 12));
	    	UIManager.put( "Label.font", new Font("Dialog", Font.PLAIN, 12));
	    	UIManager.put( "Button.font", new Font("Dialog", Font.PLAIN, 12));
	    	UIManager.put( "ComboBox.font", new Font("Dialog", Font.PLAIN, 12));
	    	
	    	FlatLightLaf.setup();
	    } catch (Exception ignore) {}

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

