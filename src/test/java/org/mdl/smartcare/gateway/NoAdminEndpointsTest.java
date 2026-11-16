package org.mdl.smartcare.gateway;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.EndpointId;
import org.springframework.boot.actuate.endpoint.web.PathMappedEndpoints;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerMapping;

/**
 * The gateway has no admin endpoints of its own.
 *
 * <ul>
 *   <li>Nothing is served under {@code /api/permissions}. The permission sync endpoints that were
 *       there needed no login, and they could create and delete Keycloak realm roles.
 *   <li>The {@code gateway} actuator endpoint, which can change routes, is not exposed.
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class NoAdminEndpointsTest {

  @Autowired private WebTestClient webTestClient;
  @Autowired private ApplicationContext applicationContext;
  @Autowired private PathMappedEndpoints pathMappedEndpoints;

  @Test
  void noControllerServesAPathUnderApiPermissions() {
    List<String> controllerPaths =
        applicationContext.getBeansOfType(RequestMappingHandlerMapping.class).values().stream()
            .flatMap(handlerMapping -> handlerMapping.getHandlerMethods().keySet().stream())
            .flatMap(mapping -> mapping.getPatternsCondition().getPatterns().stream())
            .map(pattern -> pattern.getPatternString())
            .toList();

    assertFalse(
        controllerPaths.stream().anyMatch(path -> path.startsWith("/api/permissions")),
        "A controller serves a path under /api/permissions: " + controllerPaths);
  }

  @Test
  void syncToKeycloak_isNotServed() {
    // A path that nothing serves is answered with an error by the gateway's exception handler,
    // never with 2xx.
    webTestClient
        .post()
        .uri("/api/permissions/sync-to-keycloak")
        .exchange()
        .expectStatus()
        .value(status -> assertTrue(status >= 400));
  }

  @Test
  void keycloakTestConnection_isNotServed() {
    webTestClient
        .get()
        .uri("/api/permissions/keycloak/test-connection")
        .exchange()
        .expectStatus()
        .value(status -> assertTrue(status >= 400));
  }

  @Test
  void gatewayActuatorEndpoint_isNotExposed() {
    assertNull(pathMappedEndpoints.getPath(EndpointId.of("gateway")));
    assertNotNull(pathMappedEndpoints.getPath(EndpointId.of("health")));

    webTestClient
        .get()
        .uri("/actuator/gateway/routes")
        .exchange()
        .expectStatus()
        .value(status -> assertTrue(status >= 400));
  }

  @Test
  void healthActuatorEndpoint_isStillReachable() {
    webTestClient
        .get()
        .uri("/actuator/health")
        .exchange()
        .expectStatus()
        .value(status -> assertTrue(status == 200 || status == 503));
  }
}
