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

package com.crediblex.fineract.portfolio.loc.repository;

import com.crediblex.fineract.portfolio.loc.domain.LineOfCredit;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository interface for Line of Credit entity.
 */
@Repository
public interface LineOfCreditRepository extends JpaRepository<LineOfCredit, Long> {

    /**
     * Find all line of credits by client ID.
     *
     * @param clientId
     *            the client ID
     * @return list of line of credits
     */
    List<LineOfCredit> findByClientId(Long clientId);

    /**
     * Find line of credit by client ID and name.
     *
     * @param clientId
     *            the client ID
     * @param name
     *            the line of credit name
     * @return optional line of credit
     */
    Optional<LineOfCredit> findByClientIdAndName(Long clientId, String name);

    /**
     * Check if a line of credit exists by client ID and name.
     *
     * @param clientId
     *            the client ID
     * @param name
     *            the line of credit name
     * @return true if exists, false otherwise
     */
    boolean existsByClientIdAndName(Long clientId, String name);

    /**
     * Find all line of credits by client ID and activation status.
     *
     * @param clientId
     *            the client ID
     * @param activationStatus
     *            the activation status
     * @return list of line of credits
     */
    List<LineOfCredit> findByClientIdAndActivationStatus(Long clientId, LineOfCredit.ActivationStatus activationStatus);
}
