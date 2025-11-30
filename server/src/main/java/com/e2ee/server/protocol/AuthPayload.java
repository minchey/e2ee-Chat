package com.e2ee.server.protocol;

public class AuthPayload {
    private String id;
    private String password;
    private String publicKey;

    public AuthPayload() {
    }

    public AuthPayload(String id, String password) {
        this.id = id;
        this.password = password;
    }

    public String getId() {
        return id;
    }

    public String getPassword() {
        return password;
    }

    public String getPublicKey() {
        return publicKey;
    }
}
