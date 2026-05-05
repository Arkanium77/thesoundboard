package app.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.io.InputStream;

public final class AppConfigLoader {
    private static final String CONFIG_RESOURCE = "/application.yml";

    private AppConfigLoader() {
    }

    public static AppConfig load() {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory()).findAndRegisterModules();
        try (InputStream inputStream = AppConfigLoader.class.getResourceAsStream(CONFIG_RESOURCE)) {
            if (inputStream == null) {
                throw new IllegalStateException("Missing configuration resource: " + CONFIG_RESOURCE);
            }

            JsonNode rootNode = mapper.readTree(inputStream);
            JsonNode appNode = rootNode.has("app") ? rootNode.get("app") : rootNode;
            return mapper.treeToValue(appNode, AppConfig.class);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load application configuration", exception);
        }
    }
}
