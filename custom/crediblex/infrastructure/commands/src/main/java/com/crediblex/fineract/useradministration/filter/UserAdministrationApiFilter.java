/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.crediblex.fineract.useradministration.filter;

import com.crediblex.fineract.useradministration.service.SuperUserAdministrationAuthorizationService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.data.ApiGlobalErrorResponse;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.useradministration.exception.UnAuthenticatedUserException;
import org.springframework.web.filter.OncePerRequestFilter;

@RequiredArgsConstructor
public class UserAdministrationApiFilter extends OncePerRequestFilter {

    private final SuperUserAdministrationAuthorizationService superUserAdministrationAuthorizationService;
    private final PlatformSecurityContext platformSecurityContext;

    @Override
    protected void doFilterInternal(final HttpServletRequest request, final HttpServletResponse response, final FilterChain filterChain)
            throws ServletException, IOException {
        if (!superUserAdministrationAuthorizationService.isUserAdministrationApiPath(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            superUserAdministrationAuthorizationService.validateSuperUserRole(this.platformSecurityContext.authenticatedUser());
            filterChain.doFilter(request, response);
        } catch (UnAuthenticatedUserException e) {
            filterChain.doFilter(request, response);
        } catch (RuntimeException e) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write(ApiGlobalErrorResponse.unAuthorized(e.getMessage()).toJson());
        }
    }

}
