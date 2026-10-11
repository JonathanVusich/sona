package org.sona.controller;

import com.nimbusds.jose.jwk.RSAKey;
import org.junit.jupiter.api.Test;
import org.sona.IntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

@IntegrationTest
class JwksControllerTest {

    @Autowired
    RSAKey signingKey;

    @Value("${local.server.port}")
    int port;

    @Test
    void publishesOnlyThePublicSigningKey() {
        new ApiClient(port).getAnonymously(ApiRoutes.JWKS)
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.keys.length()").isEqualTo(1)
                .jsonPath("$.keys[0].kid").isEqualTo(signingKey.getKeyID())
                .jsonPath("$.keys[0].n").isEqualTo(signingKey.getModulus().toString())
                .jsonPath("$.keys[0].d").doesNotExist();
    }
}
