package plp.filters;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;

import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.JButton;
import javax.swing.JLabel;

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

import plp.Config;
import plp.filter.InitialFilter;
import plp.location.LocationCell;

public class BoundingPolygonFilter implements InitialFilter {

	private List<LatLng> boundaryPoints;
    private List<Long> validCells;
    private List<LocationCell> locations;
    private H3Core h3;

    public BoundingPolygonFilter() {
        try {
            h3 = H3Core.newInstance();
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize H3 library", e);
        }
        boundaryPoints = new ArrayList<>();
    }
    
    @Override
    public void refreshValidCells() {
    	validCells = h3.polygonToCells(boundaryPoints, null, Config.H3_RESOLUTION);
    }

    @Override
    public void setRequirements(JPanel modifiedParameterPanel) {
        MapPanel mapPanel = (MapPanel) modifiedParameterPanel.getClientProperty("mapPanel");
        if (mapPanel == null) {
            throw new IllegalArgumentException("Parameter panel does not contain the required map panel.");
        }

        List<LatLng> points = mapPanel.getBoundaryPoints();
        if (points.size() < 3) {
            throw new IllegalArgumentException("At least 3 points are required to define a bounding polygon.");
        }

        boundaryPoints.clear();
        boundaryPoints = points;
        refreshValidCells();
    }

    @SuppressWarnings("unchecked")
	@Override
    public void setRequirements(Object requirements) throws IllegalArgumentException {
        if (requirements instanceof List<?>) {
            List<?> points = (List<?>) requirements;
            if (points.size() < 3) {
                throw new IllegalArgumentException("At least 3 points are required to define a bounding polygon.");
            }
            if (!(points.getFirst() instanceof LatLng)) {
                throw new IllegalArgumentException("Must be an array of LatLng.");
            }
            boundaryPoints = (List<LatLng>) points;
            refreshValidCells();
        } else {
            throw new IllegalArgumentException("Invalid requirement type for BoundingBoxFilter");
        }
    }

    @Override
    public String getRequirements() {
        return boundaryPoints.toString();
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

    public List<LocationCell> getValidCells() {
        return validCells.stream()
                .map(LocationCell::new)
                .toList();
    }

    @Override
    public JPanel getParameterPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        // Create a map panel
        MapPanel mapPanel = new MapPanel();
        panel.add(mapPanel, BorderLayout.NORTH);

        // Add control buttons
        JPanel controlPanel = new JPanel(new FlowLayout());
        JButton addPointButton = new JButton("Add Point");
        JButton removePointButton = new JButton("Remove Point");
        JButton clearPointsButton = new JButton("Clear Points");

        controlPanel.add(addPointButton);
        controlPanel.add(removePointButton);
        controlPanel.add(clearPointsButton);

        panel.add(controlPanel, BorderLayout.CENTER);
        
        JLabel helpLabel = new JLabel("Use right mouse button to move, mouse wheel to zoom.", SwingConstants.CENTER);
        panel.add(helpLabel, BorderLayout.SOUTH);

        // Attach the mapPanel as a client property for later retrieval
        panel.putClientProperty("mapPanel", mapPanel);

        // Event listeners
        addPointButton.addActionListener(e -> mapPanel.addPoint());
        removePointButton.addActionListener(e -> mapPanel.removeLastPoint());
        clearPointsButton.addActionListener(e -> mapPanel.clearPoints());

        return panel;
    }

    private class MapPanel extends JPanel {
        private final JMapViewer mapViewer;
        private final List<MapMarker> markers;
        private MapPolygon boundingPolygon;
        private MapMarker draggedMarker;
        
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
            markers = new ArrayList<>();
            importMarkersFromBoundaryPoints();

            mapViewer.addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                	if (!isEnabled()) {
                		return;
                	}
                    Point clickPoint = e.getPoint();
                    draggedMarker = markers.stream()
                            .min((marker1, marker2) -> {
                                double dist1 = calculateDistance(clickPoint, marker1);
                                double dist2 = calculateDistance(clickPoint, marker2);
                                return Double.compare(dist1, dist2);
                            })
                            .filter(marker -> calculateDistance(clickPoint, marker) <= 8) // 8 pixels radius
                            .orElse(null);
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                	if (!isEnabled()) {
                		return;
                	}
                    draggedMarker = null; // Stop dragging
                    updateBoundingPolygon();
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                	if (!isEnabled()) {
                		return;
                	}
                    if (draggedMarker == null) {
                        Point clickPoint = e.getPoint();
                        ICoordinate coord = mapViewer.getPosition(clickPoint);
                        MapMarkerDot marker = new MapMarkerDot(coord.getLat(), coord.getLon());
                        markers.add(marker);
                        mapViewer.addMapMarker(marker);
                        updateBoundingPolygon();
                    }
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
        
        private double calculateDistance(Point clickPoint, MapMarker marker) {
            Point markerPoint = mapViewer.getMapPosition(new Coordinate(marker.getLat(), marker.getLon()));
            if (markerPoint == null) return Integer.MAX_VALUE;

            return Math.sqrt((clickPoint.x - markerPoint.x) * (clickPoint.x - markerPoint.x) +
                   (clickPoint.y - markerPoint.y) * (clickPoint.y - markerPoint.y));
        }
        
        private void updateBoundingPolygon() {
            if (boundingPolygon != null) {
                mapViewer.removeMapPolygon(boundingPolygon);
            }

            if (markers.size() >= 3) {
                List<Coordinate> coordinates = new ArrayList<>();
                for (MapMarker marker : markers) {
                    coordinates.add(new Coordinate(marker.getLat(), marker.getLon()));
                }
                // Close the polygon by adding the first point to the end
                coordinates.add(coordinates.get(0));

                boundingPolygon = new MapPolygonImpl(coordinates);
                mapViewer.addMapPolygon(boundingPolygon);
                mapViewer.repaint();
            }
        }

        public void addPoint() {
        	ICoordinate center = mapViewer.getPosition(mapViewer.getWidth() / 2, mapViewer.getHeight() / 2);
        	MapMarkerDot marker;
            if (center != null) {
                marker = new MapMarkerDot(center.getLat(), center.getLon());
            } else {
                marker = new MapMarkerDot(37.7749, -122.4194); // San Francisco
            }
            markers.add(marker);
            mapViewer.addMapMarker(marker);
            updateBoundingPolygon();
        }

        public void removeLastPoint() {
            if (!markers.isEmpty()) {
                MapMarker lastMarker = markers.remove(markers.size() - 1);
                mapViewer.removeMapMarker(lastMarker);
                updateBoundingPolygon();
            }
        }

        public void clearPoints() {
            for (MapMarker marker : markers) {
                mapViewer.removeMapMarker(marker);
            }
            markers.clear();
            updateBoundingPolygon();
        }

        public List<LatLng> getBoundaryPoints() {
            List<LatLng> boundaryPoints = new ArrayList<>();
            for (MapMarker marker : markers) {
                boundaryPoints.add(new LatLng(marker.getLat(), marker.getLon()));
            }
            return boundaryPoints;
        }
        
        private void importMarkersFromBoundaryPoints() {
        	for (LatLng point : boundaryPoints) {
        		markers.add(new MapMarkerDot(point.lat, point.lng));
                mapViewer.addMapMarker(markers.getLast());
            }
            updateBoundingPolygon();
        }
    }

}
