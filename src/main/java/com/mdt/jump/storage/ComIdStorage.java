package com.mdt.jump.storage;

import com.mdt.jump.api.ComIdRecord;
import java.util.Optional;

public interface ComIdStorage {
    Optional<String> findComIdByUuid(String uuid) throws Exception;

    Optional<String> findUuidByComId(String comId) throws Exception;

    void save(ComIdRecord record) throws Exception;
}
