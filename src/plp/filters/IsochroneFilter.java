package plp.filters;

import org.json.JSONArray;
import org.json.JSONObject;
import org.openstreetmap.gui.jmapviewer.Coordinate;
import org.openstreetmap.gui.jmapviewer.JMapViewer;
import org.openstreetmap.gui.jmapviewer.MapMarkerDot;
import org.openstreetmap.gui.jmapviewer.MapPolygonImpl;
import org.openstreetmap.gui.jmapviewer.Tile;
import org.openstreetmap.gui.jmapviewer.interfaces.ICoordinate;
import org.openstreetmap.gui.jmapviewer.interfaces.MapMarker;
import org.openstreetmap.gui.jmapviewer.interfaces.MapPolygon;

import com.uber.h3core.H3Core;
import com.uber.h3core.util.LatLng;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import plp.config.Config;
import plp.filter.InitialFilter;
import plp.location.LocationCell;

public class IsochroneFilter implements InitialFilter {

	private LatLng centerPoint;
    private int maxMinutes;
    private TransportType transportationMode;
	private List<LatLng> boundaryPoints;
    private List<Long> validCells;
    private List<LocationCell> locations;
    private H3Core h3;

    public IsochroneFilter() {
        try {
            h3 = H3Core.newInstance();
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize H3 library", e);
        }
        centerPoint = null;
        maxMinutes = 10; // Default to 10 minutes
        transportationMode = TransportType.Driving; // Default mode
    }
    
    @Override
    public void setRequirements(JPanel modifiedParameterPanel) {
        MapPanel mapPanel = (MapPanel) modifiedParameterPanel.getClientProperty("mapPanel");
        if (mapPanel == null) {
            throw new IllegalArgumentException("Parameter panel does not contain the required map panel.");
        }
        
        JTextField minutesField = (JTextField) modifiedParameterPanel.getClientProperty("minutesField");
        if (minutesField == null) {
            throw new IllegalArgumentException("Parameter panel does not contain the required minutesField.");
        }
        
        JComboBox<TransportType> transportComboBox = (JComboBox<TransportType>) modifiedParameterPanel.getClientProperty("transportSelector");
        if (transportComboBox == null) {
            throw new IllegalArgumentException("Parameter panel does not contain the required transportSelector.");
        }

        LatLng center = mapPanel.getCenterMarker();
        if (center == null) {
            throw new IllegalArgumentException("A center point must be selected on the map.");
        }
        
        setRequirements(new IsochroneRequirements(center, Integer.parseInt(minutesField.getText()), (TransportType)transportComboBox.getSelectedItem()));
    }

	@Override
	public void setRequirements(Object requirements) throws IllegalArgumentException {
		if (!(requirements instanceof IsochroneRequirements)) {
            throw new IllegalArgumentException("Invalid requirement type for IsochroneFilter");
        }
		
		IsochroneRequirements req = (IsochroneRequirements) requirements;
        this.centerPoint = req.getCenterPoint();
        if (req.getMaxMinutes() > 60 || req.getMaxMinutes() < 1) {
        	throw new IllegalArgumentException("Invalid max minutes; Must be 1 <= 60");
        }
        this.maxMinutes = req.getMaxMinutes();
        this.transportationMode = req.getTransportationMode();
        
		try {
            boundaryPoints = fetchIsochrone();
    		System.out.println(boundaryPoints);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to fetch isochrone data: " + e.getMessage());
        }
        
        refreshValidCells();

	}

	@Override
    public String getRequirements() {
        return String.format("Center: %s, Max Minutes: %d, Mode: %s", centerPoint, maxMinutes, transportationMode);
    }

	@Override
	public void setLocations(List<LocationCell> locations) {
		this.locations = locations;
	}

	@Override
    public List<LocationCell> process() {
        return locations.stream()
                .filter(cell -> validCells.contains(cell.getH3Index()))
                .toList();
    }
	
	@Override
	public List<LocationCell> getValidCells() {
        return validCells.stream()
                .map(LocationCell::new)
                .toList();
	}
	
	private List<LatLng> fetchIsochrone() throws Exception {
		String urlString = "https://api.openrouteservice.org/v2/isochrones/" + transportationMode.getProfileString();
        URL url = new URI(urlString).toURL();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        connection.setRequestMethod("POST");
        connection.setRequestProperty("Authorization", Config.OPENROUTESERVICE_API_KEY);
        connection.setRequestProperty("Accept", "application/json, application/geo+json, application/gpx+xml, img/png; charset=utf-8");
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setDoOutput(true);

        String payload = String.format(
            "{\"locations\":[[%f,%f]],\"range\":[%d]}",
            centerPoint.lng, centerPoint.lat, maxMinutes * 60
        );

        try (OutputStream os = connection.getOutputStream()) {
            byte[] input = payload.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }

        int responseCode = connection.getResponseCode();
        if (responseCode != 200) {
            throw new IOException("Failed to fetch isochrone data. Response code: " + responseCode + "\n\n" + url + "\n" + payload);
        }

        try (InputStream responseStream = connection.getInputStream()) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(responseStream, StandardCharsets.UTF_8));
            StringBuilder responseBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                responseBuilder.append(line);
            }

            JSONObject jsonResponse = new JSONObject(responseBuilder.toString());
            JSONArray coordinates = jsonResponse
                .getJSONArray("features")
                .getJSONObject(0)
                .getJSONObject("geometry")
                .getJSONArray("coordinates")
                .getJSONArray(0);

            List<LatLng> polygonPoints = new ArrayList<>();
            for (int i = 0; i < coordinates.length(); i++) {
                JSONArray coord = coordinates.getJSONArray(i);
                double lng = coord.getDouble(0);
                double lat = coord.getDouble(1);
                polygonPoints.add(new LatLng(lat, lng));
            }

            return polygonPoints;
        }
	}

	@Override
	public void refreshValidCells() {
		
		if (centerPoint == null) return;
        if (boundaryPoints.isEmpty()) return;
		
    	validCells = h3.polygonToCells(boundaryPoints, null, Config.H3_RESOLUTION);

	}
	
	@Override
    public JPanel getParameterPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        // Create a map panel
        MapPanel mapPanel = new MapPanel();
        panel.add(mapPanel, BorderLayout.CENTER);

        // Add control buttons
        JPanel controlPanel = new JPanel(new FlowLayout());
        controlPanel.add(new JLabel("Max minutes away:"));
        JTextField maxMinutesField = new JTextField(String.valueOf(maxMinutes));
        controlPanel.add(maxMinutesField);
        panel.putClientProperty("minutesField", maxMinutesField);
        JComboBox<TransportType> transportTypeComboBox = new JComboBox<>(TransportType.values());
        if (transportationMode != null) {transportTypeComboBox.setSelectedItem(transportationMode);}
        controlPanel.add(transportTypeComboBox);
        panel.putClientProperty("transportSelector", transportTypeComboBox);
        JButton updateIsochroneButton = new JButton("Preview Isochrone");
        controlPanel.add(updateIsochroneButton);

        panel.add(controlPanel, BorderLayout.SOUTH);

        // Attach the mapPanel as a client property for later retrieval
        panel.putClientProperty("mapPanel", mapPanel);

        // Event listeners
        updateIsochroneButton.addActionListener(e -> {
            mapPanel.updateIsochrone();
        });

        return panel;
    }
	
	private class MapPanel extends JPanel {
        private final JMapViewer mapViewer;
        private MapMarker centerMarker;
        private MapMarker draggedMarker;
        private MapPolygon boundingPolygon;
        
    	private boolean loaded;
        
        private class JMapViewerFit extends JMapViewer {

            @Override
            public void tileLoadingFinished(Tile tile, boolean success) {
                super.tileLoadingFinished(tile, success);
                if (!loaded & success) {
                    loaded = true;
                    setDisplayToFitMapElements(true, false, true);
                }
            }
        }

        public MapPanel() {
            setLayout(new BorderLayout());
            mapViewer = new JMapViewerFit();
            mapViewer.setDisplayPosition(new Coordinate(39.8283, -98.5795), 3);
            setPreferredSize(new Dimension(400, 300));
            importMarkersFromSavedPoint();

            mapViewer.addMouseListener(new MouseAdapter() {
            	@Override
                public void mousePressed(MouseEvent e) {
                	if (!isEnabled() || e.getButton() != MouseEvent.BUTTON1 || centerMarker == null) {
                		return;
                	}
                    Point clickPoint = e.getPoint();
                    draggedMarker = calculateDistance(clickPoint, centerMarker) <= 8 ? centerMarker : null; // 8 pixels radius
                }
            	
            	@Override
                public void mouseReleased(MouseEvent e) {
                	if (!isEnabled()) {
                		return;
                	}
                    draggedMarker = null; // Stop dragging
                }
            	
                @Override
                public void mouseClicked(MouseEvent e) {
                	if (!isEnabled() || e.getButton() != MouseEvent.BUTTON1 || draggedMarker != null) {
                		return;
                	}
                	
                    Point clickPoint = e.getPoint();
                    ICoordinate coord = mapViewer.getPosition(clickPoint);

                    if (centerMarker != null) {
                    	mapViewer.removeMapMarker(centerMarker);
                    }
                    centerMarker = new MapMarkerDot(coord.getLat(), coord.getLon());
                    centerPoint = new LatLng(centerMarker.getLat(), centerMarker.getLon());
                    mapViewer.addMapMarker(centerMarker);

                    mapViewer.repaint();
                }
            });
            
            mapViewer.addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseDragged(MouseEvent e) {
                	if (!isEnabled()) {
                		return;
                	}
                    if (draggedMarker != null) {
                        Point dragPoint = e.getPoint();
                        ICoordinate coord = mapViewer.getPosition(dragPoint);
                        draggedMarker.setLat(coord.getLat());
                        draggedMarker.setLon(coord.getLon());
                        mapViewer.repaint();
                    }
                }
            });

            add(mapViewer, BorderLayout.CENTER);
        }
        
        private void updateBoundingPolygon() {
            if (boundingPolygon != null) {
                mapViewer.removeMapPolygon(boundingPolygon);
            }

            if (boundaryPoints.size() >= 3) {
                List<Coordinate> coordinates = new ArrayList<>();
                for (LatLng marker : boundaryPoints) {
                    coordinates.add(new Coordinate(marker.lat, marker.lng));
                }
                // Close the polygon by adding the first point to the end
                coordinates.add(coordinates.get(0));

                boundingPolygon = new MapPolygonImpl(coordinates);
                mapViewer.addMapPolygon(boundingPolygon);
                mapViewer.repaint();
            }
        }
        
        private double calculateDistance(Point clickPoint, MapMarker marker) {
            Point markerPoint = mapViewer.getMapPosition(new Coordinate(marker.getLat(), marker.getLon()));
            if (markerPoint == null) return Integer.MAX_VALUE;

            return Math.sqrt((clickPoint.x - markerPoint.x) * (clickPoint.x - markerPoint.x) +
                   (clickPoint.y - markerPoint.y) * (clickPoint.y - markerPoint.y));
        }

        public LatLng getCenterMarker() {
            if (centerMarker != null) {
                return new LatLng(centerMarker.getLat(), centerMarker.getLon());
            }
            return null;
        }
        
        private void importMarkersFromSavedPoint() {
        	if (centerPoint != null) {
        		centerMarker = new MapMarkerDot(centerPoint.lat, centerPoint.lng);
        		mapViewer.addMapMarker(centerMarker);
            }
        }

        public void updateIsochrone() {
            // Simulate fetching isochrone data and update the map
            // This should call the API and update validCells accordingly
            if (centerMarker != null) {
                refreshValidCells();
                updateBoundingPolygon();
                mapViewer.repaint();
            }
        }
    }
	
	enum TransportType {
    	Driving("driving-car"),
        PublicTransport("public-transport"),
        Cycling("cycling-regular"),
    	Walking("foot-walking"),
    	Hiking("foot-hiking");
    	
    	private final String profile;
		
    	TransportType(String profile)
		{
			this.profile = profile;
		}
	    
	    public String getProfileString()
	    {
	        return profile;
	    }
    }
	
	public static class IsochroneRequirements {
        private final LatLng centerPoint;
        private final int maxMinutes;
        private final TransportType transportationMode;

        public IsochroneRequirements(LatLng centerPoint, int maxMinutes, TransportType transportationMode) {
            this.centerPoint = centerPoint;
            this.maxMinutes = maxMinutes;
            this.transportationMode = transportationMode;
        }

        public LatLng getCenterPoint() {
            return centerPoint;
        }

        public int getMaxMinutes() {
            return maxMinutes;
        }

        public TransportType getTransportationMode() {
            return transportationMode;
        }
    }

}
