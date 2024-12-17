package plp.location;

public class LocationCell {
    private final Long h3Index;
    
    public LocationCell(Long h3Index) {
        this.h3Index = h3Index;
    }

    public Long getH3Index() {
        return h3Index;
    }

    @Override
    public String toString() {
        return String.valueOf(h3Index);
    }
}