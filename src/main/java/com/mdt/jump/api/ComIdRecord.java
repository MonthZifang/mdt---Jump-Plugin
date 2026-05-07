package com.mdt.jump.api;

import java.util.Objects;

public final class ComIdRecord {
    private final String uuid;
    private final String comId;

    public ComIdRecord(String uuid, String comId) {
        this.uuid = Objects.requireNonNull(uuid, "uuid");
        this.comId = Objects.requireNonNull(comId, "comId");
    }

    public String getUuid() {
        return uuid;
    }

    public String getComId() {
        return comId;
    }
}
