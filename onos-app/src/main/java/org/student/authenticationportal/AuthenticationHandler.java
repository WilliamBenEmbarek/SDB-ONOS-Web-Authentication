package org.student.authenticationportal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

public class AuthenticationHandler {
    private static AuthenticationHandler instance = null;

    private ConcurrentHashMap<String, Boolean> clients = new ConcurrentHashMap<String, Boolean>() {{
       put("00:00:00:00:00:01", true); //Web Portal
       put("00:00:00:00:00:03", true); //Random Web Service guest is trying to access
       put("00:00:00:00:00:04", true); //NAT Connection

    }};


    public void addClient(String id) {
        if (!clients.containsKey(id)) {
            clients.put(id, false);
        }
    }

    public void authenticateClient(String id) {
        addClient(id);
        clients.put(id, true);
    }

    public boolean isAuthenticated(String id) {
        return clients.getOrDefault(id, false);
    }

    public String DEBUGgetAuthenticated() {
        return clients.toString();
    }

    private void AuthenticationHandler() {

    }

    public static AuthenticationHandler getInstance() {
        if(instance == null) {
            instance = new AuthenticationHandler();
        }
        return instance;
    }
}