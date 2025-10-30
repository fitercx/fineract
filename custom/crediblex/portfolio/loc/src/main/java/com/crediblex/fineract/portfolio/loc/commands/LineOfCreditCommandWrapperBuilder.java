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

package com.crediblex.fineract.portfolio.loc.commands;

import com.crediblex.fineract.portfolio.loc.api.LineOfCreditApiConstants;
import org.apache.fineract.commands.domain.CommandWrapper;

public class LineOfCreditCommandWrapperBuilder {

    private String actionName;
    private String entityName;
    private Long entityId;
    private String href;
    private String json = "{}";
    private Long clientId;

    public LineOfCreditCommandWrapperBuilder createLineOfCredit(Long clientId) {
        this.actionName = "CREATE";
        this.clientId = clientId;
        this.entityName = LineOfCreditApiConstants.LINE_OF_CREDIT;
        this.entityId = null;
        this.href = "/v1/lineofcredit";
        return this;
    }

    public LineOfCreditCommandWrapperBuilder updateLineOfCredit(final Long lineOfCreditId) {
        this.actionName = "UPDATE";
        this.entityName = LineOfCreditApiConstants.LINE_OF_CREDIT;
        this.entityId = lineOfCreditId;
        this.href = "/v1/lineofcredit/" + lineOfCreditId;
        return this;
    }

    public LineOfCreditCommandWrapperBuilder activateLineOfCredit(final Long lineOfCreditId, Long clientId) {
        this.clientId = clientId;
        this.actionName = "ACTIVATE";
        this.entityName = LineOfCreditApiConstants.LINE_OF_CREDIT;
        this.entityId = lineOfCreditId;
        this.href = "/v1/lineofcredit/" + lineOfCreditId;
        return this;
    }

    public LineOfCreditCommandWrapperBuilder deactivateLineOfCredit(final Long lineOfCreditId, final Long clientId) {
        this.clientId = clientId;
        this.actionName = "DEACTIVATE";
        this.entityName = LineOfCreditApiConstants.LINE_OF_CREDIT;
        this.entityId = lineOfCreditId;
        this.href = "/v1/lineofcredit/" + lineOfCreditId;
        return this;
    }

    public LineOfCreditCommandWrapperBuilder deleteLineOfCredit(final Long lineOfCreditId) {
        this.actionName = "DELETE";
        this.entityName = LineOfCreditApiConstants.LINE_OF_CREDIT;
        this.entityId = lineOfCreditId;
        this.href = "/v1/lineofcredit/" + lineOfCreditId;
        return this;
    }

    public LineOfCreditCommandWrapperBuilder approveLineOfCredit(final Long lineOfCreditId, final Long clientId) {
        this.clientId = clientId;
        this.actionName = "APPROVE";
        this.entityName = LineOfCreditApiConstants.LINE_OF_CREDIT;
        this.entityId = lineOfCreditId;
        this.href = "/v1/lineofcredit/" + lineOfCreditId;
        return this;
    }

    public LineOfCreditCommandWrapperBuilder closeLineOfCredit(final Long lineOfCreditId, Long clientId) {
        this.clientId = clientId;
        this.actionName = "CLOSE";
        this.entityName = LineOfCreditApiConstants.LINE_OF_CREDIT;
        this.entityId = lineOfCreditId;
        this.href = "/v1/lineofcredit/" + lineOfCreditId;
        return this;
    }

    public LineOfCreditCommandWrapperBuilder increaseCreditLimit(Long lineOfCreditId, Long clientId) {

        this.clientId = clientId;
        this.actionName = "INCREASE";
        this.entityName = LineOfCreditApiConstants.LINE_OF_CREDIT;
        this.entityId = lineOfCreditId;
        this.href = "/v1/lineofcredit/" + lineOfCreditId + "/increasecreditlimit";
        return this;
    }

    public LineOfCreditCommandWrapperBuilder decreaseCreditLimit(Long lineOfCreditId, Long clientId) {

        this.clientId = clientId;
        this.actionName = "DECREASE";
        this.entityName = LineOfCreditApiConstants.LINE_OF_CREDIT;
        this.entityId = lineOfCreditId;
        this.href = "/v1/lineofcredit/" + lineOfCreditId + "/decreasecreditlimit";
        return this;
    }

    public LineOfCreditCommandWrapperBuilder undoCloseLineOfCredit(Long lineOfCreditId, Long clientId) {

        this.clientId = clientId;
        this.actionName = "UNDO_CLOSE";
        this.entityName = LineOfCreditApiConstants.LINE_OF_CREDIT;
        this.entityId = lineOfCreditId;
        this.href = "/v1/lineofcredit/" + lineOfCreditId + "/undoclose";
        return this;
    }

    public LineOfCreditCommandWrapperBuilder reactivateLineOfCredit(Long lineOfCreditId, Long clientId) {

        this.clientId = clientId;
        this.actionName = "REACTIVATE";
        this.entityName = LineOfCreditApiConstants.LINE_OF_CREDIT;
        this.entityId = lineOfCreditId;
        this.href = "/v1/lineofcredit/" + lineOfCreditId + "/reactivate";
        return this;
    }

    public LineOfCreditCommandWrapperBuilder withJson(final String json) {
        this.json = json;
        return this;
    }

    public CommandWrapper build() {
        return new CommandWrapper(null, null, clientId, null, null, this.actionName, this.entityName, this.entityId, null, this.href,
                this.json, null, null, null, null, null, null, null, null);
    }
}
