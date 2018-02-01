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
package org.apache.aries.rsa.discovery.zookeeper.publish;

import java.util.Dictionary;
import java.util.List;

import org.apache.zookeeper.ZooKeeper;
import org.easymock.EasyMock;
import org.easymock.IMocksControl;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.remoteserviceadmin.EndpointEventListener;

import junit.framework.TestCase;

public class PublishingEndpointListenerFactoryTest extends TestCase {

    @SuppressWarnings("unchecked")
    public void testScope() {
        IMocksControl c = EasyMock.createNiceControl();

        BundleContext ctx = c.createMock(BundleContext.class);
        ZooKeeper zk = c.createMock(ZooKeeper.class);
        @SuppressWarnings("rawtypes")
        ServiceRegistration sreg = c.createMock(ServiceRegistration.class);

        PublishingEndpointListenerFactory eplf = new PublishingEndpointListenerFactory(zk, ctx);

        EasyMock.expect(ctx.registerService(EasyMock.eq(EndpointEventListener.class.getName()), EasyMock.eq(eplf),
                                            (Dictionary<String, String>)EasyMock.anyObject())).andReturn(sreg).once();

        EasyMock.expect(ctx.getProperty(EasyMock.eq("org.osgi.framework.uuid"))).andReturn("myUUID").anyTimes();

        c.replay();
        eplf.start();
        c.verify();

    }

    @SuppressWarnings("unchecked")
    public void testServiceFactory() {
        IMocksControl c = EasyMock.createNiceControl();

        BundleContext ctx = c.createMock(BundleContext.class);
        ZooKeeper zk = c.createMock(ZooKeeper.class);
        @SuppressWarnings("rawtypes")
        ServiceRegistration sreg = c.createMock(ServiceRegistration.class);

        PublishingEndpointListenerFactory eplf = new PublishingEndpointListenerFactory(zk, ctx);

        EasyMock.expect(ctx.registerService(EasyMock.eq(EndpointEventListener.class.getName()), EasyMock.eq(eplf),
                                (Dictionary<String, String>)EasyMock.anyObject())).andReturn(sreg).once();

        EasyMock.expect(ctx.getProperty(EasyMock.eq("org.osgi.framework.uuid"))).andReturn("myUUID").anyTimes();

        PublishingEndpointListener eli = c.createMock(PublishingEndpointListener.class);
        eli.close();
        EasyMock.expectLastCall().once();

        c.replay();
        eplf.start();

        PublishingEndpointListener service = eplf.getService(null, null);
        assertNotNull(service);
        assertTrue(service instanceof EndpointEventListener);

        List<PublishingEndpointListener> listeners = eplf.getListeners();
        assertEquals(1, listeners.size());
        assertEquals(service, listeners.get(0));

        eplf.ungetService(null, null, service);
        listeners = eplf.getListeners();
        assertEquals(0, listeners.size());

        eplf.ungetService(null, null, eli); // no call to close
        listeners.add(eli);
        eplf.ungetService(null, null, eli); // call to close
        listeners = eplf.getListeners();
        assertEquals(0, listeners.size());

        c.verify();
    }
}
