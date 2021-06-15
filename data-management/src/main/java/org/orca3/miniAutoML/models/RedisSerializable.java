package org.orca3.miniAutoML.models;

import java.util.Map;

public interface RedisSerializable {
    Map<String, String> toRedisHash();
}
