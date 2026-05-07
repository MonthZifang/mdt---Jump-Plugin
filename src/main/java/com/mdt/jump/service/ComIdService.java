package com.mdt.jump.service;

import com.mdt.jump.api.ComIdRecord;
import com.mdt.jump.api.JumpComIdApi;
import com.mdt.jump.config.PluginConfiguration;
import com.mdt.jump.storage.LocalFileStorage;
import com.mdt.jump.storage.RemoteDatabaseStorage;
import java.security.SecureRandom;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class ComIdService implements JumpComIdApi {
    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    private final LocalFileStorage localStorage;
    private final RemoteDatabaseStorage remoteStorage;
    private final Map<String, ComIdRecord> uuidIndex = new ConcurrentHashMap<String, ComIdRecord>();
    private final Map<String, ComIdRecord> comIdIndex = new ConcurrentHashMap<String, ComIdRecord>();
    private final SecureRandom secureRandom = new SecureRandom();
    private final Object createLock = new Object();

    public ComIdService(PluginConfiguration configuration) throws Exception {
        this.localStorage = configuration.isLocalStorageEnabled() ? new LocalFileStorage(configuration.getLocalStorageFile()) : null;
        this.remoteStorage = configuration.isRemoteStorageEnabled() ? new RemoteDatabaseStorage(configuration) : null;
        preloadLocalRecords();
    }

    @Override
    public ComIdRecord getOrCreate(String uuid) {
        String normalizedUuid = normalizeUuid(uuid);
        Optional<ComIdRecord> existing = findByUuid(normalizedUuid);
        if (existing.isPresent()) {
            return existing.get();
        }

        synchronized (createLock) {
            existing = findByUuid(normalizedUuid);
            if (existing.isPresent()) {
                return existing.get();
            }

            ComIdRecord created = new ComIdRecord(normalizedUuid, generateUniqueComId());
            persist(created);
            cache(created);
            return created;
        }
    }

    @Override
    public Optional<ComIdRecord> findByUuid(String uuid) {
        String normalizedUuid = normalizeUuid(uuid);

        ComIdRecord cached = uuidIndex.get(normalizedUuid);
        if (cached != null) {
            return Optional.of(cached);
        }

        try {
            if (localStorage != null) {
                Optional<String> local = localStorage.findComIdByUuid(normalizedUuid);
                if (local.isPresent()) {
                    return Optional.of(cache(new ComIdRecord(normalizedUuid, local.get())));
                }
            }

            if (remoteStorage != null) {
                Optional<String> remote = remoteStorage.findComIdByUuid(normalizedUuid);
                if (remote.isPresent()) {
                    ComIdRecord record = cache(new ComIdRecord(normalizedUuid, remote.get()));
                    if (localStorage != null) {
                        localStorage.save(record);
                    }
                    return Optional.of(record);
                }
            }
        } catch (Exception exception) {
            throw new IllegalStateException("查询 UUID 对应 com id 失败。", exception);
        }

        return Optional.empty();
    }

    @Override
    public Optional<ComIdRecord> findByComId(String comId) {
        String normalizedComId = normalizeComId(comId);

        ComIdRecord cached = comIdIndex.get(normalizedComId);
        if (cached != null) {
            return Optional.of(cached);
        }

        try {
            if (localStorage != null) {
                Optional<String> uuid = localStorage.findUuidByComId(normalizedComId);
                if (uuid.isPresent()) {
                    return Optional.of(cache(new ComIdRecord(uuid.get(), normalizedComId)));
                }
            }

            if (remoteStorage != null) {
                Optional<String> uuid = remoteStorage.findUuidByComId(normalizedComId);
                if (uuid.isPresent()) {
                    ComIdRecord record = cache(new ComIdRecord(uuid.get(), normalizedComId));
                    if (localStorage != null) {
                        localStorage.save(record);
                    }
                    return Optional.of(record);
                }
            }
        } catch (Exception exception) {
            throw new IllegalStateException("反查 com id 对应 UUID 失败。", exception);
        }

        return Optional.empty();
    }

    public boolean isLocalStorageEnabled() {
        return localStorage != null;
    }

    public boolean isRemoteStorageEnabled() {
        return remoteStorage != null;
    }

    private void preloadLocalRecords() {
        if (localStorage == null) {
            return;
        }
        for (Map.Entry<String, String> entry : localStorage.snapshot().entrySet()) {
            cache(new ComIdRecord(entry.getKey(), entry.getValue()));
        }
    }

    private void persist(ComIdRecord record) {
        try {
            if (localStorage != null) {
                localStorage.save(record);
            }
            if (remoteStorage != null) {
                remoteStorage.save(record);
            }
        } catch (Exception exception) {
            throw new IllegalStateException("保存 UUID 与 com id 绑定关系失败。", exception);
        }
    }

    private ComIdRecord cache(ComIdRecord record) {
        uuidIndex.put(record.getUuid(), record);
        comIdIndex.put(record.getComId(), record);
        return record;
    }

    private String generateUniqueComId() {
        for (int attempt = 0; attempt < 5000; attempt++) {
            String candidate = randomComId();
            if (!comIdExists(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("无法生成唯一的四位 com id。");
    }

    private boolean comIdExists(String comId) {
        if (comIdIndex.containsKey(comId)) {
            return true;
        }

        try {
            if (localStorage != null && localStorage.findUuidByComId(comId).isPresent()) {
                return true;
            }
            if (remoteStorage != null && remoteStorage.findUuidByComId(comId).isPresent()) {
                return true;
            }
        } catch (Exception exception) {
            throw new IllegalStateException("校验 com id 唯一性失败。", exception);
        }

        return false;
    }

    private String randomComId() {
        char[] value = new char[4];
        for (int index = 0; index < value.length; index++) {
            value[index] = ALPHABET[secureRandom.nextInt(ALPHABET.length)];
        }
        return new String(value);
    }

    private String normalizeUuid(String uuid) {
        if (uuid == null || uuid.trim().isEmpty()) {
            throw new IllegalArgumentException("UUID 不能为空。");
        }
        return uuid.trim();
    }

    private String normalizeComId(String comId) {
        if (comId == null || comId.trim().isEmpty()) {
            throw new IllegalArgumentException("com id 不能为空。");
        }
        return comId.trim().toUpperCase();
    }
}
