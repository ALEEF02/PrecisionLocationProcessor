package plp.filters;

import java.awt.GridLayout;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
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

public class ElevationFilter implements Filter {
    private Double minElevation; // meters
    private Double maxElevation; // meters
    private List<LocationCell> locations;
    private static final String TILE_PATH = "data/srtm/tiles/";
    private static final Map<String, short[]> tileDataCache = new HashMap<>(); // Cache for decompressed tiles
    private static final int TILE_SIZE = 1201; // SRTM3: 1201x1201 per tile
    private static boolean dataChecked = false;
    private static boolean dataAvailable = false;

    public ElevationFilter() {
        LocationUtils.initialize();
        ensureSRTMTilesExist();
        preloadTiles();
    }

    /**
     * Ensures the SRTM tiles are present. If not, print a warning.
     */
    private void ensureSRTMTilesExist() {
        if (dataChecked) return;
        File tileDirectory = new File(TILE_PATH);
        if (!tileDirectory.exists() || tileDirectory.list() == null || tileDirectory.list().length == 0) {
            System.out.println("[ElevationFilter] SRTM tiles not found in " + TILE_PATH + ". Elevation filtering will not work.");
            dataAvailable = false;
        } else {
            dataAvailable = true;
        }
        dataChecked = true;
    }

    /**
     * Preloads all SRTM tiles into memory to optimize access.
     */
    private static void preloadTiles() {
        if (!dataAvailable || !tileDataCache.isEmpty()) return;
        File dir = new File(TILE_PATH);
        File[] files = dir.listFiles((d, name) -> name.endsWith(".hgt.gz"));
        if (files == null) return;
        for (File file : files) {
            try {
                String tileKey = file.getName().replace(".hgt.gz", "");
                tileDataCache.put(tileKey, decompressTile(file));
            } catch (IOException e) {
                System.err.println("[ElevationFilter] Failed to load tile: " + file.getName());
            }
        }
    }

    /**
     * Decompresses a GZIP SRTM file and returns its short[] data (big-endian, 2 bytes per sample).
     */
    private static short[] decompressTile(File file) throws IOException {
        try (InputStream fileStream = new FileInputStream(file);
             GZIPInputStream gzipStream = new GZIPInputStream(fileStream);
             ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            byte[] temp = new byte[4096];
            int bytesRead;
            while ((bytesRead = gzipStream.read(temp)) != -1) {
                buffer.write(temp, 0, bytesRead);
            }
            byte[] raw = buffer.toByteArray();
            short[] data = new short[raw.length / 2];
            for (int i = 0; i < data.length; i++) {
                int hi = raw[2 * i] & 0xFF;
                int lo = raw[2 * i + 1] & 0xFF;
                data[i] = (short) ((hi << 8) | lo);
            }
            return data;
        }
    }

    @Override
    public void setRequirements(JPanel modifiedParameterPanel) throws IllegalArgumentException {
        JTextField[] fields = (JTextField[]) modifiedParameterPanel.getClientProperty("fields");
        String minText = fields[0].getText().trim();
        String maxText = fields[1].getText().trim();
        Double min = null, max = null;
        if (!minText.isEmpty()) {
            try { min = Double.parseDouble(minText); } catch (NumberFormatException e) { throw new IllegalArgumentException("Invalid min elevation"); }
        }
        if (!maxText.isEmpty()) {
            try { max = Double.parseDouble(maxText); } catch (NumberFormatException e) { throw new IllegalArgumentException("Invalid max elevation"); }
        }
        setRequirements(new Double[]{min, max});
    }

    @Override
    public void setRequirements(Object requirements) throws IllegalArgumentException {
        if (requirements instanceof Double[] arr && arr.length == 2) {
            this.minElevation = arr[0];
            this.maxElevation = arr[1];
        } else {
            throw new IllegalArgumentException("Invalid requirement type for ElevationFilter");
        }
    }

    @Override
    public void setLocations(List<LocationCell> locations) {
        this.locations = locations;
    }

    @Override
    public List<LocationCell> process() {
        if (!dataAvailable) {
            System.out.println("[ElevationFilter] No SRTM data available. Returning all locations.");
            return locations;
        }
        return locations.stream()
                .filter(cell -> {
                    double elev = getElevation(LocationUtils.getLatLng(cell));
                    boolean aboveMin = (minElevation == null) || (elev >= minElevation);
                    boolean belowMax = (maxElevation == null) || (elev <= maxElevation);
                    return aboveMin && belowMax;
                })
                .toList();
    }

    @Override
    public JPanel getParameterPanel() {
        JPanel panel = new JPanel(new GridLayout(1, 4));
        panel.add(new JLabel("Min Elevation (m):"));
        JTextField minField = new JTextField(minElevation == null ? "" : String.valueOf(minElevation));
        panel.add(minField);
        panel.add(new JLabel("Max Elevation (m):"));
        JTextField maxField = new JTextField(maxElevation == null ? "" : String.valueOf(maxElevation));
        panel.add(maxField);
        panel.putClientProperty("fields", new JTextField[]{minField, maxField});
        return panel;
    }

    @Override
    public String getRequirements() {
        return "Min: " + (minElevation == null ? "-inf" : minElevation) + ", Max: " + (maxElevation == null ? "+inf" : maxElevation);
    }

    /**
     * Returns the elevation in meters for the given LatLng, or Double.NaN if unavailable.
     */
    private double getElevation(LatLng coords) {
        // SRTM tiles are named like N33W117.hgt.gz for 33N, 117W
        int lat = (int) Math.floor(coords.lat);
        int lon = (int) Math.floor(coords.lng);
        String ns = lat >= 0 ? "N" : "S";
        String ew = lon >= 0 ? "E" : "W";
        String tileName = String.format("%s%02d%s%03d", ns, Math.abs(lat), ew, Math.abs(lon));
        short[] data = tileDataCache.get(tileName);
        if (data == null) return Double.NaN;
        // SRTM3: 1201x1201 grid, top-left is (lat+1, lon), bottom-right is (lat, lon+1)
        double latFrac = coords.lat - lat;
        double lonFrac = coords.lng - lon;
        int row = (int) Math.round((1 - latFrac) * (TILE_SIZE - 1));
        int col = (int) Math.round(lonFrac * (TILE_SIZE - 1));
        if (row < 0 || row >= TILE_SIZE || col < 0 || col >= TILE_SIZE) return Double.NaN;
        int idx = row * TILE_SIZE + col;
        short val = data[idx];
        if (val == -32768) return Double.NaN; // void value
        return val;
    }
} 