/*
 * Copyright 2021-present Open Networking Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.student.authenticationportal;

import com.google.common.collect.ImmutableSet;
import org.onlab.packet.*;
import org.onosproject.cfg.ComponentConfigService;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Host;
import org.onosproject.net.HostId;
import org.onosproject.net.PortNumber;
import org.onosproject.net.flow.*;
import org.onosproject.net.flowobjective.DefaultForwardingObjective;
import org.onosproject.net.flowobjective.FlowObjectiveService;
import org.onosproject.net.flowobjective.ForwardingObjective;
import org.onosproject.net.host.HostService;
import org.onosproject.net.packet.*;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.Dictionary;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

import static org.onlab.util.Tools.get;

/**
 * Skeletal ONOS application component.
 */
@Component(immediate = true)
public class AppComponent {

    private final Logger log = LoggerFactory.getLogger(getClass());

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected FlowRuleService flowRuleService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected FlowObjectiveService flowObjectiveService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected HostService hostService;

    private ReactivePacketProcessor processor = new ReactivePacketProcessor();
    private ApplicationId appId;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected ComponentConfigService cfgService;

    private AuthenticationHandler authenticationHandler = AuthenticationHandler.getInstance();

    private long PORTAL_PORT = 1;
    private MacAddress PORTAL_MAC = MacAddress.valueOf("00:00:00:00:00:01");
    private IpAddress PORTAL_IP = IpAddress.valueOf("10.0.0.1");

    @Activate
    protected void activate() {
        cfgService.registerProperties(getClass());
        log.info("Started");
    }

    @Deactivate
    protected void deactivate() {
        cfgService.unregisterProperties(getClass(), false);
        log.info("Stopped");
    }

    private class ReactivePacketProcessor implements PacketProcessor {

        @Override
        public void process(PacketContext context) {

            //If the packet has been handled don't do anything (this occurs when the user is already authenticated)
            if(context.isHandled()) {
                return;
            }

            InboundPacket pkt = context.inPacket();
            Ethernet ethPkt = pkt.parsed();
            //Discard if packet is null.
            if (ethPkt == null) {
                return;
            }

            String clientId = ethPkt.getSourceMAC().toString();
            authenticationHandler.addClient(clientId);


            //If it isnt an IPv4 packet we don't bother
            if (ethPkt.getEtherType() != Ethernet.TYPE_IPV4) return;
            IPv4 ipv4Packet = (IPv4) ethPkt.getPayload();

            //Create the Traffic Selector and start adding criteria.
            TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
            selector.matchEthType(Ethernet.TYPE_IPV4);
            int srcPort;
            int[] dstPorts;


            //Next we only want to match HTTP/S traffic, as we want to forward these packets to our authentication service
            //Current theory, no need to install a rule here and that the individual packets can be forwarded

            //Handle HTTP packets here.
            if (ipv4Packet.getProtocol() == IPv4.PROTOCOL_TCP) {
                TCP tcpPkt = (TCP) ipv4Packet.getPayload();
                srcPort = tcpPkt.getSourcePort();
                dstPorts = new int[]{80, 8008, 8080, 443, 8443};
                //Very important here: Specify the protocol (TCP, UDP) before specifying transport port.
                //Specifying only the transport port WILL NOT work.

                //Loop over all http/s ports
                for (int dstPort : dstPorts) {
                    selector.matchIPProtocol(IPv4.PROTOCOL_TCP).matchTcpSrc(TpPort.tpPort(srcPort))
                            .matchTcpDst(TpPort.tpPort(dstPort));
                }
            }
            selector.build();

        }
    }

    private void forwardPacketToPortal(PacketContext context, TrafficSelector selector) {
        //We forward all packets which match our selector (HTTP/S Traffic, to our portal, this rule is temporary).

        TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                .setEthDst(PORTAL_MAC)
                .setIpDst(PORTAL_IP)
                .setOutput(PortNumber.portNumber(PORTAL_PORT))
                .build();

        ForwardingObjective forwardingObjective = DefaultForwardingObjective.builder().withTreatment(treatment)
                .withSelector(selector)
                .withPriority(100)
                .withFlag(ForwardingObjective.Flag.VERSATILE)
                .fromApp(appId)
                .makeTemporary(10)
                .add();
        flowObjectiveService.forward(context.inPacket().receivedFrom().deviceId(), forwardingObjective);
        return;
    }
}
