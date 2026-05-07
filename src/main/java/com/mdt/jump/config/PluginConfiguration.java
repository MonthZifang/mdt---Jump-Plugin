package com.mdt.jump.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.Properties;

public final class PluginConfiguration {
    private static final String DEFAULT_RESOURCE = "jump-plugin.properties";
    private static final String DEFAULT_CONFIG_FILE = "plugin-config.properties";

    private final Path dataDirectory;
    private final Path configFile;
    private final boolean localStorageEnabled;
    private final Path localStorageFile;
    private final boolean remoteStorageEnabled;
    private final String remoteDriverClassName;
    private final String remoteJdbcUrl;
    private final String remoteUsername;
    private final String remotePassword;
    private final String remoteTableName;
    private final boolean apiEnabled;
    private final String apiHost;
    private final int apiPort;
    private final boolean apiRequireToken;
    private final String apiToken;

    private PluginConfiguration(
        Path dataDirectory,
        Path configFile,
        boolean localStorageEnabled,
        Path localStorageFile,
        boolean remoteStorageEnabled,
        String remoteDriverClassName,
        String remoteJdbcUrl,
        String remoteUsername,
        String remotePassword,
        String remoteTableName,
        boolean apiEnabled,
        String apiHost,
        int apiPort,
        boolean apiRequireToken,
        String apiToken
    ) {
        this.dataDirectory = dataDirectory;
        this.configFile = configFile;
        this.localStorageEnabled = localStorageEnabled;
        this.localStorageFile = localStorageFile;
        this.remoteStorageEnabled = remoteStorageEnabled;
        this.remoteDriverClassName = remoteDriverClassName;
        this.remoteJdbcUrl = remoteJdbcUrl;
        this.remoteUsername = remoteUsername;
        this.remotePassword = remotePassword;
        this.remoteTableName = remoteTableName;
        this.apiEnabled = apiEnabled;
        this.apiHost = apiHost;
        this.apiPort = apiPort;
        this.apiRequireToken = apiRequireToken;
        this.apiToken = apiToken;
    }

    public static PluginConfiguration load(Path dataDirectory) throws IOException {
        Files.createDirectories(dataDirectory);

        Path configFile = dataDirectory.resolve(DEFAULT_CONFIG_FILE);
        if (Files.notExists(configFile)) {
            copyDefaultConfig(configFile);
        }

        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }

        boolean localEnabled = readBoolean(properties, "本地存储.启用", "storage.local.enabled", true);
        Path localFile = resolvePath(
            dataDirectory,
            readString(properties, "本地存储.文件", "storage.local.path", "com-id-storage.properties")
        );

        boolean remoteEnabled = readBoolean(properties, "远程数据库.启用", "storage.remote.enabled", false);
        String remoteDriver = readString(properties, "远程数据库.驱动类名", "storage.remote.driverClassName", "");
        String remoteJdbcUrl = readString(properties, "远程数据库.JDBC地址", "storage.remote.jdbcUrl", "");
        String remoteUsername = readString(properties, "远程数据库.用户名", "storage.remote.username", "");
        String remotePassword = readString(properties, "远程数据库.密码", "storage.remote.password", "");
        String remoteTableName = sanitizeTableName(readString(properties, "远程数据库.表名", "storage.remote.tableName", "jump_com_ids"));

        if (remoteEnabled && remoteJdbcUrl.isEmpty()) {
            throw new IllegalArgumentException("启用远程数据库时必须提供 远程数据库.JDBC地址。");
        }

        boolean apiEnabled = readBoolean(properties, "接口服务.启用", "api.enabled", true);
        String apiHost = readString(properties, "接口服务.主机", "api.host", "127.0.0.1");
        int apiPort = readInt(properties, "接口服务.端口", "api.port", 19115);
        boolean apiRequireToken = readBoolean(properties, "接口服务.验证令牌", "api.requireToken", false);
        String apiToken = readString(properties, "接口服务.令牌", "api.token", "");

        if (apiRequireToken && apiToken.isEmpty()) {
            throw new IllegalArgumentException("接口服务.验证令牌=true 时必须配置 接口服务.令牌。");
        }

        return new PluginConfiguration(
            dataDirectory,
            configFile,
            localEnabled,
            localFile,
            remoteEnabled,
            remoteDriver,
            remoteJdbcUrl,
            remoteUsername,
            remotePassword,
            remoteTableName,
            apiEnabled,
            apiHost,
            apiPort,
            apiRequireToken,
            apiToken
        );
    }

    private static void copyDefaultConfig(Path configFile) throws IOException {
        try (InputStream inputStream = PluginConfiguration.class.getClassLoader().getResourceAsStream(DEFAULT_RESOURCE)) {
            if (inputStream == null) {
                throw new IOException("找不到默认配置资源: " + DEFAULT_RESOURCE);
            }
            Files.createDirectories(Objects.requireNonNull(configFile.getParent(), "configFile parent"));
            Files.copy(inputStream, configFile);
        }
    }

    private static boolean readBoolean(Properties properties, String chineseKey, String englishKey, boolean defaultValue) {
        String raw = readString(properties, chineseKey, englishKey, null);
        return raw == null ? defaultValue : Boolean.parseBoolean(raw);
    }

    private static int readInt(Properties properties, String chineseKey, String englishKey, int defaultValue) {
        String raw = readString(properties, chineseKey, englishKey, null);
        return raw == null || raw.trim().isEmpty() ? defaultValue : Integer.parseInt(raw.trim());
    }

    private static String readString(Properties properties, String chineseKey, String englishKey, String defaultValue) {
        String value = properties.getProperty(chineseKey);
        if (value == null && englishKey != null) {
            value = properties.getProperty(englishKey);
        }
        if (value == null) {
            return defaultValue;
        }
        return value.trim();
    }

    private static Path resolvePath(Path baseDirectory, String configuredPath) {
        Path path = Paths.get(configuredPath.trim());
        if (path.isAbsolute()) {
            return path.normalize();
        }
        return baseDirectory.resolve(path).normalize();
    }

    private static String sanitizeTableName(String tableName) {
        String sanitized = tableName.trim().replaceAll("[^a-zA-Z0-9_]", "_");
        if (sanitized.isEmpty()) {
            throw new IllegalArgumentException("远程数据库.表名 不能为空。");
        }
        return sanitized;
    }

    public Path getDataDirectory() {
        return dataDirectory;
    }

    public Path getConfigFile() {
        return configFile;
    }

    public boolean isLocalStorageEnabled() {
        return localStorageEnabled;
    }

    public Path getLocalStorageFile() {
        return localStorageFile;
    }

    public boolean isRemoteStorageEnabled() {
        return remoteStorageEnabled;
    }

    public String getRemoteDriverClassName() {
        return remoteDriverClassName;
    }

    public String getRemoteJdbcUrl() {
        return remoteJdbcUrl;
    }

    public String getRemoteUsername() {
        return remoteUsername;
    }

    public String getRemotePassword() {
        return remotePassword;
    }

    public String getRemoteTableName() {
        return remoteTableName;
    }

    public boolean isApiEnabled() {
        return apiEnabled;
    }

    public String getApiHost() {
        return apiHost;
    }

    public int getApiPort() {
        return apiPort;
    }

    public boolean isApiRequireToken() {
        return apiRequireToken;
    }

    public String getApiToken() {
        return apiToken;
    }
}
