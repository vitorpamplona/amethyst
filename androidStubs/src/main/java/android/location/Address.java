package android.location;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** JVM stand-in for android.location.Address. Pure data. */
public class Address {
    private final Locale locale;
    private final List<String> addressLines = new ArrayList<>();
    private String countryName;
    private String countryCode;
    private String adminArea;
    private String subAdminArea;
    private String locality;
    private String subLocality;
    private String thoroughfare;
    private String postalCode;
    private String featureName;

    public Address(Locale locale) { this.locale = locale; }

    public Locale getLocale() { return locale; }

    public String getAddressLine(int index) {
        return index >= 0 && index < addressLines.size() ? addressLines.get(index) : null;
    }

    public void setAddressLine(int index, String line) {
        while (addressLines.size() <= index) addressLines.add(null);
        addressLines.set(index, line);
    }

    public int getMaxAddressLineIndex() { return addressLines.size() - 1; }

    public String getCountryName() { return countryName; }

    public void setCountryName(String value) { countryName = value; }

    public String getCountryCode() { return countryCode; }

    public void setCountryCode(String value) { countryCode = value; }

    public String getAdminArea() { return adminArea; }

    public void setAdminArea(String value) { adminArea = value; }

    public String getSubAdminArea() { return subAdminArea; }

    public void setSubAdminArea(String value) { subAdminArea = value; }

    public String getLocality() { return locality; }

    public void setLocality(String value) { locality = value; }

    public String getSubLocality() { return subLocality; }

    public void setSubLocality(String value) { subLocality = value; }

    public String getThoroughfare() { return thoroughfare; }

    public void setThoroughfare(String value) { thoroughfare = value; }

    public String getPostalCode() { return postalCode; }

    public void setPostalCode(String value) { postalCode = value; }

    public String getFeatureName() { return featureName; }

    public void setFeatureName(String value) { featureName = value; }
}
