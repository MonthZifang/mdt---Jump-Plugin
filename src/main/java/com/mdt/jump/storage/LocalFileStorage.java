package com.mdt.jump.storage;

import com.mdt.jump.api.ComIdRecord;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

public final class LocalFileStorage implements ComIdStorage {
    private final Path storageFile;
    private final Map<String, String> records = new LinkedHashMap<String, String>();

    public LocalFileStorage(Path storageFile) throws IOException {
        this.storageFile = storageFile;
        load();
    }

    public synchronized Map<String, String> snapshot() {
        return new LinkedHashMap<String, String>(records);
    }

    @Override
    public synchronized Optional<String> findComIdByUuid(String uuid) {
        return Optional.ofNullable(records.get(uuid));
    }

    @Override
    public synchronized Optional<String> findUuidByComId(String comId) {
        for (Map.Entry<String, String> entry : records.entrySet()) {
            if (entry.getValue().equalsIgnoreCase(comId)) {
                return Optional.of(entry.getKey());
            }
        }
        return Optional.empty();
    }

    @Override
    public synchronized void save(ComIdRecord record) throws IOException {
        records.put(record.getUuid(), record.getComId());
        flush();
    }

    private void load() throws IOException {
        records.clear();
        if (Files.notExists(storageFile)) {
            Files.createDirectories(storageFile.getParent());
            Files.createFile(storageFile);
            return;
        }

        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(storageFile, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        for (String key : properties.stringPropertyNames()) {
            records.put(key, properties.getProperty(key));
        }
    }

    private void flush() throws IOException {
        Files.createDirectories(storageFile.getParent());
        Properties properties = new Properties();
        for (Map.Entry<String, String> entry : records.entrySet()) {
            properties.setProperty(entry.getKey(), entry.getValue());
        }
        try (Writer writer = Files.newBufferedWriter(storageFile, StandardCharsets.UTF_8)) {
            properties.store(writer, "UUID -> com id");
        }
    }
}
