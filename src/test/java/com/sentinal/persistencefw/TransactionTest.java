package com.sentinal.persistencefw;

import com.sentinal.persistencefw.transaction.*;
import com.sentinal.persistencefw.metadata.*;
import org.junit.jupiter.api.*;

import java.sql.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class TransactionTest
{
	static final String URL = "jdbc:h2:mem:persistence_test;DB_CLOSE_DELAY=-1";

	@BeforeEach void setup() throws Exception
	{
		PersistenceManager.initialize(URL, "sa", "");
		Schema.clear();
		try (Connection c = DriverManager.getConnection(URL, "sa", ""); Statement s = c.createStatement())
		{
			s.execute("DROP ALL OBJECTS");
			s.execute("CREATE TABLE TxItem (id INT PRIMARY KEY, label VARCHAR(100) NOT NULL)");
		}
		Schema.registerTable("TxItem", new ColumnMetaData("id", Types.INTEGER, "INTEGER", 10, false, false, true, true, null, false, null, null), new ColumnMetaData("label", Types.VARCHAR, "VARCHAR", 100, false, false, false, false, null, false, null, null));
	}

	@AfterEach void clean()
	{
		TransactionManager.setDefaultTimeoutSeconds(30);
		assertFalse(TransactionManager.isActive(), "ThreadLocal leak");
		Schema.clear();
	}

	static TransactionDefinition def(Propagation p)
	{
		return new TransactionDefinition(p, Isolation.DEFAULT, -1, false);
	}

	static Row insert(int id)
	{
		Row r = new Row("TxItem");
		r.set("id", id);
		r.set("label", "item" + id);
		PersistenceObject po = new PersistenceObject();
		po.addRowForInsert(r);
		PersistenceManager.update(po);
		return r;
	}

	static int committedCount() throws Exception
	{
		try (Connection c = DriverManager.getConnection(URL, "sa", ""); Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT COUNT(*) FROM TxItem"))
		{
			r.next();
			return r.getInt(1);
		}
	}

	static int participatingCount()
	{
		return PersistenceManager.get(new PersistenceQuery("TxItem")).getRows("TxItem").size();
	}

	@Test void multipleUpdatesCommitAtBusinessBoundary() throws Throwable
	{
		TransactionManager.execute(() -> {
			Row r = insert(1);
			insert(2);
			assertEquals(0, committedCount());
			assertEquals(2, participatingCount());
			assertTrue(r.isModified("label"));
			return null;
		});
		assertEquals(2, committedCount());
	}

	@Test void standaloneUpdateStillCommitsAndClearsFlags() throws Exception
	{
		Row r = insert(1);
		assertEquals(1, committedCount());
		assertFalse(r.isModified("label"));
	}

	@Test void checkedExceptionRollsBackEverything() throws Exception
	{
		IOException expected = new IOException("business");
		assertSame(expected, assertThrows(IOException.class, () -> TransactionManager.execute(() -> {
			insert(1);
			insert(2);
			throw expected;
		})));
		assertEquals(0, committedCount());
	}

	@Test void requiredSharesConnectionAndDoesNotCommitEarly() throws Throwable
	{
		TransactionManager.execute(() -> {
			Connection outer = TransactionManager.withConnection(c -> c);
			TransactionManager.execute(() -> {
				assertSame(outer, TransactionManager.withConnection(c -> c));
				insert(1);
				return null;
			});
			assertEquals(0, committedCount());
			assertFalse(outer.isClosed());
			return null;
		});
		assertEquals(1, committedCount());
	}

	@Test void caughtRequiredExceptionStillRollsBackOwner() throws Exception
	{
		assertThrows(UnexpectedRollbackException.class, () -> TransactionManager.execute(() -> {
			insert(1);
			try
			{
				TransactionManager.execute(() -> {
					insert(2);
					throw new IOException();
				});
			}
			catch (IOException ignored)
			{
			}
			return null;
		}));
		assertEquals(0, committedCount());
	}

	@Test void requiresNewCommitsIndependentlyAndResumesOuter() throws Exception
	{
		assertThrows(IOException.class, () -> TransactionManager.execute(() -> {
			Connection outer = TransactionManager.withConnection(c -> c);
			insert(1);
			TransactionManager.execute(def(Propagation.REQUIRES_NEW), () -> {
				assertNotSame(outer, TransactionManager.withConnection(c -> c));
				insert(2);
				return null;
			});
			assertSame(outer, TransactionManager.withConnection(c -> c));
			assertEquals(1, committedCount());
			throw new IOException();
		}));
		assertEquals(1, committedCount());
	}

	@Test void failedRequiresNewDoesNotPoisonOuter() throws Throwable
	{
		TransactionManager.execute(() -> {
			insert(1);
			try
			{
				TransactionManager.execute(def(Propagation.REQUIRES_NEW), () -> {
					insert(2);
					throw new IOException();
				});
			}
			catch (IOException ignored)
			{
			}
			assertFalse(TransactionManager.isRollbackOnly());
			return null;
		});
		assertEquals(1, committedCount());
	}

	@Test void nestedSavepointRollsBackOnlyInnerChangesAndCallbacks() throws Throwable
	{
		List<Integer> callbacks = new ArrayList<>();
		TransactionManager.execute(() -> {
			insert(1);
			TransactionManager.afterCommit(() -> callbacks.add(1));
			try
			{
				TransactionManager.execute(def(Propagation.NESTED), () -> {
					insert(2);
					TransactionManager.afterCommit(() -> callbacks.add(2));
					throw new IOException();
				});
			}
			catch (IOException ignored)
			{
			}
			assertEquals(1, participatingCount());
			insert(3);
			return null;
		});
		assertEquals(2, committedCount());
		assertEquals(List.of(1), callbacks);
	}

	@Test void nestedCaughtSqlFailureMarksOnlySavepointScope() throws Throwable
	{
		TransactionManager.execute(() -> {
			insert(1);
			assertThrows(UnexpectedRollbackException.class, () -> TransactionManager.execute(def(Propagation.NESTED), () -> {
				insert(2);
				try
				{
					insert(1);
				}
				catch (RuntimeException ignored)
				{
				}
				return null;
			}));
			assertFalse(TransactionManager.isRollbackOnly());
			return null;
		});
		assertEquals(1, committedCount());
	}

	@Test void nestedSuccessStillRollsBackWithOuter() throws Exception
	{
		assertThrows(IOException.class, () -> TransactionManager.execute(() -> {
			TransactionManager.execute(def(Propagation.NESTED), () -> {
				insert(1);
				return null;
			});
			throw new IOException();
		}));
		assertEquals(0, committedCount());
	}

	@Test void nestedWithoutOuterStartsTransaction() throws Throwable
	{
		TransactionManager.execute(def(Propagation.NESTED), () -> {
			insert(1);
			assertEquals(0, committedCount());
			return null;
		});
		assertEquals(1, committedCount());
	}

	@Test void mandatoryNeverAndSupports() throws Throwable
	{
		assertThrows(TransactionException.class, () -> TransactionManager.execute(def(Propagation.MANDATORY), () -> null));
		TransactionManager.execute(def(Propagation.NEVER), () -> {
			assertFalse(TransactionManager.isActive());
			return null;
		});
		TransactionManager.execute(def(Propagation.SUPPORTS), () -> {
			assertFalse(TransactionManager.isActive());
			return null;
		});
		TransactionManager.execute(() -> {
			TransactionManager.execute(def(Propagation.MANDATORY), () -> {
				assertTrue(TransactionManager.isActive());
				return null;
			});
			TransactionManager.execute(def(Propagation.SUPPORTS), () -> {
				assertTrue(TransactionManager.isActive());
				return null;
			});
			assertThrows(TransactionException.class, () -> TransactionManager.execute(def(Propagation.NEVER), () -> null));
			return null;
		});
	}

	@Test void notSupportedSuspendsAndResumesEvenOnFailure() throws Throwable
	{
		TransactionManager.execute(() -> {
			Connection outer = TransactionManager.withConnection(c -> c);
			assertThrows(IOException.class, () -> TransactionManager.execute(def(Propagation.NOT_SUPPORTED), () -> {
				assertFalse(TransactionManager.isActive());
				insert(1);
				throw new IOException();
			}));
			assertSame(outer, TransactionManager.withConnection(c -> c));
			assertEquals(1, committedCount());
			return null;
		});
	}

	@Test void explicitRollbackOnly() throws Exception
	{
		assertThrows(UnexpectedRollbackException.class, () -> TransactionManager.execute(() -> {
			insert(1);
			TransactionManager.setRollbackOnly();
			return null;
		}));
		assertEquals(0, committedCount());
		assertThrows(TransactionException.class, TransactionManager::setRollbackOnly);
	}

	@Test void readOnlyRejectsWritesEvenWhenDriverTreatsItAsHint() throws Exception
	{
		assertThrows(TransactionException.class, () -> TransactionManager.execute(new TransactionDefinition(Propagation.REQUIRED, Isolation.DEFAULT, -1, true), () -> {
			insert(1);
			return null;
		}));
		assertEquals(0, committedCount());
	}

	@Test void isolationAndJoinValidation() throws Throwable
	{
		assertThrows(UnexpectedRollbackException.class, () -> TransactionManager.execute(new TransactionDefinition(Propagation.REQUIRED, Isolation.SERIALIZABLE, -1, false), () -> {
			assertEquals(Connection.TRANSACTION_SERIALIZABLE, TransactionManager.withConnection(Connection::getTransactionIsolation));
			assertThrows(TransactionException.class, () -> TransactionManager.execute(new TransactionDefinition(Propagation.REQUIRED, Isolation.READ_COMMITTED, -1, false), () -> null));
			assertTrue(TransactionManager.isRollbackOnly());
			return null;
		}));
	}

	@Test void timeoutIncludesJavaProcessingAndPreventsCommit() throws Exception
	{
		assertThrows(TransactionTimeoutException.class, () -> TransactionManager.execute(new TransactionDefinition(Propagation.REQUIRED, Isolation.DEFAULT, 1, false), () -> {
			insert(1);
			Thread.sleep(1100);
			return null;
		}));
		assertEquals(0, committedCount());
	}

	@Test void remainingTimeoutAppliedToStatements() throws Throwable
	{
		TransactionManager.execute(new TransactionDefinition(Propagation.REQUIRED, Isolation.DEFAULT, 2, false), () -> {
			return TransactionManager.withConnection(c -> {
				try (Statement s = c.createStatement())
				{
					TransactionManager.prepareStatement(s);
					assertTrue(s.getQueryTimeout() >= 1 && s.getQueryTimeout() <= 2);
					return null;
				}
			});
		});
	}

	@Test void timeoutIsNotInheritedAcrossAsyncThreads() throws Throwable
	{
		TransactionManager.execute(() -> {
			ExecutorService executor = Executors.newSingleThreadExecutor();
			try
			{
				assertFalse(executor.submit(TransactionManager::isActive).get());
			}
			finally
			{
				executor.shutdownNow();
			}
			return null;
		});
	}

	@Test void noRollbackRuleCommitsButPreservesException() throws Exception
	{
		TransactionDefinition d = new TransactionDefinition(Propagation.REQUIRED, Isolation.DEFAULT, -1, false, List.of(Exception.class), List.of(IOException.class));
		assertThrows(IOException.class, () -> TransactionManager.execute(d, () -> {
			insert(1);
			throw new IOException();
		}));
		assertEquals(1, committedCount());
	}

	@Test void errorAlwaysRollsBack() throws Exception
	{
		assertThrows(AssertionError.class, () -> TransactionManager.execute(() -> {
			insert(1);
			throw new AssertionError();
		}));
		assertEquals(0, committedCount());
	}

	@Test void afterCommitFailureDoesNotPretendToRollback() throws Exception
	{
		assertThrows(TransactionException.class, () -> TransactionManager.execute(() -> {
			insert(1);
			TransactionManager.afterCommit(() -> {
				throw new IllegalStateException();
			});
			return null;
		}));
		assertEquals(1, committedCount());
	}

	@Test void laterUnsavedEditsKeepDirtyFlags() throws Throwable
	{
		Row[] saved = new Row[1];
		TransactionManager.execute(() -> {
			saved[0] = insert(1);
			saved[0].set("label", "unsaved");
			return null;
		});
		assertTrue(saved[0].isModified("label"));
		assertFalse(saved[0].isModified("id"));
	}

	public interface Service
	{
		void run() throws IOException;
	}

	public static class Child implements Service
	{
		@Transactional public void run() throws IOException
		{
			insert(2);
			throw new IOException("child");
		}
	}

	public static class Parent implements Service
	{
		private final Service child;

		Parent(Service child)
		{
			this.child = child;
		}

		@Transactional public void run() throws IOException
		{
			insert(1);
			child.run();
		}
	}

	@Test void proxyCrossClassRollbackAndUnwrappedException() throws Exception
	{
		Service child = TransactionProxy.create(Service.class, new Child());
		Service parent = TransactionProxy.create(Service.class, new Parent(child));
		assertEquals("child", assertThrows(IOException.class, parent::run).getMessage());
		assertEquals(0, committedCount());
		assertEquals(parent, parent);
		assertNotEquals(parent, child);
		assertTrue(parent.toString().contains("TransactionProxy"));
	}

	@Transactional public interface AnnotatedService
	{
		void run();
	}

	public static class InterfaceService implements AnnotatedService
	{
		public void run()
		{
			assertTrue(TransactionManager.isActive());
			insert(1);
		}
	}

	@Test void interfaceTypeAnnotation() throws Exception
	{
		TransactionProxy.create(AnnotatedService.class, new InterfaceService()).run();
		assertEquals(1, committedCount());
	}

	@Test void invalidTimeoutRejected()
	{
		assertThrows(IllegalArgumentException.class, () -> new TransactionDefinition(Propagation.REQUIRED, Isolation.DEFAULT, 0, false));
	}

	public interface AsyncService
	{
		CompletionStage<Void> run();
	}

	public static class AsyncImplementation implements AsyncService
	{
		@Transactional public CompletionStage<Void> run()
		{
			throw new AssertionError("Async method must be rejected before invocation");
		}
	}

	@Test void proxyRejectsAsyncTransactionBoundary()
	{
		AsyncService proxy = TransactionProxy.create(AsyncService.class, new AsyncImplementation());
		assertThrows(TransactionException.class, proxy::run);
	}

	public interface MethodAnnotatedService
	{
		@Transactional void run();
	}

	public static class MethodImplementation implements MethodAnnotatedService
	{
		public void run()
		{
			assertTrue(TransactionManager.isActive());
			insert(1);
		}
	}

	@Test void interfaceMethodAnnotation() throws Exception
	{
		TransactionProxy.create(MethodAnnotatedService.class, new MethodImplementation()).run();
		assertEquals(1, committedCount());
	}

	@Transactional public static class TypeImplementation implements Service
	{
		public void run()
		{
			assertTrue(TransactionManager.isActive());
			insert(1);
		}
	}

	@Test void implementationTypeAnnotation() throws Exception
	{
		TransactionProxy.create(Service.class, new TypeImplementation()).run();
		assertEquals(1, committedCount());
	}

	public static class NoRollbackService implements Service
	{
		@Transactional(noRollbackFor = IOException.class) public void run() throws IOException
		{
			insert(1);
			throw new IOException("expected");
		}
	}

	@Test void proxyConfiguredRollbackRules() throws Exception
	{
		assertThrows(IOException.class, TransactionProxy.create(Service.class, new NoRollbackService())::run);
		assertEquals(1, committedCount());
	}

	@Test void nestedExplicitRollbackOnlyDoesNotPoisonOuter() throws Throwable
	{
		TransactionManager.execute(() -> {
			insert(1);
			assertThrows(UnexpectedRollbackException.class, () -> TransactionManager.execute(def(Propagation.NESTED), () -> {
				insert(2);
				TransactionManager.setRollbackOnly();
				return null;
			}));
			assertFalse(TransactionManager.isRollbackOnly());
			return null;
		});
		assertEquals(1, committedCount());
	}

	@Test void aggregateReadSeesUncommittedUpdates() throws Throwable
	{
		TransactionManager.execute(() -> {
			insert(1);
			PersistenceQuery q = new PersistenceQuery("TxItem");
			q.count("TxItem", "id", "total");
			assertEquals(1L, ((Number) PersistenceManager.execute(q).iterator().next().get("total")).longValue());
			assertEquals(0, committedCount());
			return null;
		});
	}

	@Test void joinedTimeoutUsesOwnersDeadline() throws Throwable
	{
		TransactionManager.execute(new TransactionDefinition(Propagation.REQUIRED, Isolation.DEFAULT, 5, false), () -> {
			TransactionManager.execute(new TransactionDefinition(Propagation.REQUIRED, Isolation.DEFAULT, 1, false), () -> {
				TransactionManager.withConnection(c -> {
					try (Statement statement = c.createStatement())
					{
						TransactionManager.prepareStatement(statement);
						assertTrue(statement.getQueryTimeout() > 1);
						return null;
					}
				});
				return null;
			});
			return null;
		});
	}

	@Test @Timeout(10) void runningSqlIsCancelledByDriverTimeout() throws Exception
	{
		assertThrows(SQLException.class, () -> TransactionManager.execute(new TransactionDefinition(Propagation.REQUIRED, Isolation.DEFAULT, 1, false), () -> {
			insert(1);
			return TransactionManager.withConnection(c -> {
				try (Statement statement = c.createStatement())
				{
					TransactionManager.prepareStatement(statement);
					try (ResultSet result = statement.executeQuery("SELECT SUM(a.X * b.X) FROM SYSTEM_RANGE(1, 100000) a, SYSTEM_RANGE(1, 100000) b"))
					{
						result.next();
						return result.getLong(1);
					}
				}
			});
		}));
		assertEquals(0, committedCount());
	}

	@Test void defaultTransactionsUseCommonTimeout() throws Throwable
	{
		TransactionManager.setDefaultTimeoutSeconds(3);
		assertEquals(3, TransactionManager.getDefaultTimeoutSeconds());
		TransactionManager.execute(() -> {
			TransactionManager.withConnection(c -> {
				try (Statement statement = c.createStatement())
				{
					TransactionManager.prepareStatement(statement);
					assertEquals(3, statement.getQueryTimeout());
					return null;
				}
			});
			return null;
		});
	}

	@Test void proxyWithoutMethodTimeoutUsesCommonSetting() throws Exception
	{
		TransactionManager.setDefaultTimeoutSeconds(4);
		Service service = TransactionProxy.create(Service.class, new CommonTimeoutService());
		service.run();
	}

	public static class CommonTimeoutService implements Service
	{
		@Transactional public void run() throws IOException
		{
			try
			{
				TransactionManager.withConnection(c -> {
					try (Statement statement = c.createStatement())
					{
						TransactionManager.prepareStatement(statement);
						assertEquals(4, statement.getQueryTimeout());
						return null;
					}
				});
			}
			catch (Exception e)
			{
				throw new IOException(e);
			}
		}
	}

	@Test void explicitTimeoutOverridesCommonDefault() throws Throwable
	{
		TransactionManager.setDefaultTimeoutSeconds(8);
		TransactionManager.execute(new TransactionDefinition(Propagation.REQUIRED, Isolation.DEFAULT, 2, false), () -> {
			TransactionManager.withConnection(c -> {
				try (Statement statement = c.createStatement())
				{
					TransactionManager.prepareStatement(statement);
					assertEquals(2, statement.getQueryTimeout());
					return null;
				}
			});
			return null;
		});
	}

	@Test void explicitUnlimitedTimeoutOverridesCommonDefault() throws Throwable
	{
		TransactionManager.setDefaultTimeoutSeconds(8);
		TransactionManager.execute(new TransactionDefinition(Propagation.REQUIRED, Isolation.DEFAULT, TransactionDefinition.NO_TIMEOUT, false), () -> {
			TransactionManager.withConnection(c -> {
				try (Statement statement = c.createStatement())
				{
					TransactionManager.prepareStatement(statement);
					assertEquals(0, statement.getQueryTimeout());
					return null;
				}
			});
			return null;
		});
	}

	@Test void activeOwnerKeepsSnapshotOfCommonTimeout() throws Throwable
	{
		TransactionManager.setDefaultTimeoutSeconds(5);
		TransactionManager.execute(() -> {
			TransactionManager.setDefaultTimeoutSeconds(1);
			TransactionManager.withConnection(c -> {
				try (Statement statement = c.createStatement())
				{
					TransactionManager.prepareStatement(statement);
					assertEquals(5, statement.getQueryTimeout());
					return null;
				}
			});
			return null;
		});
	}

	@Test void commonDeadlineRejectsCommitWithoutAnyMethodTimeout() throws Exception
	{
		TransactionManager.setDefaultTimeoutSeconds(1);
		assertThrows(TransactionTimeoutException.class, () -> TransactionManager.execute(() -> {
			insert(1);
			Thread.sleep(1100);
			return null;
		}));
		assertEquals(0, committedCount());
	}

	@Test void sharedTimeoutCanBeDisabledAndInvalidValuesAreRejected() throws Throwable
	{
		assertThrows(IllegalArgumentException.class, () -> TransactionManager.setDefaultTimeoutSeconds(0));
		assertThrows(IllegalArgumentException.class, () -> TransactionManager.setDefaultTimeoutSeconds(-2));
		TransactionManager.setDefaultTimeoutSeconds(-1);
		TransactionManager.execute(() -> {
			TransactionManager.withConnection(c -> {
				try (Statement statement = c.createStatement())
				{
					TransactionManager.prepareStatement(statement);
					assertEquals(0, statement.getQueryTimeout());
					return null;
				}
			});
			return null;
		});
	}

	public static class PlainService implements Service
	{
		private final boolean slow;

		PlainService(boolean slow)
		{
			this.slow = slow;
		}

		public void run() throws IOException
		{
			assertTrue(TransactionManager.isActive());
			insert(1);
			insert(2);
			try
			{
				assertEquals(0, committedCount());
				TransactionManager.withConnection(c -> {
					try (Statement statement = c.createStatement())
					{
						TransactionManager.prepareStatement(statement);
						assertEquals(slow ? 1 : 4, statement.getQueryTimeout());
						return null;
					}
				});
				if (slow)
					Thread.sleep(1100);
			}
			catch (Exception e)
			{
				throw new IOException(e);
			}
		}
	}

	@Test void unannotatedProxyCommitsAtBoundaryAndUsesSharedTimeout() throws Exception
	{
		TransactionManager.setDefaultTimeoutSeconds(4);
		TransactionProxy.create(Service.class, new PlainService(false)).run();
		assertEquals(2, committedCount());
	}

	@Test void unannotatedProxyRejectsExpiredCommitAndRollsBack() throws Exception
	{
		TransactionManager.setDefaultTimeoutSeconds(1);
		Service service = TransactionProxy.create(Service.class, new PlainService(true));
		assertThrows(TransactionTimeoutException.class, service::run);
		assertEquals(0, committedCount());
	}

	public static class PlainChild implements Service
	{
		public void run() throws IOException
		{
			insert(2);
			throw new IOException("plain child");
		}
	}

	public static class PlainParent implements Service
	{
		final Service child;
		final boolean catchFailure;

		PlainParent(Service child, boolean catchFailure)
		{
			this.child = child;
			this.catchFailure = catchFailure;
		}

		public void run() throws IOException
		{
			insert(1);
			try
			{
				child.run();
			}
			catch (IOException e)
			{
				if (!catchFailure)
					throw e;
			}
		}
	}

	@Test void unannotatedCrossServiceFailureRollsBackSharedTransaction() throws Exception
	{
		Service child = TransactionProxy.create(Service.class, new PlainChild());
		Service parent = TransactionProxy.create(Service.class, new PlainParent(child, false));
		assertEquals("plain child", assertThrows(IOException.class, parent::run).getMessage());
		assertEquals(0, committedCount());
	}

	@Test void caughtUnannotatedParticipantFailureStillMarksRollbackOnly() throws Exception
	{
		Service child = TransactionProxy.create(Service.class, new PlainChild());
		Service parent = TransactionProxy.create(Service.class, new PlainParent(child, true));
		assertThrows(UnexpectedRollbackException.class, parent::run);
		assertEquals(0, committedCount());
	}

	public static class PlainAsyncImplementation implements AsyncService
	{
		public CompletionStage<Void> run()
		{
			throw new AssertionError("Async method must be rejected before invocation");
		}
	}

	@Test void unannotatedAsyncMethodIsRejectedBeforeInvocation()
	{
		assertThrows(TransactionException.class, TransactionProxy.create(AsyncService.class, new PlainAsyncImplementation())::run);
	}

	public static class NoTransactionChild implements Service
	{
		@Transactional(propagation = Propagation.NOT_SUPPORTED) public void run()
		{
			assertFalse(TransactionManager.isActive());
			insert(2);
		}
	}

	@Test void optionalAnnotationOverridesUnannotatedProxyDefaults() throws Exception
	{
		Service child = TransactionProxy.create(Service.class, new NoTransactionChild());
		Service parent = TransactionProxy.create(Service.class, new Service()
		{
			public void run() throws IOException
			{
				insert(1);
				child.run();
				throw new IOException();
			}
		});
		assertThrows(IOException.class, parent::run);
		assertEquals(1, committedCount());
	}

	@Test void clearOperationsClearsBothCollectionsAndPreventsOldReplay() throws Exception
	{
		PersistenceObject object = new PersistenceObject();
		Row first = new Row("TxItem");
		first.set("id", 1);
		first.set("label", "first");
		object.addRowForInsert(first);
		PersistenceManager.update(object);
		object.clearOperations();
		assertFalse(object.hasOperations());
		assertTrue(object.getOperations().isEmpty());
		assertEquals(1, object.getRows("TxItem").size());
		Row next = new Row("TxItem");
		next.set("id", 2);
		next.set("label", "next");
		object.addRowForInsert(next);
		PersistenceManager.update(object);
		object.clearOperations();
		assertEquals(2, committedCount());
		assertFalse(object.hasOperations());
	}
}
