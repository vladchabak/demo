package com.localpro.chat;

import java.security.Principal;

record StompPrincipal(String name) implements Principal {
    @Override
    public String getName() { return name; }
}
