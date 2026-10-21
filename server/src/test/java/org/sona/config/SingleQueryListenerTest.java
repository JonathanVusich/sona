package org.sona.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.sona.model.Tables.ARTIST;
import static org.sona.utils.IDGenerator.uuidv7;

import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.sona.IntegrationTest;
import org.sona.auth.SessionState;
import org.sona.auth.TestTokens;
import org.sona.controller.ApiClient;
import org.sona.controller.ApiRoutes;
import org.sona.model.enums.UserRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.JwtEncoder;

@IntegrationTest
class SingleQueryListenerTest {

    @Autowired
    DSLContext dsl;

    @Autowired
    JwtEncoder encoder;

    @Value("${local.server.port}")
    int port;

    @Test
    void aPageReadInOneQueryIsServed() {
        final var token = new TestTokens(encoder).accessToken(uuidv7(), "alice", UserRole.USER, SessionState.ACTIVE);

        new ApiClient(port)
                .getWithToken(ApiRoutes.RELEASE_GROUPS, token)
                .expectStatus()
                .isOk();
    }

    @Test
    void aPagesReadThatRunsASecondQueryFails() {
        SingleQueryListener.startRequest("GET /api/test");
        try {
            loadArtist();

            assertThatThrownBy(this::loadArtist)
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("GET /api/test ran a second query");
        } finally {
            SingleQueryListener.finishRequest();
        }
    }

    @Test
    void queriesOutsideAPagesReadArentCounted() {
        assertThatCode(() -> {
                    loadArtist();
                    loadArtist();
                })
                .doesNotThrowAnyException();
    }

    private void loadArtist() {
        dsl.selectFrom(ARTIST).where(ARTIST.ARTIST_ID.eq(uuidv7())).fetch();
    }
}
