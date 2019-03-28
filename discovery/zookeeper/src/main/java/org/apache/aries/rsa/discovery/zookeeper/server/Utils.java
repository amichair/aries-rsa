/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.aries.rsa.discovery.zookeeper.server;

import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * General purpose utility methods.
 */
public final class Utils {

    private Utils() {
        // prevent instantiation
    }

    /**
     * Remove entries whose values are empty from the given dictionary.
     *
     * @param dict a dictionary
     */
    public static void removeEmptyValues(Dictionary<String, ?> dict) {
        List<String> keysToRemove = new ArrayList<>();
        Enumeration<String> keys = dict.keys();
        while (keys.hasMoreElements()) {
            String key = keys.nextElement();
            Object value = dict.get(key);
            if (value instanceof String && "".equals(value)) {
                keysToRemove.add(key);
            }
        }
        for (String key : keysToRemove) {
            dict.remove(key);
        }
    }

    /**
     * Puts the given key-value pair in the given dictionary if the key does not
     * already exist in it or if its existing value is null.
     *
     * @param dict a dictionary
     * @param key the key
     * @param value the default value to set
     */
    public static void setDefault(Dictionary<String, String> dict, String key, String value) {
        if (dict.get(key) == null) {
            dict.put(key, value);
        }
    }

    /**
     * Converts the given Dictionary to a Map.
     *
     * @param dict a dictionary
     * @param <K> the key type
     * @param <V> the value type
     * @return the converted map, or an empty map if the given dictionary is null
     */
    public static <K, V> Map<K, V> toMap(Dictionary<K, V> dict) {
        Map<K, V> map = new HashMap<>();
        if (dict != null) {
            Enumeration<K> keys = dict.keys();
            while (keys.hasMoreElements()) {
                K key = keys.nextElement();
                map.put(key, dict.get(key));
            }
        }
        return map;
    }

    /**
     * Converts a Dictionary into a Properties instance.
     *
     * @param dict a dictionary
     * @param <K> the key type
     * @param <V> the value type
     * @return the properties
     */
    public static <K, V> Properties toProperties(Dictionary<K, V> dict) {
        Properties props = new Properties();
        for (Enumeration<K> e = dict.keys(); e.hasMoreElements();) {
            K key = e.nextElement();
            props.put(key, dict.get(key));
        }
        return props;
    }
}
