# Apache Fineract PR Review Guidelines

## When performing a code review for Apache Fineract, follow these guidelines:

### Architecture & Design Patterns

- **Repository Pattern**: Verify proper use of JPA repositories and repository wrappers. Ensure repositories are accessed through wrapper classes for consistent error handling.
- **Service Layer**: Check that business logic is properly separated into service interfaces and implementations, following the established pattern (`ServiceImpl` classes).
- **Domain Driven Design**: Ensure domain entities follow DDD principles with proper aggregate boundaries and domain methods.
- **Custom Modules**: Pay special attention to the `custom/` folder structure. Custom modules should extend core Fineract services properly using `@Primary` annotation and inheritance patterns.

### Data Validation & DTOs

- **Validation Framework**: Ensure proper use of `DataValidatorBuilder` for all input validation with meaningful error messages.
- **API Parameter Errors**: Verify that validation errors use `ApiParameterError.parameterError()` with appropriate error codes and user-friendly messages.
- **Command Pattern**: Check that API requests properly use `JsonCommand` objects and validation is performed before business logic execution.
- **Null Safety**: Verify proper null checks and use of Optional where appropriate.

### Error Handling & Exception Management

- **Exception Hierarchy**: Ensure custom exceptions extend appropriate Fineract base exceptions (`PlatformApiDataValidationException`, `PlatformDataIntegrityException`).
- **Error Context**: Check that exceptions include sufficient context (entity IDs, parameter names, values) for debugging.
- **Transaction Boundaries**: Verify that service methods have proper `@Transactional` annotations and rollback behavior.
- **Resource Not Found**: Ensure proper use of repository wrapper classes that throw standardized not-found exceptions.

### Logging Guidelines

A good log = context + consistency + clear message + correct log level.

- **SLF4J Logger**: Always use SLF4J Logger (`log.info`, `log.warn`, `log.error`, `log.debug`) - never `System.out.println` or `printStackTrace()`.
- **Log Levels**:
  - `log.error()`: System errors, unrecoverable failures, external system connectivity issues
  - `log.warn()`: Unexpected conditions that are handled, deprecated usage
  - `log.info()`: Important business events, startup/shutdown information (use sparingly in production paths)
  - `log.debug()`: Detailed diagnostic information for troubleshooting
- **Structured Logging**: Include relevant business identifiers (clientId, loanId, accountId) in log messages for searchability.
- **Example patterns**:
  ```java
  log.info("Loan {} approved for client {}", loanId, clientId);
  log.warn("Deprecated API endpoint used: {} by user {}", endpoint, userId);
  log.error("Failed to process loan transaction for loan {}: {}", loanId, e.getMessage(), e);
  ```

### Database & JPA Patterns

- **Entity Relationships**: Verify proper JPA relationship mappings (`@OneToMany`, `@ManyToOne`) with appropriate fetch strategies.
- **Audit Fields**: Ensure all domain entities extend `AbstractPersistableCustom` or `AbstractAuditableWithUTCDateTimeCustom` for audit support.
- **Query Optimization**: Check for N+1 query problems and recommend batch fetching or JOIN FETCH where appropriate.
- **Database Transactions**: Verify that long-running operations are properly chunked and don't hold locks excessively.

### API Design & REST Controllers

- **RESTful Design**: Ensure API endpoints follow REST conventions and use appropriate HTTP methods.
- **Response DTOs**: Verify that API responses use proper data transfer objects, not domain entities directly.
- **Swagger Documentation**: Check that controllers have proper `@Operation`, `@Parameter`, and `@ApiResponse` annotations.
- **Pagination**: Ensure large data sets use proper pagination with `SearchParameters`.

### Security & Authorization

- **Security Context**: Verify proper use of `PlatformSecurityContext` for authentication and tenant context.
- **Permission Checks**: Ensure service methods check appropriate permissions before execution.
- **SQL Injection**: Watch for any dynamic SQL construction and ensure use of parameterized queries.
- **Tenant Isolation**: Verify that multi-tenant data access is properly isolated.

### Code Quality & Style

- **Checkstyle Compliance**: Ensure code follows the project's checkstyle rules (checked by CI).
- **Spotless Formatting**: Verify code is properly formatted according to project standards.
- **SpotBugs Issues**: Check for common bug patterns that SpotBugs would catch.
- **ErrorProne**: Address any ErrorProne warnings that indicate potential issues.
- **Modernizer**: Ensure use of modern Java APIs and avoid deprecated patterns.

### Testing Patterns

- **Unit Tests**: Verify comprehensive unit test coverage for business logic, especially in service classes.
- **Integration Tests**: Check that complex workflows have appropriate integration tests.
- **Test Data Builders**: Encourage use of test data builders for consistent test data creation.
- **Mock Usage**: Ensure proper mocking of dependencies in unit tests using Mockito.

### Performance Considerations

- **Database Queries**: Review query patterns for efficiency and proper indexing.
- **Lazy Loading**: Check for appropriate use of lazy vs eager loading in JPA entities.
- **Caching**: Verify that appropriate caching strategies are used for frequently accessed data.
- **Batch Processing**: Ensure bulk operations are properly batched and optimized.

### Custom Module Specific Checks

- **Inheritance Patterns**: Verify custom services properly extend core Fineract services.
- **Configuration**: Check that custom modules have proper Spring configuration and auto-configuration.
- **Backward Compatibility**: Ensure custom changes don't break existing core functionality.
- **Module Dependencies**: Verify that custom modules don't create inappropriate dependencies between core modules.

### Breaking Changes & API Compatibility

- **Backward Compatibility**: Identify any changes that could break existing API clients.
- **Database Schema**: Check that database changes include proper migration scripts.
- **Configuration Changes**: Verify that new configuration properties have appropriate defaults.
- **Deprecation**: Ensure deprecated features have proper deprecation annotations and migration paths.

### CI/CD & Build Checks

- **Build Success**: Ensure the PR passes all CI checks including:
  - Gradle build without warnings
  - All tests passing
  - Checkstyle compliance
  - SpotBugs analysis
  - Spotless formatting
- **Test Coverage**: Verify that new code has appropriate test coverage.
- **Documentation**: Check that significant changes include updated documentation.

### Security & Compliance

- **Sensitive Data**: Ensure no sensitive information (passwords, tokens) is logged or exposed.
- **Input Sanitization**: Verify all user inputs are properly validated and sanitized.
- **Audit Trail**: Check that significant business operations create appropriate audit records.
- **External Dependencies**: Review any new dependencies for security vulnerabilities.

## Common Anti-Patterns to Flag

1. **Direct Repository Access**: Services accessing repositories directly instead of using wrapper classes
2. **Empty Catch Blocks**: Exception handling that silently swallows errors
3. **String Concatenation in Logs**: Using `+` instead of parameterized logging
4. **Missing Validation**: Business logic executing without proper input validation
5. **Hardcoded Values**: Business rules or configuration hardcoded instead of configurable
6. **Overly Complex Methods**: Methods that are too long or have too many responsibilities
7. **Missing Transaction Boundaries**: Database operations without proper transaction management
8. **Inappropriate Exception Types**: Using generic exceptions instead of domain-specific ones

## Review Process Tips

1. **Start with Architecture**: Review the overall design and approach before diving into implementation details
2. **Focus on Custom Modules**: Pay extra attention to code in the `custom/` folder as it extends core functionality
3. **Check CI Status**: Ensure all automated checks pass before approving
4. **Verify Tests**: Don't approve PRs without adequate test coverage
5. **Business Logic Priority**: Focus more on business logic correctness than style issues (which are caught by automated tools)
6. **Security Mindset**: Always consider security implications of changes
7. **Performance Impact**: Consider the performance implications of changes, especially in high-traffic areas
