/*
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
package org.apache.aries.rsa.eapub;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.osgi.framework.*;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.RemoteServiceAdminEvent;
import org.osgi.service.remoteserviceadmin.RemoteServiceAdminListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventAdminPublisher implements RemoteServiceAdminListener {

    private static final Logger LOG = LoggerFactory.getLogger(EventAdminPublisher.class);

    private BundleContext context;

    public EventAdminPublisher(BundleContext context) {
        this.context = context;
    }

    private Map<String, Object> createProps(RemoteServiceAdminEvent rsae) {
        Map<String, Object> props = new HashMap<>();
        // bundle properties
        Bundle bundle = context.getBundle();
        String version = bundle.getHeaders().get("Bundle-Version");
        Version v = version != null ? new Version(version) : Version.emptyVersion;
        props.put("bundle", bundle);
        props.put("bundle.id", bundle.getBundleId());
        props.put("bundle.symbolicname", bundle.getSymbolicName());
        putIfNotNull(props, "bundle.version", v);

        // exception properties
        putIfNotNull(props, "cause", rsae.getException());

        // endpoint properties
        EndpointDescription endpoint = null;
        if (rsae.getImportReference() != null) {
            endpoint = rsae.getImportReference().getImportedEndpoint();
            putIfNotNull(props, "import.registration", endpoint);
        } else if (rsae.getExportReference() != null) {
            endpoint = rsae.getExportReference().getExportedEndpoint();
            putIfNotNull(props, "export.registration", endpoint);
        }

        if (endpoint != null) {
            putIfNotNull(props, "service.remote.id", endpoint.getServiceId());
            putIfNotNull(props, "service.remote.uuid", endpoint.getFrameworkUUID());
            putIfNotNull(props, "service.remote.uri", endpoint.getId());
            putIfNotNull(props, "objectClass", endpoint.getInterfaces().toArray(new String[0]));
            putIfNotNull(props, "service.imported.configs", endpoint.getConfigurationTypes());
        }

        // general properties
        props.put("timestamp", System.currentTimeMillis());
        props.put("event", rsae);

        return props;
    }

    @Override
    public void remoteAdminEvent(RemoteServiceAdminEvent rsae) {
        String type = getConstName(RemoteServiceAdminEvent.class, null, rsae.getType(), "UNKNOWN_EVENT");
        String topic = "org/osgi/service/remoteserviceadmin/" + type;
        Map<String, Object> props = createProps(rsae);
        Event event = new Event(topic, props);
        notifyEventAdmins(type, event);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void notifyEventAdmins(String type, Event event) {
        // get all EventAdmin service references
        ServiceReference[] refs = null;
        try {
            refs = context.getAllServiceReferences(EventAdmin.class.getName(), null);
        } catch (InvalidSyntaxException ise) {
            LOG.error("Failed to get EventAdmins: {}", ise.getMessage(), ise);
        }

        // post the event to each EventAdmin
        if (refs != null) {
            LOG.debug("Publishing event {} to {} EventAdmins", type, refs.length);
            for (ServiceReference serviceReference : refs) {
                EventAdmin eventAdmin = (EventAdmin) context.getService(serviceReference);
                try {
                    eventAdmin.postEvent(event);
                } finally {
                    if (eventAdmin != null) {
                        context.ungetService(serviceReference);
                    }
                }
            }
        }
    }

    private static <K, V> void putIfNotNull(Map<K, V> map, K key, V val) {
        if (val != null) {
            map.put(key, val);
        }
    }

    /**
     * Returns the name of the first constant field (public static final) in the given class
     * whose value is equal to the given value and name starts with the given prefix,
     * or a default value if it is not found.
     *
     * @param cls the class containing the constant
     * @param prefix the constant name prefix (or null for any name)
     * @param value the constant value
     * @param defaultValue a default value to return if the constant is not found
     * @return the constant name, or the default value if it is not found
     */
    private static String getConstName(Class<?> cls, String prefix, Object value, String defaultValue) {
        for (Field f : cls.getDeclaredFields()) {
            try {
                int m = f.getModifiers();
                if (Modifier.isFinal(m) && Modifier.isStatic(m) && Modifier.isPublic(m)
                        && Objects.equals(f.get(null), value)
                        && (prefix == null || f.getName().startsWith(prefix))) {
                    return f.getName();
                }
            } catch (IllegalAccessException ignore) {
            }
        }
        return defaultValue;
    }
}
