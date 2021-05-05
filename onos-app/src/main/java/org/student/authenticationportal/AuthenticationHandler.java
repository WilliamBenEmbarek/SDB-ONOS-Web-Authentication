package org.student.authenticationportal;

import java.util.ArrayList;
import java.util.HashMap;

public class AuthenticationHandler {
    private static AuthenticationHandler instance = null;

    private HashMap<String, Boolean> clients = new HashMap<>();


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
        return clients.get(id);
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