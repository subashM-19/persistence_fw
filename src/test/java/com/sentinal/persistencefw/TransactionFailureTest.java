package com.sentinal.persistencefw;

import com.sentinal.persistencefw.transaction.*;
import org.junit.jupiter.api.*;

import java.sql.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.logging.Logger;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Faults are injected at the JDBC boundary while real H2 handles the SQL.
 */
class TransactionFailureTest
{
	static final String URL = TransactionTest.URL;
	Driver original;
	FaultDriver faults;

	@BeforeEach void setup() throws Exception
	{
		PersistenceManager.initialize(URL, "sa", "");
		original = DriverManager.getDriver(URL);
		DriverManager.deregisterDriver(original);
		faults = new FaultDriver(original);
		DriverManager.registerDriver(faults);
	}

	@AfterEach void clean() throws Exception
	{
		DriverManager.deregisterDriver(faults);
		DriverManager.registerDriver(original);
		assertFalse(TransactionManager.isActive());
	}

	static class FaultDriver implements Driver
	{
		final Driver delegate;
		final List<String> calls = new ArrayList<>();
		String fail;
		Connection connection;

		FaultDriver(Driver delegate)
		{
			this.delegate = delegate;
		}

		public Connection connect(String url, Properties props) throws SQLException
		{
			if (!acceptsURL(url))
				return null;
			Connection actual = delegate.connect(url, props);
			connection = (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { Connection.class }, (p, m, args) -> {
				calls.add(m.getName());
				if (m.getName().equals(fail))
				{
					if (fail.equals("close"))
						actual.close();
					throw new SQLException("injected " + fail);
				}
				if (m.getName().equals("abort"))
				{
					actual.close();
					return null;
				}
				try
				{
					return m.invoke(actual, args);
				}
				catch (InvocationTargetException e)
				{
					throw e.getCause();
				}
			});
			return connection;
		}

		public boolean acceptsURL(String url) throws SQLException
		{
			return delegate.acceptsURL(url);
		}

		public DriverPropertyInfo[] getPropertyInfo(String u, Properties p) throws SQLException
		{
			return delegate.getPropertyInfo(u, p);
		}

		public int getMajorVersion()
		{
			return delegate.getMajorVersion();
		}

		public int getMinorVersion()
		{
			return delegate.getMinorVersion();
		}

		public boolean jdbcCompliant()
		{
			return delegate.jdbcCompliant();
		}

		public Logger getParentLogger() throws SQLFeatureNotSupportedException
		{
			return delegate.getParentLogger();
		}
	}

	@Test void commitFailureHasDistinctExceptionAndDiscardsConnection() throws Exception
	{
		faults.fail = "commit";
		TransactionCommitException e = assertThrows(TransactionCommitException.class, () -> TransactionManager.execute(() -> null));
		assertEquals("injected commit", e.getCause().getMessage());
		assertTrue(faults.calls.contains("abort"));
		assertTrue(faults.connection.isClosed());
		assertFalse(faults.calls.contains("rollback"));
		assertEquals(1, Collections.frequency(faults.calls, "setAutoCommit"));
	}

	@Test void rollbackFailurePreservesBusinessExceptionAndAborts()
	{
		faults.fail = "rollback";
		IOException business = new IOException("business");
		IOException actual = assertThrows(IOException.class, () -> TransactionManager.execute(() -> {
			throw business;
		}));
		assertSame(business, actual);
		assertEquals("injected rollback", actual.getSuppressed()[0].getMessage());
		assertTrue(faults.calls.contains("abort"));
		assertTrue(faults.calls.contains("close"));
	}

	@Test void closeFailureDoesNotLoseOriginalException()
	{
		faults.fail = "close";
		IOException business = new IOException("business");
		assertSame(business, assertThrows(IOException.class, () -> TransactionManager.execute(() -> {
			throw business;
		})));
		assertEquals("injected close", business.getSuppressed()[0].getMessage());
	}

	@Test void connectionSettingsRestoredBeforeClose() throws Throwable
	{
		TransactionManager.execute(new TransactionDefinition(Propagation.REQUIRED, Isolation.SERIALIZABLE, -1, false), () -> null);
		assertEquals(2, Collections.frequency(faults.calls, "setAutoCommit"));
		assertEquals(2, Collections.frequency(faults.calls, "setTransactionIsolation"));
		assertEquals("close", faults.calls.get(faults.calls.size() - 1));
	}

	@Test void beginFailureClosesConnectionAndCleansContext() throws Exception
	{
		faults.fail = "setReadOnly";
		assertThrows(SQLException.class, () -> TransactionManager.execute(() -> null));
		assertTrue(faults.connection.isClosed());
	}

	@Test void joinedDeadlineNotResetAndExpiredTransactionRejectsFurtherSql() throws Throwable
	{
		assertThrows(TransactionTimeoutException.class, () -> TransactionManager.execute(new TransactionDefinition(Propagation.REQUIRED, Isolation.DEFAULT, 1, false), () -> {
			Thread.sleep(1100);
			assertThrows(TransactionTimeoutException.class, () -> TransactionManager.withConnection(c -> null));
			return null;
		}));
		assertTrue(faults.calls.contains("rollback"));
		assertFalse(faults.calls.contains("commit"));
	}

	@Test void unsupportedSavepointsFailExplicitly() throws Throwable
	{
		// The H2 driver supports savepoints; a failed setSavepoint must poison the owner safely.
		faults.fail = "setSavepoint";
		assertThrows(UnexpectedRollbackException.class, () -> TransactionManager.execute(() -> {
			assertThrows(SQLException.class, () -> TransactionManager.execute(new TransactionDefinition(Propagation.NESTED, Isolation.DEFAULT, -1, false), () -> null));
			return null;
		}));
	}

	@Test void restorationFailureDiscardsConnectionEvenAfterCommit()
	{
		assertThrows(SQLException.class, () -> TransactionManager.execute(() -> {
			faults.fail = "setTransactionIsolation";
			return null;
		}));
		assertTrue(faults.calls.contains("commit"));
		assertTrue(faults.calls.contains("abort"));
	}
}
