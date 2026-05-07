package com.mdt.jump.api;

import java.util.Optional;

public interface JumpComIdApi {
    ComIdRecord getOrCreate(String uuid);

    Optional<ComIdRecord> findByUuid(String uuid);

    Optional<ComIdRecord> findByComId(String comId);
}
