package org.sona.controller;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Publishes the public key that Sona's access tokens are signed with.
 */
@RestController
@RequiredArgsConstructor
public final class JwksController {

    private final RSAKey signingKey;

    @GetMapping(ApiRoutes.JWKS)
    public Map<String, Object> publicKeys() {
        return new JWKSet(signingKey.toPublicJWK()).toJSONObject();
    }
}
