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
package com.crediblex.fineract.useradministration.service;

import org.apache.fineract.commands.domain.CommandWrapper;
import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
import org.apache.fineract.useradministration.domain.AppUser;
import org.apache.fineract.useradministration.domain.Role;
import org.springframework.stereotype.Service;

@Service
public class SuperUserAdministrationAuthorizationService {

    public static final Long SUPER_USER_ROLE_ID = 1L;

    private static final String SUPER_USER_ONLY_MESSAGE = "Only users assigned the Super user role may manage users, roles and permissions";

    public boolean hasSuperUserRole(final AppUser user) {
        for (final Role role : user.getRoles()) {
            if (SUPER_USER_ROLE_ID.equals(role.getId())) {
                return true;
            }
        }
        return false;
    }

    public void validateSuperUserRole(final AppUser user) {
        if (!hasSuperUserRole(user)) {
            throw new NoAuthorizationException(SUPER_USER_ONLY_MESSAGE);
        }
    }

    public boolean isSuperUserRole(final Long roleId) {
        return SUPER_USER_ROLE_ID.equals(roleId);
    }

    public void validateSuperUserRoleIsNotModified(final Long roleId) {
        if (isSuperUserRole(roleId)) {
            throw new NoAuthorizationException("The Super user role cannot be modified or deleted");
        }
    }

    public boolean userHasSuperUserRole(final AppUser user) {
        return hasSuperUserRole(user);
    }

    public void validateSuperUserAccountIsNotDeleted(final AppUser userToDelete) {
        if (userHasSuperUserRole(userToDelete)) {
            throw new NoAuthorizationException("Super user accounts cannot be deleted");
        }
    }

    public boolean requiresSuperUserForCommand(final CommandWrapper wrapper, final Long authenticatedUserId) {
        if (wrapper.isUpdateOfOwnUserDetails(authenticatedUserId)) {
            return false;
        }
        return requiresSuperUserForEntity(wrapper.entityName());
    }

    public boolean requiresSuperUserForEntity(final String entityName) {
        if (entityName == null) {
            return false;
        }
        return "USER".equalsIgnoreCase(entityName) || "ROLE".equalsIgnoreCase(entityName) || "PERMISSION".equalsIgnoreCase(entityName);
    }

    public boolean isUserAdministrationApiPath(final String requestUri) {
        if (requestUri == null) {
            return false;
        }
        return requestUri.matches(".*/api/v\\d+/users/downloadtemplate.*") || requestUri.matches(".*/api/v\\d+/users/uploadtemplate.*");
    }

}
