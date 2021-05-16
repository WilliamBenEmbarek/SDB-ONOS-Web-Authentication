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
import java.util.concurrent.ConcurrentHashMap;

import static org.onlab.util.Tools.get;

/**
 * Skeletal ONOS application component.
 */
@Component(immediate = true)
public class AppComponent {

    private final Logger log = LoggerFactory.getLogger(getClass());

    ConcurrentHashMap<DeviceId, ConcurrentHashMap<MacAddress,PortNumber>> switchTable = new ConcurrentHashMap<>();

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
        appId = coreService.registerApplication("org.student.lb");
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        selector.matchEthType(Ethernet.TYPE_IPV4);
        packetService.requestPackets(selector.build(), PacketPriority.REACTIVE, appId);
        packetService.addProcessor(processor, PacketProcessor.director(2));
        log.info("Started", appId.id());
    }

    @Deactivate
    protected void deactivate() {
        flowRuleService.removeFlowRulesById(appId);
        packetService.removeProcessor(processor);
        processor = null;log.info("Stopped");
    }

    private class ReactivePacketProcessor implements PacketProcessor {

        @Override
        public void process(PacketContext context) {
            InboundPacket pkt = context.inPacket();
            Ethernet ethPkt = pkt.parsed();

            //Discard if  packet is null.
            if (ethPkt == null) {
                log.info("Discarding null packet");
                return;
            }

            if(ethPkt.getEtherType() != Ethernet.TYPE_IPV4) return;
            log.info("Proccesing packet request.");
            log.info(authenticationHandler.DEBUGgetAuthenticated());
            /*
                Check if the host has been authenticated, if it has we just forward packets regular using the learning
                switch otherwise we drop all non HTTP packets and forward all HTTP packets to the authentication portal.
            */
            String packetMAC = ethPkt.getSourceMAC().toString();
            if (authenticationHandler.isAuthenticated(packetMAC)) {
                log.info("Client : " + ethPkt.getSourceMAC().toString() + " is already authenticated");
                learningSwitch(context, pkt ,ethPkt);
            } else {
                log.info("Client : " + ethPkt.getSourceMAC().toString() + " is not authenticated, forwarding packets to portal");
                forwardPacketToPortal(context, pkt ,ethPkt);
            }
        }
    }


    private void learningSwitch(PacketContext context, InboundPacket pkt, Ethernet ethPkt) {

        // First step is to check if the packet came from a newly discovered switch.
        // Create a new entry if required.
        DeviceId deviceId = pkt.receivedFrom().deviceId();
        if (!switchTable.containsKey(deviceId)){
            log.info("Adding new switch: "+deviceId.toString());
            ConcurrentHashMap<MacAddress, PortNumber> hostTable = new ConcurrentHashMap<>();
            switchTable.put(deviceId, hostTable);
        }

        // Now lets check if the source host is a known host. If it is not add it to the switchTable.
        ConcurrentHashMap<MacAddress,PortNumber> hostTable = switchTable.get(deviceId);
        MacAddress srcMac = ethPkt.getSourceMAC();
        if (!hostTable.containsKey(srcMac)){
            log.info("Adding new host: "+srcMac.toString()+" for switch "+deviceId.toString());
            hostTable.put(srcMac,pkt.receivedFrom().port());
            switchTable.replace(deviceId,hostTable);
        }

        // To take care of loops, we must drop the packet if the port from which it came from does not match the port that the source host should be attached to.
        if (!hostTable.get(srcMac).equals(pkt.receivedFrom().port())){
            log.info("Dropping packet to break loop");
            return;
        }

        // Now lets check if we know the destination host. If we do asign the correct output port.
        // By default set the port to FLOOD.
        MacAddress dstMac = ethPkt.getDestinationMAC();
        PortNumber outPort = PortNumber.FLOOD;
        if (hostTable.containsKey(dstMac)){
            outPort = hostTable.get(dstMac);
            log.info("Setting output port to: "+outPort);

        }

        //Generate the traffic selector based on the packet that arrived.
        TrafficSelector packetSelector = DefaultTrafficSelector.builder()
                .matchEthType(Ethernet.TYPE_IPV4)
                .matchEthDst(dstMac).build();

        TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                .setOutput(outPort).build();

        ForwardingObjective forwardingObjective = DefaultForwardingObjective.builder()
                .withSelector(packetSelector)
                .withTreatment(treatment)
                .withPriority(5000)
                .withFlag(ForwardingObjective.Flag.VERSATILE)
                .fromApp(appId)
                .makeTemporary(5000)
                .add();

        if (outPort != PortNumber.FLOOD) flowObjectiveService.forward(deviceId,forwardingObjective);
        context.treatmentBuilder().addTreatment(treatment);
        log.info("Sending packet");
        context.send();
    }

    private void forwardPacketToPortal(PacketContext context, InboundPacket pkt, Ethernet ethPkt) {
        IPv4 ipv4Packet = (IPv4) ethPkt.getPayload();
        //Create the Traffic Selector and start adding criteria.
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        selector.matchEthType(Ethernet.TYPE_IPV4);
        selector.matchIPProtocol((byte) 4);
        int srcPort;
        int[] dstPorts;


        //Next we only want to match HTTP/S traffic, as we want to forward these packets to our authentication service

        //Handle HTTP packets here.
        if (ipv4Packet.getProtocol() == IPv4.PROTOCOL_TCP) {
            log.info("Packet is of type IPv4");
            TCP tcpPkt = (TCP) ipv4Packet.getPayload();
            srcPort = tcpPkt.getSourcePort();
            dstPorts = new int[]{80};
            //uncomment below line once you figure out how to match several ports
            //dstPorts = new int[]{80, 8008, 8080, 443, 8443};
            //Very important here: Specify the protocol (TCP, UDP) before specifying transport port.
            //Specifying only the transport port WILL NOT work.

            //Loop over all http/s ports
            for (int dstPort : dstPorts) {
                selector.matchIPProtocol(IPv4.PROTOCOL_TCP).matchTcpSrc(TpPort.tpPort(srcPort))
                        .matchTcpDst(TpPort.tpPort(dstPort));
            }
        }
        //We forward all packets which match our selector (HTTP/S Traffic, to our portal, this rule is temporary).

        TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                .setEthDst(PORTAL_MAC)
                .setIpDst(PORTAL_IP)
                .setOutput(PortNumber.portNumber(PORTAL_PORT))
                .build();

        ForwardingObjective forwardingObjective = DefaultForwardingObjective.builder().withTreatment(treatment)
                .withSelector(selector.build())
                .withPriority(100)
                .withFlag(ForwardingObjective.Flag.VERSATILE)
                .fromApp(appId)
                .makeTemporary(60)
                .add();
        log.info("Adding flow objective " + forwardingObjective.toString());
        flowObjectiveService.forward(context.inPacket().receivedFrom().deviceId(), forwardingObjective);

        /*
            To simplify the process, install also the return route on the LB.
            Now we need to instruct the LB to change the srcIP and srcMAC from that of the serving server to that of the LB itself.
        */
        TrafficTreatment treatment2 = DefaultTrafficTreatment.builder()
                .setEthSrc(ethPkt.getDestinationMAC())
                .setIpSrc(IpAddress.valueOf(ipv4Packet.getDestinationAddress()))
                .setOutput(pkt.receivedFrom().port())
                .build();

        TrafficSelector.Builder selectorBuilder = DefaultTrafficSelector.builder();
        selectorBuilder.matchEthType(Ethernet.TYPE_IPV4)
                .matchIPDst(IpPrefix.valueOf(ipv4Packet.getSourceAddress(), IpPrefix.MAX_INET_MASK_LENGTH))
                .matchEthDst(ethPkt.getSourceMAC());

        ForwardingObjective forwardingObjective2 = DefaultForwardingObjective.builder().withTreatment(treatment2)
                .withSelector(selectorBuilder.build())
                .withPriority(100)
                .withFlag(ForwardingObjective.Flag.VERSATILE)
                .fromApp(appId)
                .makeTemporary(60)
                .add();
        log.info("Adding flow objective " + forwardingObjective2.toString());
        flowObjectiveService.forward(context.inPacket().receivedFrom().deviceId(), forwardingObjective2);
        return;
    }

}
