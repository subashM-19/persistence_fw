# Business transactions

This framework now commits at a synchronous business-method boundary. SQL runs during the method; commit waits until the owning method returns successfully. All persistence reads and writes in that
thread use the same connection. No Spring dependency is needed.

## Configure once

Existing configuration works unchanged:

```java
PersistenceManager.initialize(databaseUrl, username, password);
TransactionManager.

setDefaultTimeoutSeconds(10); // once at startup; applies to new transactions
```

Alternatively, configure a JDBC pool through `PersistenceManager.initialize(dataSource)`. The provider must supply an idle connection with auto-commit enabled. Configure connection-acquisition and
network/socket timeouts on your driver/pool separately. Do not configure both initialization forms.

The built-in shared timeout is **30 seconds**. Set your preferred value once at startup, as above. All service methods called through `TransactionProxy.create(...)` inherit it without annotations.
Explicit boundaries using default options and standalone updates also inherit it. A positive method timeout is an optional override. Existing transactions keep the timeout chosen at their start;
changing the shared value affects only new transactions.

## Interface proxies

```java
import com.sentinal.persistencefw.transaction.*;

public interface RequestService
{
	void createRequest() throws Exception;
}

public interface MechanicService
{
	void assign() throws Exception;
}

public class MechanicServiceImpl implements MechanicService
{
	public void assign() throws Exception
	{
		// Existing PersistenceManager.get/update calls here join the caller's transaction.
	}
}

public class RequestServiceImpl implements RequestService
{
	private final MechanicService mechanics;

	public RequestServiceImpl(MechanicService mechanics)
	{
		this.mechanics = mechanics;
	}

	public void createRequest() throws Exception
	{
		// PersistenceManager.update(requestObject);
		mechanics.assign();
		// PersistenceManager.update(customerObject);
	}
}
```

Wire the application using proxies:

```java
MechanicService mechanics = TransactionProxy.create(MechanicService.class, new MechanicServiceImpl());
RequestService requests = TransactionProxy.create(RequestService.class, new RequestServiceImpl(mechanics));
requests.

createRequest();
```

The request method owns the transaction; mechanic assignment joins it. Neither individual updates nor the inner service commit it. An exception rolls everything back by default, including checked
exceptions. The original business exception is rethrown; cleanup failures are suppressed on it.

Every non-Object service method invoked through this proxy is transactional by default: REQUIRED propagation, DEFAULT isolation, writable mode, the common timeout, and rollback for Exceptions and
Errors. No class or method annotation is required. `equals`, `hashCode`, and `toString` do not start transactions.

Annotations are optional overrides. They can appear on implementation methods, interface methods, implementation classes, or the service interface. Resolution uses that precedence. Class annotations
are inherited by subclasses; method annotations use Java's normal rules and must be repeated on overrides that change metadata.

**Use the proxy at the entry point and inject proxies into other services.** Instantiating an implementation directly or calling `this.otherMethod()` bypasses proxy interception. Such code still
participates in an existing transaction because persistence operations use the thread context, but it cannot start a new intercepted boundary or apply another propagation mode. Overriding a method
alone does not enable interception. If your REST framework constructs handler instances itself, change its registration/wiring to call the service proxy, or wrap the handler's business work explicitly
as below. The supplied repository contains the persistence framework, so no Road Rescue server wiring was changed.

## Explicit boundary, including services without interfaces

```java
TransactionDefinition options = new TransactionDefinition(Propagation.REQUIRED, Isolation.READ_COMMITTED, 10, false);

// Work.run() may throw Throwable; this example belongs in a method declaring throws Throwable.
TransactionManager.

execute(options, () ->{
	// Existing PersistenceManager.update(firstObject);
	// Call other business methods on this same thread.
	// Existing PersistenceManager.update(secondObject);
	return null;
	});
```

`TransactionManager.execute(() -> {...})` uses default options. This works without reflection/proxies and gives an explicit boundary for self-invocation. Do not manually commit, roll back, close,
retain, or share the connection supplied to `withConnection`.

## Propagation

| Mode          | Existing transaction                                                      | No transaction                      |
|---------------|---------------------------------------------------------------------------|-------------------------------------|
| REQUIRED      | Join it; only its owner commits                                           | Start and own one                   |
| REQUIRES_NEW  | Suspend it; start an independent connection/transaction; resume afterward | Start and own one                   |
| SUPPORTS      | Join it                                                                   | Run without a business transaction  |
| NOT_SUPPORTED | Suspend it and resume afterward                                           | Run without a business transaction  |
| MANDATORY     | Join it                                                                   | Fail before executing business code |
| NEVER         | Fail before executing business code                                       | Run without a business transaction  |
| NESTED        | JDBC savepoint; inner rollback undoes its section                         | Start and own one                   |

A standalone `PersistenceManager.update()` still creates a local REQUIRED transaction for its operation list. Consequently, updates inside SUPPORTS/NOT_SUPPORTED/NEVER without an active transaction
commit individually; these modes remove the enclosing business transaction, not the update API's existing local atomicity.

REQUIRES_NEW needs an additional connection. Size the pool accordingly. Avoid waiting on rows locked by a suspended outer transaction. Its successful writes survive an outer rollback.

NESTED uses the same connection and owner deadline. Unsupported savepoints fail explicitly. A nested rollback removes its after-commit callbacks. A caught nested error can permit the outer method to
continue; a failure to create, roll back to, or release the savepoint marks the entire transaction rollback-only.

A failing REQUIRED participant marks the enclosing scope rollback-only even when its exception is caught. If the enclosing scope is NESTED, its savepoint is marked rather than the physical outer
transaction. `setRollbackOnly()` follows the same rule. Successful return from a rollback-only owner/savepoint throws `UnexpectedRollbackException` after rollback.

## Timeouts and isolation

`timeout` is in seconds: `-1` (`TransactionDefinition.DEFAULT_TIMEOUT`) inherits the shared setting; `-2` (`TransactionDefinition.NO_TIMEOUT`) explicitly disables a new transaction's deadline;
positive values override the shared setting. Zero and other negative values are rejected. `TransactionManager.setDefaultTimeoutSeconds(-1)` disables the shared deadline globally. The initial shared
setting is 30 seconds. The monotonic deadline starts when a connection has been obtained and transaction setup begins. It includes Java processing and all SQL work, not connection-pool acquisition.
The owner defines the deadline; joined and nested definitions do not reset or shorten it. REQUIRES_NEW gets its own deadline; time spent in a suspended outer transaction still counts toward that outer
deadline.

The deadline is checked at scope entry, before and after persistence operations, before statement execution, and before commit. Timed transactions set `Statement.setQueryTimeout` to the remaining
duration rounded up to a positive whole second. A timeout marks the physical transaction rollback-only even if caught, and no commit is started after an expired deadline is detected. `checkTimeout()`
allows cooperative checks inside long Java loops.

**This is a cooperative transaction deadline, not a hard wall-clock interrupt.** It does not forcibly stop Java processing or roll back from a scheduler thread. JDBC query cancellation depends on
driver support and whole-second timing. Connection acquisition, result fetching, commit, rollback, and network stalls may need independent driver/pool/database limits. A commit already started cannot
be made atomic with a Java deadline check; a timeout during commit can leave the result uncertain.

Isolation options: DEFAULT (provider default), READ_UNCOMMITTED, READ_COMMITTED, REPEATABLE_READ, SERIALIZABLE. The driver/database must support the requested level. A participant requesting a
different explicit isolation level fails and marks the active transaction rollback-only. Joining writable metadata to a read-only transaction is rejected. A read-only participant joining a writable
owner does not change the owner's read-only setting.

`readOnly = true` applies a JDBC hint and also rejects framework writes and locking reads. Raw SQL supplied through `withConnection` is a trusted escape hatch; JDBC's hint alone may not enforce it. Do
not use raw SQL to bypass read-only policy.

## Rollback rules

```java
@Transactional(rollbackFor = { Exception.class }, noRollbackFor = { BusinessValidationException.class }) public void performBusinessWork() throws Exception
{ /* ... */ }
```

The nearest matching exception class wins; `noRollbackFor` wins an equal-specificity tie. Errors and framework `TransactionException`s always cause rollback. An empty `rollbackFor` disables default
exception rollback, so set it intentionally. JDBC/persistence operation failures mark the current scope rollback-only independently of business exception rules; catching those errors or using
`noRollbackFor` cannot turn failed SQL into a commit.

If no rollback rule matches, the transaction commits and the original exception is still thrown. Commit failure raises `TransactionCommitException` when there is no earlier business exception. When an
earlier exception exists, the commit failure is suppressed on it. A failed commit has an unknown database outcome; the framework aborts/closes that connection without enabling auto-commit. Do not
retry non-idempotent work blindly. Pool/driver abort support must correctly discard the underlying connection.

## Resources, objects and callbacks

Statements and result sets remain in try-with-resources. Only the transaction owner restores isolation/read-only/auto-commit and closes its connection. The context and scope ThreadLocals are
restored/removed in finally, including suspend/resume and failures, preventing request-thread reuse leaks.

Generated IDs, defaults, and LastModifiedTime values are still populated during SQL execution. **Rollback does not rewind in-memory Row/PersistenceObject values.** Discard/reload affected objects
after rollback, including a savepoint rollback. Dirty flags clear only after successful physical commit and only for columns whose values still match the executed operation; later unsaved edits remain
dirty. Operation lists are not automatically cleared. When reusing a PersistenceObject, call `clearOperations()` after a successful `update()` to remove executed actions from both tracking
collections; this does not commit SQL or remove loaded rows.

`afterCommit(Runnable)` registers synchronous callbacks for a successful physical commit. A callback failure reports an exception but cannot undo committed DB work. Callbacks execute with a completed
context still bound; use an explicit REQUIRES_NEW boundary if a callback must do additional DB work. These callbacks are not a durable notification mechanism; use a transactional outbox when delivery
must survive process failure.

Transactions are strictly thread-bound. An executor, background job, or CompletableFuture worker starts its own boundary inside that worker. Sharing a JDBC connection/transaction across threads is
unsupported. Proxy methods returning Future/CompletionStage are rejected before invocation to prevent committing before async work finishes; void methods must also finish their transactional work
synchronously. There is no XA/distributed transaction across multiple databases or external services.

## Build and verification

Requires JDK 17. The wrapper JAR is included, and its distribution is pinned to Gradle 9.1.0 with a SHA-256 checksum.

```bash
./gradlew test
./gradlew build
```

The original persistence tests plus transaction and JDBC fault-injection tests verify business-boundary visibility, all propagation modes, savepoint rollback, caught failures, query deadlines,
read-only protection, isolation validation, exception rules, proxy metadata, async rejection, cleanup, and uncertain commit handling. See TEST_RESULTS.md for the execution performed on this delivery.
MySQL-specific timeout and savepoint behavior still needs verification against your deployed driver/database.
