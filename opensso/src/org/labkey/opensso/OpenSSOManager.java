package org.labkey.opensso;

import com.iplanet.am.util.SystemProperties;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.PropertyManager.PropertyMap;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;

public class OpenSSOManager
{
    private static OpenSSOManager _instance;

    public static synchronized OpenSSOManager get()
    {
        if (_instance == null)
            _instance = new OpenSSOManager();
        return _instance;
    }


    public void initialize() throws Exception
    {
        Properties props = loadProps("AMClient.properties");
        replaceDefaults(props, getSystemSettings());  // System settings will replace values in static properties file
        SystemProperties.initializeProperties(props);
    }


    private Properties loadProps(String filename) throws IOException
    {
        InputStream is = null;

        try
        {
            is = OpenSSOManager.class.getResourceAsStream(filename);
            Properties props = new Properties();
            props.load(is);
            return props;
        }
        finally
        {
            if (null != is)
                is.close();
        }
    }


    private void replaceDefaults(Properties props, Map<String, String> replacements)
    {
        Set keys = props.keySet();

        for (Object o : keys)
        {
            String key = (String)o;
            String value = props.getProperty(key);

            if (value.startsWith("@") && value.endsWith("@"))
            {
                String defaultKey = value.substring(1, value.length() - 1);
                String defaultValue = replacements.get(defaultKey);
                props.setProperty(key, defaultValue);
            }
        }
    }


    private static final String OPENSSO_PROPERTIES_KEY = "OpenSSO";

    public Map<String, String> getSystemSettings() throws IOException
    {
        Properties fileProps = loadProps("clientDefault.properties");
        // dbProps will be null if settings have never been saved
        Map<String, String> dbProps = PropertyManager.getProperties(OPENSSO_PROPERTIES_KEY, false);
        // Map we will return -- sort by key
        Map<String, String> map = new TreeMap<String, String>();
        Set<Object> keys = fileProps.keySet();

        for (Object o : keys)
        {
            String key = (String)o;
            String value = (null != dbProps ? dbProps.get(key) : null);
            if (null != value)
                map.put(key, value);
            else
                map.put(key, fileProps.getProperty(key));
        }

        // TODO: Eliminate some of the properties we don't care about

        return map;
    }


    public void writeSystemSettings(Map<String, String> newSettings)
    {
        PropertyMap map = PropertyManager.getWritableProperties(OPENSSO_PROPERTIES_KEY, true);
        map.clear();
        map.putAll(newSettings);
        PropertyManager.saveProperties(map);
    }


    private static final String OPENSSO_SETTINGS = "OpenSSO_Settings";
    private static final String REFERRER_PREFIX = "Referrer_Prefix";
    private static String _referrerPrefix;

    static
    {
        loadReferrerPrefix();
    }

    private static void loadReferrerPrefix()
    {
        Map<String, String> map = PropertyManager.getProperties(OPENSSO_SETTINGS, false);

        if (null != map)
            _referrerPrefix = map.get(REFERRER_PREFIX);
    }

    public void saveReferrerPrefix(String prefix)
    {
        PropertyMap map = PropertyManager.getWritableProperties(OPENSSO_SETTINGS, true);
        map.put(REFERRER_PREFIX, prefix);
        PropertyManager.saveProperties(map);
        loadReferrerPrefix();
    }

    public String getReferrerPrefix()
    {
        return _referrerPrefix;
    }
}