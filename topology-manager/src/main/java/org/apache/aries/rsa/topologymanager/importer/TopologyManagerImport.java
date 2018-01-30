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
package org.apache.aries.rsa.topologymanager.importer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.osgi.framework.BundleContext;
import org.osgi.framework.hooks.service.FindHook;
import org.osgi.framework.hooks.service.ListenerHook;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.EndpointListener;
import org.osgi.service.remoteserviceadmin.ImportReference;
import org.osgi.service.remoteserviceadmin.ImportRegistration;
import org.osgi.service.remoteserviceadmin.RemoteServiceAdmin;
import org.osgi.service.remoteserviceadmin.RemoteServiceAdminEvent;
import org.osgi.service.remoteserviceadmin.RemoteServiceAdminListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Listens for remote endpoints using the EndpointListener interface and the EndpointListenerManager.
 * Listens for local service interests using the ListenerHookImpl that calls back through the
 * ServiceInterestListener interface.
 * Manages local creation and destruction of service imports using the available RemoteServiceAdmin services.
 */
public class TopologyManagerImport implements EndpointListener, RemoteServiceAdminListener, ServiceInterestListener {

    private static final Logger LOG = LoggerFactory.getLogger(TopologyManagerImport.class);
    private ExecutorService execService;

    private final EndpointListenerManager endpointListenerManager;
    private final BundleContext bctx;
    private Set<RemoteServiceAdmin> rsaSet;
    private final ListenerHookImpl listenerHook;
    private RSFindHook findHook;

    /**
     * Contains an instance of the Class Import Interest for each distinct import request. If the same filter
     * is requested multiple times the existing instance of the Object increments an internal reference
     * counter. If an interest is removed, the related ServiceInterest object is used to reduce the reference
     * counter until it reaches zero. in this case the interest is removed.
     */
    private final ReferenceCounter<String> importInterestsCounter = new ReferenceCounter<String>();

    /**
     * List of Endpoints by matched filter that were reported by the EndpointListener and can be imported
     */
    private final MultiMap<EndpointDescription> importPossibilities
        = new MultiMap<EndpointDescription>();

    /**
     * List of already imported Endpoints by their matched filter
     */
    private final MultiMap<ImportRegistration> importedServices
        = new MultiMap<ImportRegistration>();
    
    public TopologyManagerImport(BundleContext bc) {
        this.rsaSet = new HashSet<RemoteServiceAdmin>();
        bctx = bc;
        endpointListenerManager = new EndpointListenerManager(bctx, this);
        execService = new ThreadPoolExecutor(5, 10, 50, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
        listenerHook = new ListenerHookImpl(bc, this);
        findHook = new RSFindHook(bc, this);
    }
    
    public void start() {
        bctx.registerService(RemoteServiceAdminListener.class, this, null);
        bctx.registerService(ListenerHook.class, listenerHook, null);
        bctx.registerService(FindHook.class, findHook, null);
        endpointListenerManager.start();
    }

    public void stop() {
        endpointListenerManager.stop();
        execService.shutdown();
        // this is called from Activator.stop(), which implicitly unregisters our registered services
    }

    @Override
    public void addServiceInterest(String filter) {
        if (importInterestsCounter.add(filter) == 1) {
            endpointListenerManager.extendScope(filter);
        }
    }

    @Override
    public void removeServiceInterest(String filter) {
        if (importInterestsCounter.remove(filter) == 0) {
            LOG.debug("last reference to import interest is gone -> removing interest filter: {}", filter);
            endpointListenerManager.reduceScope(filter);
        }
    }

    @Override
    public void endpointAdded(EndpointDescription endpoint, String filter) {
        LOG.debug("Endpoint added for filter {}, endpoint {}", filter, endpoint);
        importPossibilities.put(filter, endpoint);
        triggerImport(filter);
    }

    @Override
    public void endpointRemoved(EndpointDescription endpoint, String filter) {
        LOG.debug("Endpoint removed for filter {}, endpoint {}", filter, endpoint);
        importPossibilities.remove(filter, endpoint);
        triggerImport(filter);
    }

    public void add(RemoteServiceAdmin rsa) {
        rsaSet.add(rsa);
        for (String filter : importPossibilities.keySet()) {
            triggerImport(filter);
        }
    }
    
    public void remove(RemoteServiceAdmin rsa) {
        rsaSet.remove(rsa);
    }

    @Override
    public void remoteAdminEvent(RemoteServiceAdminEvent event) {
        if (event.getType() == RemoteServiceAdminEvent.IMPORT_UNREGISTRATION) {
            removeAndClose(event.getImportReference());
        }
    }

    private void triggerImport(final String filter) {
        LOG.debug("Import of a service for filter {} was queued", filter);
        if (!rsaSet.isEmpty()) {
            execService.execute(new Runnable() {
                public void run() {
                    doImport(filter);
                }
            });
        }
    }
    
    private void doImport(final String filter) {
        try {
            unexportNotAvailableServices(filter);
            importServices(filter);
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
        }
        // Notify EndpointListeners? NO!
    }

    private void importServices(String filter) {
        List<ImportRegistration> importRegistrations = importedServices.get(filter);
        for (EndpointDescription endpoint : importPossibilities.get(filter)) {
            // TODO but optional: if the service is already imported and the endpoint is still
            // in the list of possible imports check if a "better" endpoint is now in the list
            if (!alreadyImported(endpoint, importRegistrations)) {
                ImportRegistration ir = importService(endpoint);
                if (ir != null) {
                    // import was successful
                    importedServices.put(filter, ir);
                }
            }
        }
    }

    private boolean alreadyImported(EndpointDescription endpoint, List<ImportRegistration> importRegistrations) {
        if (importRegistrations != null) {
            for (ImportRegistration ir : importRegistrations) {
                if (endpoint.equals(ir.getImportReference().getImportedEndpoint())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Tries to import the service with each rsa until one import is successful
     *
     * @param endpoint endpoint to import
     * @return import registration of the first successful import
     */
    private ImportRegistration importService(EndpointDescription endpoint) {
        for (RemoteServiceAdmin rsa : rsaSet) {
            ImportRegistration ir = rsa.importService(endpoint);
            if (ir != null) {
                if (ir.getException() == null) {
                    LOG.debug("Service import was successful {}", ir);
                    return ir;
                } else {
                    LOG.info("Error importing service " + endpoint, ir.getException());
                }
            }
        }
        return null;
    }

    private void unexportNotAvailableServices(String filter) {
        List<ImportRegistration> importRegistrations = importedServices.get(filter);
        List<EndpointDescription> endpoints = importPossibilities.get(filter);
        for (ImportRegistration ir : importRegistrations) {
            EndpointDescription endpoint = ir.getImportReference().getImportedEndpoint();
            if (!endpoints.contains(endpoint)) {
                removeAndClose(ir.getImportReference());
            }
        }
    }

    private void removeAndClose(ImportReference ref) {
        List<ImportRegistration> removed = new ArrayList<ImportRegistration>();
        for (String key : importedServices.keySet()) {
            for (ImportRegistration ir : importedServices.get(key)) {
                if (ir.getImportReference().equals(ref)) {
                    removed.add(ir);
                    importedServices.remove(key, ir);
                }
            }
        }
        closeAll(removed);
    }

    private void closeAll(List<ImportRegistration> removed) {
        for (ImportRegistration ir : removed) {
            ir.close();
        }
    }
    
}
