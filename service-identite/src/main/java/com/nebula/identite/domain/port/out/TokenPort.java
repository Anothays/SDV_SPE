package com.nebula.identite.domain.port.out;

public interface TokenPort {

    String issue(String subject, String username, String role);
}
