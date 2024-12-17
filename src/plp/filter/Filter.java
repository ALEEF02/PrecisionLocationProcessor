package plp.filter;

import java.util.List;
import plp.location.LocationCell;

public interface Filter {
    void setRequirements(Object requirements);
    void setLocations(List<LocationCell> locations);
    List<LocationCell> process();
}
