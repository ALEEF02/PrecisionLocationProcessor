package plp.config;

import com.kaaz.configuration.ConfigurationOption;

public class Config {
	
	@ConfigurationOption
    public static int H3_RESOLUTION = 9; // Shared configuration variable for resolution

    @ConfigurationOption
    public static String OPENROUTESERVICE_API_KEY = ""; // OpenRouteService API key'
    
}
