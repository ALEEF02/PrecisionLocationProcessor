package plp.filters;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

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

import plp.config.Config;
import plp.filter.InitialFilter;
import plp.location.LocationCell;

public class BoundingEllipseFilter implements InitialFilter {
    private LatLng center;
    private double majorAxis;
    private double minorAxis;
    private double rotation;
    private List<Long> validCells;
    private List<LocationCell> locations;
    private H3Core h3;

    public BoundingEllipseFilter() {
        try {
            h3 = H3Core.newInstance();
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize H3 library", e);
        }
        center = null; // Center is not initialized until user interaction
        majorAxis = 1.0; // Default major axis length in degrees
        minorAxis = 1.0; // Default minor axis length in degrees
        rotation = 0.0; // Default rotation angle in degrees
    }
    
    @Override
    public void refreshValidCells() {
        validCells = h3.polygonToCells(getEllipseBoundary(), null, Config.H3_RESOLUTION);
    }

    @Override
    public void setRequirements(JPanel modifiedParameterPanel) {
        MapPanel mapPanel = (MapPanel) modifiedParameterPanel.getClientProperty("mapPanel");
        if (mapPanel == null) {
            throw new IllegalArgumentException("Parameter panel does not contain the required map panel.");
        }

        center = mapPanel.getCenter();
        majorAxis = mapPanel.getMajorAxis();
        minorAxis = mapPanel.getMinorAxis();
        rotation = mapPanel.getRotation();

        // Generate H3 indexes within the ellipse boundary
        refreshValidCells();
    }

    @Override
    public void setRequirements(Object requirements) throws IllegalArgumentException {
    	if (requirements instanceof EllipseRequirements req) {
            validateEllipseParameters(req.center, req.majorAxis, req.minorAxis, req.rotation);
            this.center = req.center;
            this.majorAxis = req.majorAxis;
            this.minorAxis = req.minorAxis;
            this.rotation = req.rotation;

            // Generate H3 indexes within the ellipse boundary
            refreshValidCells();
        } else {
            throw new IllegalArgumentException("Invalid requirement type for BoundingEllipseFilter");
        }
    }

    private void validateEllipseParameters(LatLng center, double majorAxis, double minorAxis, double rotation) {
        if (center == null) {
            throw new IllegalArgumentException("Center point cannot be null.");
        }
        if (majorAxis <= 0 || minorAxis <= 0) {
            throw new IllegalArgumentException("Major and minor axes must be positive values.");
        }
        if (rotation < 0 || rotation >= 360) {
            throw new IllegalArgumentException("Rotation must be between 0 and 360 degrees.");
        }
    }

    @Override
    public String getRequirements() {
        if (center == null) {
            return "Ellipse not initialized";
        }
        return String.format("Center: %s, Major Axis: %.2f, Minor Axis: %.2f, Rotation: %.2f", center, majorAxis, minorAxis, rotation);
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

    @Override
    public JPanel getParameterPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        // Create a map panel
        MapPanel mapPanel = new MapPanel();
        panel.add(mapPanel, BorderLayout.CENTER);

        JLabel helpLabel = new JLabel("Use right mouse button to move, mouse wheel to zoom.", SwingConstants.CENTER);
        panel.add(helpLabel, BorderLayout.SOUTH);

        // Attach the mapPanel as a client property for later retrieval
        panel.putClientProperty("mapPanel", mapPanel);

        return panel;
    }

    private List<LatLng> getEllipseBoundary() {
    	System.out.println(getRequirements());
        List<LatLng> boundary = new ArrayList<>();
        int numPoints = 100;
        double radiansRotation = Math.toRadians(rotation);

        for (int i = 0; i < numPoints; i++) {
            double angle = 2 * Math.PI * i / numPoints;
            double x = majorAxis * Math.cos(angle);
            double y = minorAxis * Math.sin(angle);

            // Rotate point
            double rotatedX = x * Math.cos(radiansRotation) - y * Math.sin(radiansRotation);
            double rotatedY = x * Math.sin(radiansRotation) + y * Math.cos(radiansRotation);

            // Translate to center
            boundary.add(new LatLng(center.lat + rotatedY, center.lng + rotatedX));
        }

        return boundary;
    }

    private class MapPanel extends JPanel {
        private final JMapViewer mapViewer;
        private MapMarkerDot centerMarker;
        private MapMarkerDot majorAxisMarker;
        private MapMarkerDot minorAxisMarker;
        private MapMarkerDot rotationMarker;
        private MapPolygon ellipsePolygon;
        private MapMarkerDot selectedMarker;
        
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
            setPreferredSize(new Dimension(400, 300));
            mapViewer.setDisplayPosition(new Coordinate(39.8283, -98.5795), 3);

            centerMarker = null; // Center marker not initialized until user sets it
            majorAxisMarker = null;
            minorAxisMarker = null;
            rotationMarker = null;
            ellipsePolygon = null;
            selectedMarker = null;
            
            if (center != null) { // Has existing data, but the UI is being re-initialized. Most common for nested operators.
            	initializeWithExistingEllipse();
            }

            mapViewer.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                	if (!isEnabled()) {
                		return;
                	}
                    if (centerMarker == null) {
                        Point clickPoint = e.getPoint();
                        ICoordinate coord = mapViewer.getPosition(clickPoint);

                        center = new LatLng(coord.getLat(), coord.getLon());
                        centerMarker = new MapMarkerDot(coord.getLat(), coord.getLon());
                        centerMarker.getStyle().setBackColor(Color.GREEN); // Differentiating center marker
                        mapViewer.addMapMarker(centerMarker);

                        // Get current viewport
                        double latitudeExtent = mapViewer.getPosition(new Point(0, mapViewer.getHeight())).getLat() -
                                mapViewer.getPosition(new Point(0, 0)).getLat();
                        double longitudeExtent = mapViewer.getPosition(new Point(mapViewer.getWidth(), 0)).getLon() -
                                mapViewer.getPosition(new Point(0, 0)).getLon();

                        // Initialize axis markers relative to center
                        majorAxisMarker = new MapMarkerDot(coord.getLat(), coord.getLon() + longitudeExtent / 4);
                        minorAxisMarker = new MapMarkerDot(coord.getLat() - latitudeExtent / 4, coord.getLon());
                        rotationMarker = new MapMarkerDot(coord.getLat(), coord.getLon() + longitudeExtent / 8);
                        rotationMarker.getStyle().setBackColor(Color.RED); // Differentiating rotation marker

                        mapViewer.addMapMarker(majorAxisMarker);
                        mapViewer.addMapMarker(minorAxisMarker);
                        mapViewer.addMapMarker(rotationMarker);

                        majorAxis = calculateDistance(centerMarker, majorAxisMarker);
                        minorAxis = calculateDistance(centerMarker, minorAxisMarker);

                        updateEllipse();
                        mapViewer.repaint();
                    }
                }

                @Override
                public void mousePressed(MouseEvent e) {
                	if (!isEnabled()) {
                		return;
                	}
                    if (centerMarker == null) {
                        return; // Do nothing if center is not set
                    }

                    Point clickPoint = e.getPoint();
                    MapMarkerDot closestMarker = getClosestMarker(clickPoint);
                    if (calculateDistance(clickPoint, closestMarker) > 8) return; // Too far, assume misclick
                    selectedMarker = closestMarker;
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                	if (!isEnabled()) {
                		return;
                	}
                	selectedMarker = null; // Release the selected marker
                    updateEllipse();
                }
            });

            mapViewer.addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseDragged(MouseEvent e) {
                	if (!isEnabled()) {
                		return;
                	}
                	if (centerMarker == null || selectedMarker == null) {
                        return; // Do nothing if center or a selected marker is not set
                    }

                    Point dragPoint = e.getPoint();
                    ICoordinate coord = mapViewer.getPosition(dragPoint);
                    
                    if (selectedMarker == centerMarker) {
                    	center = new LatLng(coord.getLat(), coord.getLon());
                        centerMarker.setLat(coord.getLat());
                        centerMarker.setLon(coord.getLon());
                        
                        // Constrain axis markers to remain inline with the center
                        updateAxisMarkers();
                    } else if (selectedMarker == majorAxisMarker) {
                        adjustMarkerAlongAxis(coord, true);
                        majorAxis = calculateDistance(centerMarker, majorAxisMarker);
                    } else if (selectedMarker == minorAxisMarker) {
                        adjustMarkerAlongAxis(coord, false);
                        minorAxis = calculateDistance(centerMarker, minorAxisMarker);
                    } else if (selectedMarker == rotationMarker) {
                        rotationMarker.setLat(coord.getLat());
                        rotationMarker.setLon(coord.getLon());
                        rotation = calculateRotation(centerMarker, rotationMarker);
                        updateAxisMarkers(); // Rotate axis markers around the center
                    }

                    mapViewer.repaint();
                }
            });
            
            /*
            mapViewer.addTileLoaderListener(new TileLoaderListener() {
                @Override
				public void tileLoadingFinished(Tile tile, boolean success) {
					
					
				}
            });*/

            add(mapViewer, BorderLayout.CENTER);
        }
        
        private void initializeWithExistingEllipse() {
            this.centerMarker = new MapMarkerDot(center.lat, center.lng);

            // Initialize axis markers
            double radiansRotation = Math.toRadians(rotation);
            this.majorAxisMarker = new MapMarkerDot(
                    center.lat + majorAxis * Math.sin(radiansRotation),
                    center.lng + majorAxis * Math.cos(radiansRotation));
            this.minorAxisMarker = new MapMarkerDot(
                    center.lat + minorAxis * Math.cos(radiansRotation),
                    center.lng - minorAxis * Math.sin(radiansRotation));
            this.rotationMarker = new MapMarkerDot(
                    center.lat + majorAxis / 2 * Math.sin(radiansRotation),
                    center.lng + majorAxis / 2 * Math.cos(radiansRotation));
            this.rotationMarker.getStyle().setBackColor(Color.RED);

            // Add markers to the map
            mapViewer.addMapMarker(centerMarker);
            mapViewer.addMapMarker(majorAxisMarker);
            mapViewer.addMapMarker(minorAxisMarker);
            mapViewer.addMapMarker(rotationMarker);

            updateEllipse();
        }

        private MapMarkerDot getClosestMarker(Point clickPoint) {
            double minDistance = Double.MAX_VALUE;
            MapMarkerDot closestMarker = null;

            for (MapMarkerDot marker : List.of(centerMarker, majorAxisMarker, minorAxisMarker, rotationMarker)) {
                if (marker == null) continue;

                Point markerPoint = mapViewer.getMapPosition(new Coordinate(marker.getLat(), marker.getLon()));
                if (markerPoint != null) {
                    double distance = clickPoint.distance(markerPoint);
                    if (distance < minDistance) {
                        minDistance = distance;
                        closestMarker = marker;
                    }
                }
            }

            return closestMarker;
        }

        private double calculateDistance(MapMarkerDot marker1, MapMarkerDot marker2) {
            double dx = marker1.getLat() - marker2.getLat();
            double dy = marker1.getLon() - marker2.getLon();
            return Math.sqrt(dx * dx + dy * dy);
        }
        
        private double calculateDistance(Point clickPoint, MapMarker marker) {
            Point markerPoint = mapViewer.getMapPosition(new Coordinate(marker.getLat(), marker.getLon()));
            if (markerPoint == null) return Integer.MAX_VALUE;

            return Math.sqrt((clickPoint.x - markerPoint.x) * (clickPoint.x - markerPoint.x) +
                   (clickPoint.y - markerPoint.y) * (clickPoint.y - markerPoint.y));
        }

        private double calculateRotation(MapMarkerDot center, MapMarkerDot rotationMarker) {
            double dx = rotationMarker.getLon() - center.getLon();
            double dy = rotationMarker.getLat() - center.getLat();
            return Math.toDegrees(Math.atan2(dy, dx));
        }
        
        private void updateAxisMarkers() {
            if (centerMarker == null || majorAxisMarker == null || minorAxisMarker == null) return;

            double radians = Math.toRadians(rotation);

            // Calculate rotated major axis position
            double majorDx = majorAxis * Math.cos(radians);
            double majorDy = majorAxis * Math.sin(radians);
            majorAxisMarker.setLat(centerMarker.getLat() + majorDy);
            majorAxisMarker.setLon(centerMarker.getLon() + majorDx);

            // Calculate rotated minor axis position
            double minorDx = minorAxis * Math.cos(radians + Math.PI / 2);
            double minorDy = minorAxis * Math.sin(radians + Math.PI / 2);
            minorAxisMarker.setLat(centerMarker.getLat() + minorDy);
            minorAxisMarker.setLon(centerMarker.getLon() + minorDx);
        }
        
        /**
         * Adjusts the given marker along the rotated major or minor axis.
         *
         * @param coord The new coordinate for the marker.
         * @param isMajorAxis True if the marker is for the major axis; false for the minor axis.
         */
        private void adjustMarkerAlongAxis(ICoordinate coord, boolean isMajorAxis) {
            if (centerMarker == null) {
                return; // Do nothing if the center marker is not initialized
            }

            double dx = coord.getLon() - centerMarker.getLon();
            double dy = coord.getLat() - centerMarker.getLat();

            // Determine the angle of the axis
            double axisAngle = Math.toRadians(rotation);
            if (!isMajorAxis) {
                axisAngle += Math.PI / 2; // Minor axis is perpendicular to the major axis
            }

            // Project the point onto the axis
            double projectionLength = dx * Math.cos(axisAngle) + dy * Math.sin(axisAngle);
            double projectedX = projectionLength * Math.cos(axisAngle);
            double projectedY = projectionLength * Math.sin(axisAngle);

            // Update the marker position
            if (isMajorAxis) {
                majorAxisMarker.setLon(centerMarker.getLon() + projectedX);
                majorAxisMarker.setLat(centerMarker.getLat() + projectedY);
            } else {
                minorAxisMarker.setLon(centerMarker.getLon() + projectedX);
                minorAxisMarker.setLat(centerMarker.getLat() + projectedY);
            }
        }


        public LatLng getCenter() {
            if (centerMarker == null) {
                throw new IllegalStateException("Center marker is not set");
            }
            return new LatLng(centerMarker.getLat(), centerMarker.getLon());
        }

        public double getMajorAxis() {
            return majorAxis;
        }

        public double getMinorAxis() {
            return minorAxis;
        }

        public double getRotation() {
            return rotation;
        }

        private void updateEllipse() {
            if (ellipsePolygon != null) {
                mapViewer.removeMapPolygon(ellipsePolygon);
            }

            if (centerMarker != null && majorAxis > 0 && minorAxis > 0) {
                List<Coordinate> coordinates = new ArrayList<>();

                List<LatLng> boundaryPoints = getEllipseBoundary();
                for (LatLng point : boundaryPoints) {
                    coordinates.add(new Coordinate(point.lat, point.lng));
                }

                ellipsePolygon = new MapPolygonImpl(coordinates);
                mapViewer.addMapPolygon(ellipsePolygon);
                mapViewer.repaint();
            }
        }
    }
    
    public class EllipseRequirements {
        public final LatLng center;
        public final double majorAxis;
        public final double minorAxis;
        public final double rotation;

        public EllipseRequirements(LatLng center, double majorAxis, double minorAxis, double rotation) {
            this.center = center;
            this.majorAxis = majorAxis;
            this.minorAxis = minorAxis;
            this.rotation = rotation;
        }
    }

}

