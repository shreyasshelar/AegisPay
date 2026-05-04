package com.aegispay.common.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
public class JwtClaimsExtractor {

    private static final String CLAIM_USER_ID   = "aegispay_user_id";
    private static final String CLAIM_ROLE       = "aegispay_role";
    private static final String CLAIM_TENANT_ID  = "aegispay_tenant_id";
    private static final String CLAIM_ROLES      = "roles";

    public ActorContext.Actor extract(Jwt jwt) {
        String sub        = jwt.getSubject();
        String userId     = jwt.getClaimAsString(CLAIM_USER_ID);
        String role       = jwt.getClaimAsString(CLAIM_ROLE);
        String tenantId   = jwt.getClaimAsString(CLAIM_TENANT_ID);
        List<String> roles = Optional.ofNullable(jwt.getClaimAsStringList(CLAIM_ROLES))
                                     .orElse(Collections.emptyList());

        return ActorContext.Actor.builder()
                .userId(userId != null ? UUID.fromString(userId) : null)
                .externalId(sub)
                .role(role)
                .tenantId(tenantId)
                .authorities(roles)
                .build();
    }
}
