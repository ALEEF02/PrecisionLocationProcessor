package plp.filters;

import java.awt.GridLayout;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.io.FileInputStream;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import com.uber.h3core.util.LatLng;

import plp.filter.Filter;
import plp.location.LocationCell;
import plp.location.LocationUtils;

public class LightPollutionFilter implements Filter {
    private double minSQM;
    private List<LocationCell> locations;
    private static final String TILE_PATH = "data/lightpollution/binary_tiles/2022/";
    private static final String TILE_URL_BASE = "https://github.com/djlorenz/djlorenz.github.io/raw/refs/heads/master/astronomy/binary_tiles/2022/";
    private static final Map<String, byte[]> tileDataCache = new HashMap<>(); // Cache for decompressed tiles
    private static final int TILE_SIZE = 600; 

    public LightPollutionFilter() {
    	LocationUtils.initialize();
    	ensureLightPollutionTilesExist();
    	preloadTiles();
    }
    
    /**
     * Ensures the light pollution tiles are downloaded and available in TILE_PATH.
     */
    private void ensureLightPollutionTilesExist() {
        File tileDirectory = new File(TILE_PATH);
        if (!tileDirectory.exists() || tileDirectory.list().length == 0) {
            System.out.println("Light pollution tiles not found. Downloading...");
            tileDirectory.mkdirs();
            downloadLightPollutionTiles();
        }
    }

    /**
     * Downloads light pollution tiles from the specified GitHub URL and saves them locally.
     */
    private void downloadLightPollutionTiles() {
        try {
            for (int x = 1; x <= 72; x++) { // 72 tiles in longitude (5 degrees each)
                for (int y = 1; y <= 28; y++) { // 28 tiles in latitude
                    String fileName = "binary_tile_" + x + "_" + y + ".dat.gz";
                    URL fileUrl = new URI(TILE_URL_BASE + fileName).toURL();
                    Path localFilePath = Path.of(TILE_PATH, fileName);

                    System.out.println("Downloading: " + fileUrl);
                    try (InputStream in = fileUrl.openStream()) {
                        Files.copy(in, localFilePath, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
            System.out.println("Light pollution data downloaded successfully.");
        } catch (IOException e) {
            throw new RuntimeException("Failed to download light pollution tiles: " + e.getMessage(), e);
        } catch (URISyntaxException e) {
            throw new RuntimeException("Failed to download light pollution tiles: " + e.getMessage(), e);
		}
    }
    
    /**
     * Preloads all binary tiles into memory to optimize access.
     */
    private static void preloadTiles() {
    	if (!tileDataCache.isEmpty()) return;
        File dir = new File(TILE_PATH);
        File[] files = dir.listFiles((d, name) -> name.endsWith(".dat.gz"));
        if (files == null) {
            throw new RuntimeException("Light pollution data folder is empty or invalid: " + TILE_PATH);
        }

        for (File file : files) {
            try {
                String tileKey = file.getName().replace(".dat.gz", "").replace("binary_tile_", "");
                tileDataCache.put(tileKey, decompressTile(file));
            } catch (IOException e) {
                System.err.println("Failed to load tile: " + file.getName());
            }
        }
    }

    /**
     * Decompresses a GZIP file and returns its byte data.
     */
    private static byte[] decompressTile(File file) throws IOException {
        try (InputStream fileStream = new FileInputStream(file);
             GZIPInputStream gzipStream = new GZIPInputStream(fileStream);
             ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {

            byte[] temp = new byte[1024];
            int bytesRead;
            while ((bytesRead = gzipStream.read(temp)) != -1) {
                buffer.write(temp, 0, bytesRead);
            }
            return buffer.toByteArray();
        }
    }
    
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
            System.out.println(this.minSQM);
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
                .filter(cell -> getSQM(LocationUtils.getLatLng(cell)) >= minSQM)
                .toList();
    }
    
    @Override
    public JPanel getParameterPanel() {
        JPanel panel = new JPanel(new GridLayout(1, 2));
        panel.add(new JLabel("Min SQM:"));
        JTextField minSQMField = new JTextField(minSQM == 0.0 ? "21.92" : String.valueOf(minSQM));
        panel.add(minSQMField);
        panel.putClientProperty("fields", new JTextField[]{minSQMField});
        return panel;
    }

	@Override
	public String getRequirements() {
		return "Minimum SQM: " + String.valueOf(minSQM);
	}
	
	private double getSQM(LocationCell cell) {
		return getSQM(LocationUtils.getLatLng(cell));
	}
	
	private double getSQM(LatLng coords) {
		return getSQM(coords.lat, coords.lng);
	}
	
	/**
     * Computes the mean SQM for a given LocationCell using the Light Pollution Atlas binary tiles.
     *
     * @param latitude  The latitude of the location.
     * @param longitude The longitude of the location.
     * @return The mean SQM value for the location.
     */
    private double getSQM(double latitude, double longitude) {

        // Convert latitude and longitude to tile and grid indices
        double lonFromDateLine = mod(longitude + 180.0, 360.0);
        double latFromStart = latitude + 65.0;

        int tileX = (int) Math.floor(lonFromDateLine / 5.0) + 1;
        int tileY = (int) Math.floor(latFromStart / 5.0) + 1;

        if (tileY < 1 || tileY > 28) {
            throw new IllegalArgumentException("Location out of bounds (65S to 75N latitude).");
        }

        String tileKey = tileX + "_" + tileY;
        byte[] data = tileDataCache.get(tileKey);
        if (data == null) {
            throw new RuntimeException("Tile not found in cache: " + tileKey);
        }

        int ix = (int) Math.round(120.0 * (lonFromDateLine - 5.0 * (tileX - 1) + 1.0 / 240.0));
        int iy = (int) Math.round(120.0 * (latFromStart - 5.0 * (tileY - 1) + 1.0 / 240.0));
    	
    	// Ensure indices are within bounds
        if (ix < 0 || ix > 600 || iy < 0 || iy > 600) {
            throw new RuntimeException("Grid indices out of bounds: ix=" + ix + ", iy=" + iy);
        }

        int firstNumber = 128 * data[0] + data[1];
        int change = 0;

        for (int i = 1; i < iy; i++) {
            change += data[TILE_SIZE * i + 1];
        }

        for (int i = 1; i < ix; i++) {
            change += data[TILE_SIZE * (iy - 1) + 1 + i];
        }

        int compressed = firstNumber + change;
        
        // Ensure compressed value is valid
        if (compressed < 0) {
            throw new RuntimeException("Invalid compressed value: " + compressed);
        }
        
        double brightnessRatio = compressed2full(compressed);
        return 22.0 - 5.0 * Math.log10(1.0 + brightnessRatio) / Math.log10(100.0);
    }

    /**
     * Handles the modulo operation for positive and negative numbers.
     * @param x
     * @param y
     * @return x mod y
     */
    private static double mod(double x, double y) {
        return ((x % y) + y) % y;
    }

    /**
     * Converts the compressed value to brightness ratio.
     *
     * @param compressed The compressed brightness value.
     * @return The full brightness ratio.
     */
    private static double compressed2full(int compressed) {
    	return (5.0/195.0) * ( Math.exp(0.0195*compressed) - 1.0);
    }
}
