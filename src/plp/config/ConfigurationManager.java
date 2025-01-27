package plp.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.reflections.Reflections;
import org.reflections.scanners.FieldAnnotationsScanner;
import org.reflections.util.ClasspathHelper;

import com.kaaz.configuration.ConfigurationBuilder;
import com.kaaz.configuration.ConfigurationOption;
import com.kaaz.configuration.ConfigurationProperties;
import com.kaaz.configuration.IConfigurationParser;

public class ConfigurationManager {

	private final File configFile;
    private final ConfigurationProperties properties;
    private final Class configclass;
    private final Map<Class<?>, IConfigurationParser> configurationParsers = new HashMap<>();

    public ConfigurationManager(Class configclass, File configFile) {
        this.configFile = configFile;
        this.configclass = configclass;
        this.properties = new ConfigurationProperties();
        loadParsers();
    }

    /**
     * loads the configuration parsers for each type
     */
    private void loadParsers() {
        Reflections reflections = new Reflections("com.kaaz.configuration.types");
        Set<Class<? extends IConfigurationParser>> classes = reflections.getSubTypesOf(IConfigurationParser.class);
        for (Class<? extends IConfigurationParser> parserclass : classes) {
            try {
                Class<?> parserType = (Class<?>) ((ParameterizedType) parserclass.getGenericInterfaces()[0]).getActualTypeArguments()[0];
                IConfigurationParser parserInstance = parserclass.getConstructor().newInstance();
                configurationParsers.put(parserType, parserInstance);
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Writes the config back to the disk
     *-
     * @throws IOException
     */
    public void write() throws Exception {
        if (configFile == null) throw new IllegalStateException("File not initialized");
        Reflections reflections = new Reflections(new org.reflections.util.ConfigurationBuilder()
                .setUrls(ClasspathHelper.forClass(configclass))
                .addScanners(new FieldAnnotationsScanner()));
        Set<Field> options = reflections.getFieldsAnnotatedWith(ConfigurationOption.class);
        if (configFile.exists()) {
            properties.load(new FileInputStream(configFile));
        } else {
        	new ConfigurationBuilder(configclass, configFile).build(true);
        	return;
        }

        options.forEach(o -> {
            ConfigurationOption option = o.getAnnotation(ConfigurationOption.class);
            try {
                String variableName = o.getName().toLowerCase();
                Object value = o.get(null);
                if (configurationParsers.containsKey(value.getClass())) {
                    properties.setProperty(variableName, configurationParsers.get(value.getClass()).toStringValue(o.get(null)));
                } else {
                    throw new Exception("Unknown Configuration Type");
                }
            } catch (IllegalAccessException e) {
                System.out.println("Could not load configuration, IllegalAccessException");
            } catch (Exception e) {
                System.out.println(e);
            }
        });
        properties.store(new FileOutputStream(configFile), null);
    }

}
