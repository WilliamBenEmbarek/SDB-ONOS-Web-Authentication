#!/usr/bin/python                                                                            
 
from mininet.topo import Topo
from mininet.net import Mininet
from mininet.node import RemoteController
from mininet.node import Host
from mininet.node import OVSKernelSwitch
from mininet.cli import CLI
from mininet.log import setLogLevel
from mininet.link import TCLink
from mininet.util import waitListening
from mininet.log import lg, info

class MyTopo(Topo):
	def __init__(self, **opts):
		Topo.__init__(self, **opts)

		PortalHost = self.addHost('portal', cls=Host, 	ip='10.0.0.1',   	mac='00:00:00:00:00:01')
		Guest      = self.addHost('guest', 	cls=Host, 	ip='10.0.0.2', 		mac='00:00:00:00:00:02')
		TestSite   = self.addHost('dst', 	cls=Host, 	ip='10.0.0.3', 	    mac='00:00:00:00:00:03')
		s1 = self.addSwitch('s1', cls=OVSKernelSwitch)

		self.addLink(PortalHost, s1, cls=TCLink)
		self.addLink(Guest,		 s1, cls=TCLink)
		self.addLink(TestSite,	 s1, cls=TCLink)
		

topos = { 'mytopo': ( lambda: MyTopo() ) }  # this makes it possible to run the mininet with the parameter "--topo mytopo"

class ONOSController (RemoteController):

    def __init__ (self):
        RemoteController.__init__(self,'ONOSController','127.0.0.1',6633)

controllers={'onos': ONOSController}   # this makes it possible to run mininet with the parameter "--controller onos"

if __name__ == '__main__':

    setLogLevel('info')   
    topo = MyTopo()
    net = Mininet(topo=topo,controller=None,link=TCLink, listenPort=6634)
   
    c0 = ONOSController()
    net.addController(c0)
    switch = net.switches[ 0 ]



    portalHost = net.getNodeByName('portal')
    testSite = net.getNodeByName('dst')

    # connect the switch to localhost, so the web portal can connect to onos
    #_intf = Intf('enp0s8', node=switch)
    
    net.addNAT(ip='10.0.0.4', mac='00:00:00:00:00:04').configDefault()
    
    net.start()
    portalHost.cmd('cd h1')
    portalHost.cmd('sudo ./onos-portal &')
    testSite.cmd('cd h3')
    testSite.cmd('sudo python3 -m http.server 80 &')

    CLI(net)
    net.stop()

