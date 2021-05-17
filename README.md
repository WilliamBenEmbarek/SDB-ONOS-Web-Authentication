# SDN ONOS Network Authentication Service

This repository contains my assignment for the course 34359 SDN: Software-defined-networking
The project revolved around creating a network authentication portal using the ONOS Platform.

To build the project the following commands are to be executed.

Requirements:
ONOS > 4.2
Golang > 1.16
Python = 2.7

## ONOS App
```bash
 cd onos-app
 mvn clean install -DskipTests
 onos-app localhost reinstall! target/authenticationportal-1.1-SNAPSHOT.oar
```

## Web Portal

First install Golang following the instructions here https://golang.org/doc/install
Then
```bash
 cd web-portal
 go build ./
```

A prebuilt binary of the portal is also provided which does not require go to be installed to run.

## Mininet

To run the mininet setup first copy the web-portal binary and public folder into mininet\_script/h1
Then run the mininet setup with
```
sudo python topo1.py
```
