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
package org.apache.fineract.infrastructure.integration.odoo.controller;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Simple REST API controller for Odoo integration
 */
@Path("/v1/odoo")
@Component
@ConditionalOnProperty(name = "odoo.enabled", havingValue = "true")
@Slf4j
public class OdooTestController {

    @GET
    @Path("/test")
    @Produces(MediaType.APPLICATION_JSON)
    public String testConnection() {
        log.info("OdooTestController test endpoint called");
        return "{\"status\":\"ok\",\"message\":\"Odoo integration endpoint working\",\"enabled\":true}";
    }
}
