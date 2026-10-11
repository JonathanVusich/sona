package org.sona.config;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import static org.sona.utils.IDGenerator.uuidv7;

@Configuration
public class SigningKeyConfig {

    /**
     * A new key on every start. A restart invalidates the access tokens in use, and clients get new ones with their
     * refresh tokens, which are stored in the database.
     */
    @Bean
    public RSAKey signingKey() throws JOSEException {
        return new RSAKeyGenerator(2048)
                .keyID(uuidv7().toString())
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256)
                .generate();
    }

    @Bean
    public JwtEncoder jwtEncoder(final RSAKey signingKey) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(signingKey)));
    }
}
