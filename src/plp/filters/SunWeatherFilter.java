package plp.filters;

import java.awt.Color;
import java.awt.GridLayout;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;

import com.uber.h3core.util.LatLng;

import plp.filter.Filter;
import plp.location.LocationCell;
import plp.location.LocationUtils;

public class SunWeatherFilter implements Filter {

    private List<LocationCell> locations;
    private static final Map<String, BufferedImage> sunriseImages = new HashMap<>();
    private static final Map<String, BufferedImage> sunsetImages = new HashMap<>();
    private SunType selectedSunType;
    private int percentage;
    
    static {
        try {
            // Preload all sunrise images
            sunriseImages.put("PT", ImageIO.read(new URI("https://sunsetwx.com/sunrise/sunrise_pt.png").toURL()));
            sunriseImages.put("MT", ImageIO.read(new URI("https://sunsetwx.com/sunrise/sunrise_mt.png").toURL()));
            sunriseImages.put("CT", ImageIO.read(new URI("https://sunsetwx.com/sunrise/sunrise_ct.png").toURL()));
            sunriseImages.put("ET", ImageIO.read(new URI("https://sunsetwx.com/sunrise/sunrise_et.png").toURL()));

            // Preload all sunset images
            sunsetImages.put("PT", ImageIO.read(new URI("https://sunsetwx.com/sunset/sunset_pt.png").toURL()));
            sunsetImages.put("MT", ImageIO.read(new URI("https://sunsetwx.com/sunset/sunset_mt.png").toURL()));
            sunsetImages.put("CT", ImageIO.read(new URI("https://sunsetwx.com/sunset/sunset_ct.png").toURL()));
            sunsetImages.put("ET", ImageIO.read(new URI("https://sunsetwx.com/sunset/sunset_et.png").toURL()));
        } catch (Exception e) {
            throw new RuntimeException("Failed to preload weather images: " + e.getMessage(), e);
        }
    }
    
	public SunWeatherFilter() {}
	
    @Override
    public String getRequirements() {
        return selectedSunType + ", Min Quality: " + percentage + "%";
    }

	@Override
	public void setLocations(List<LocationCell> locations) {
        this.locations = locations;
	}

	@Override
	public List<LocationCell> process() {
		return locations.stream()
                .filter(cell -> getPercentageFromColor(getColorAt(LocationUtils.getLatLng(cell))) >= percentage)
                .toList();
	}
	
	@Override
    public void setRequirements(JPanel modifiedParameterPanel) throws IllegalArgumentException {
        JComboBox<SunType> comboBox = (JComboBox<SunType>) modifiedParameterPanel.getClientProperty("sunTypeComboBox");
        JSlider slider = (JSlider) modifiedParameterPanel.getClientProperty("percentageSlider");

        if (comboBox == null || slider == null) {
            throw new IllegalArgumentException("Parameters are missing in the panel.");
        }
        
        setRequirements(new SunWeatherRequirements((SunType) comboBox.getSelectedItem(), slider.getValue()));
    }

	@Override
    public void setRequirements(Object requirements) throws IllegalArgumentException {
        if (requirements instanceof String stringRequirement) {
            String[] parts = stringRequirement.split(",");
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid string format. Expected 'SunType,Percentage'.");
            }

            try {
                this.selectedSunType = SunType.valueOf(parts[0].trim().toUpperCase());
                this.percentage = Integer.parseInt(parts[1].trim());
                if (this.percentage < 0 || this.percentage > 100) {
                    throw new IllegalArgumentException("Percentage must be between 0 and 100.");
                }
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid SunType or Percentage: " + e.getMessage());
            }
        } else if (requirements instanceof SunWeatherRequirements swr) {
            this.selectedSunType = swr.getSunType();
            this.percentage = swr.getPercentage();
            if (this.percentage < 0 || this.percentage > 100) {
                throw new IllegalArgumentException("Percentage must be between 0 and 100.");
            }
        } else {
            throw new IllegalArgumentException("Unsupported requirements type: " + requirements.getClass().getName());
        }
    }

    @Override
    public JPanel getParameterPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new GridLayout(2, 2));

        JLabel sunTypeLabel = new JLabel("Sun Event:");
        JComboBox<SunType> sunTypeComboBox = new JComboBox<>(SunType.values());
        panel.add(sunTypeLabel);
        panel.add(sunTypeComboBox);

        JLabel percentageLabel = new JLabel("Minimum Quality Percentage:");
        JSlider percentageSlider = new JSlider(0, 100, 50);
        percentageSlider.setMajorTickSpacing(20);
        percentageSlider.setPaintTicks(true);
        percentageSlider.setPaintLabels(true);
        panel.add(percentageLabel);
        panel.add(percentageSlider);

        panel.putClientProperty("sunTypeComboBox", sunTypeComboBox);
        panel.putClientProperty("percentageSlider", percentageSlider);

        return panel;
    }
    
    /**
     * Gets the appropriate weather image based on the longitude and selected sun type.
     *
     * @param longitude The longitude of the location.
     * @return The corresponding BufferedImage.
     */
    private BufferedImage getWeatherImage(double longitude) {
        Map<String, BufferedImage> imageMap = selectedSunType == SunType.Sunrise ? sunriseImages : sunsetImages;
        if (longitude <= -113) {
            return imageMap.get("PT");
        } else if (longitude <= -98) {
            return imageMap.get("MT");
        } else if (longitude <= -83) {
            return imageMap.get("CT");
        } else {
            return imageMap.get("ET");
        }
    }
    
    /**
     * Gets the color of the weather image at the specified coordinates.
     * 
     * @param x The x-coordinate of the pixel.
     * @param y The y-coordinate of the pixel.
     * @return The Color of the pixel at the given coordinates.
     */
    private Color getColorAt(LatLng location) {
        BufferedImage weatherImage = getWeatherImage(location.lng);
        int x = (int) (21.2182 * location.lng + 2726.27);
        int y = (int) (-25.4166 * location.lat + 1504.25);
        return getColorAt(weatherImage, x, y);
    }
	
	/**
     * Gets the color of the weather image at the specified coordinates.
     * 
     * @param image The BufferedImage to analyze.
     * @param x The x-coordinate of the pixel.
     * @param y The y-coordinate of the pixel.
     * @return The Color of the pixel at the given coordinates.
     */
    private Color getColorAt(BufferedImage image, int x, int y) {
        if (image == null) {
            throw new IllegalStateException("Weather image not initialized.");
        }
        int rgb = image.getRGB(x, y);
        return new Color(rgb);
    }
    
    /**
     * Matches a given color to a percentage based on a predefined color bar.
     * The pink end represents 0%, and the red end represents 100%.
     *
     * @param inputColor The input color to match.
     * @return The corresponding percentage (0–100).
     */
    private int getPercentageFromColor(Color inputColor) {
        BufferedImage referenceImage = selectedSunType == SunType.Sunrise ? sunriseImages.get("ET") : sunsetImages.get("ET");

        int barEnd = 197;
        int barStart = 886;
        int closestY = -1;
        double closestDistance = Double.MAX_VALUE;

        for (int y = barStart; y >= barEnd; y--) { // Traverse the color bar from bottom to top
            Color barColor = new Color(referenceImage.getRGB(1350, y)); // 1350 is the middle of the colorbar in the image
            double distance = calculateColorDistance(barColor, inputColor);

            if (distance < closestDistance) {
                closestDistance = distance;
                closestY = y;
            }
        }

        return (int) ((double) (closestY-barStart) / (barEnd-barStart) * 100);
    }

    /**
     * Calculates the distance between two colors in RGB space.
     *
     * @param c1 The first color.
     * @param c2 The second color.
     * @return The distance between the two colors.
     */
    private double calculateColorDistance(Color c1, Color c2) {
        int redDiff = c1.getRed() - c2.getRed();
        int greenDiff = c1.getGreen() - c2.getGreen();
        int blueDiff = c1.getBlue() - c2.getBlue();
        return Math.sqrt(redDiff * redDiff + greenDiff * greenDiff + blueDiff * blueDiff);
    }

    /**
     * SunWeatherRequirements - Helper class for structured input.
     */
    public static class SunWeatherRequirements {
        private final SunType sunType;
        private final int percentage;

        public SunWeatherRequirements(SunType sunType, int percentage) {
            this.sunType = sunType;
            this.percentage = percentage;
        }

        public SunType getSunType() {
            return sunType;
        }

        public int getPercentage() {
            return percentage;
        }
    }

    enum SunType {
    	Sunrise, Sunset
    }
}
