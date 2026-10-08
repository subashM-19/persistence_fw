package com.sentinal.persistencefw.transaction;

import com.sentinal.persistencefw.PersistenceManager;

import java.sql.*;
import java.util.Objects;

/**
 * Synchronous JDBC transactions bound strictly to the calling thread.
 */
public final class TransactionManager
{
	private TransactionManager()
	{
	}

	private static volatile int defaultTimeoutSeconds = 30;

	/**
	 * Configure once at startup. Positive seconds, or -1 to disable the shared deadline.
	 */
	public static void setDefaultTimeoutSeconds(int seconds)
	{
		if (seconds != -1 && seconds <= 0)
			throw new IllegalArgumentException("Default timeout must be -1 or positive seconds");
		defaultTimeoutSeconds = seconds;
	}

	public static int getDefaultTimeoutSeconds()
	{
		return defaultTimeoutSeconds;
	}

	@FunctionalInterface public interface Work<T>
	{
		T run() throws Throwable;
	}

	@FunctionalInterface public interface SqlWork<T>
	{
		T run(Connection connection) throws Exception;
	}

	private static final ThreadLocal<TransactionContext> CURRENT = new ThreadLocal<>();
	private static final ThreadLocal<Scope> SCOPE = new ThreadLocal<>();

	private static final class Scope
	{
		final TransactionContext context;
		final Scope previous;
		final boolean nested;
		boolean rollbackOnly;

		Scope(TransactionContext context, Scope previous, boolean nested)
		{
			this.context = context;
			this.previous = previous;
			this.nested = nested;
		}
	}

	public static boolean isActive()
	{
		return CURRENT.get() != null;
	}

	public static void checkTimeout()
	{
		if (isActive())
			CURRENT.get().remaining();
	}

	public static void setRollbackOnly()
	{
		TransactionContext c = CURRENT.get();
		if (c == null)
			throw new TransactionException("No active transaction");
		for (Scope s = SCOPE.get(); s != null && s.context == c; s = s.previous)
		{
			if (s.nested)
			{
				s.rollbackOnly = true;
				return;
			}
		}
		c.rollbackOnly = true;
	}

	public static boolean isRollbackOnly()
	{
		TransactionContext c = CURRENT.get();
		if (c == null)
			return false;
		if (c.rollbackOnly)
			return true;
		for (Scope s = SCOPE.get(); s != null && s.context == c; s = s.previous)
			if (s.rollbackOnly)
				return true;
		return false;
	}

	public static void assertWritable()
	{
		checkTimeout();
		if (isActive() && CURRENT.get().readOnly)
		{
			setRollbackOnly();
			throw new TransactionException("Writes and locking reads are forbidden in read-only transactions");
		}
	}

	public static void afterCommit(Runnable action)
	{
		Objects.requireNonNull(action);
		if (!isActive())
			throw new TransactionException("No active transaction");
		CURRENT.get().afterCommit.add(action);
	}

	public static void prepareStatement(Statement statement) throws SQLException
	{
		TransactionContext c = CURRENT.get();
		if (c != null)
		{
			long remaining = c.remaining();
			if (c.budget != -1)
				statement.setQueryTimeout((int) Math.min(Integer.MAX_VALUE, Math.max(1, (remaining + 999_999_999L) / 1_000_000_000L)));
		}
	}

	/**
	 * Never closes a transaction-owned connection. SQL failures mark the current scope rollback-only.
	 */
	public static <T> T withConnection(SqlWork<T> work) throws Exception
	{
		TransactionContext c = CURRENT.get();
		if (c == null)
		{
			try (Connection connection = PersistenceManager.openTransactionConnection())
			{
				return work.run(connection);
			}
		}
		try
		{
			c.remaining();
			T result = work.run(c.connection);
			c.remaining();
			return result;
		}
		catch (Exception | Error failure)
		{
			setRollbackOnly();
			throw failure;
		}
	}

	public static <T> T execute(Work<T> work) throws Throwable
	{
		return execute(TransactionDefinition.DEFAULT, work);
	}

	public static <T> T execute(TransactionDefinition d, Work<T> work) throws Throwable
	{
		Objects.requireNonNull(d);
		Objects.requireNonNull(work);
		TransactionContext previous = CURRENT.get();
		Scope oldScope = SCOPE.get();
		if (d.propagation == Propagation.MANDATORY && previous == null)
			throw new TransactionException("MANDATORY requires an existing transaction");
		if (d.propagation == Propagation.NEVER && previous != null)
			throw new TransactionException("NEVER forbids an existing transaction");
		boolean suspend = d.propagation == Propagation.REQUIRES_NEW || d.propagation == Propagation.NOT_SUPPORTED;
		if (suspend)
			CURRENT.remove();
		TransactionContext c = CURRENT.get();
		boolean owner = false;
		Savepoint savepoint = null;
		int callbackCount = 0;
		Scope scope = null;
		Throwable failure = null;
		T result = null;
		try
		{
			if (c == null && (d.propagation == Propagation.REQUIRED || d.propagation == Propagation.REQUIRES_NEW || d.propagation == Propagation.NESTED))
			{
				c = begin(d);
				CURRENT.set(c);
				owner = true;
			}
			else if (c != null)
			{
				c.remaining();
				if (d.isolation != Isolation.DEFAULT && d.isolation.jdbc != c.connection.getTransactionIsolation())
					throw new TransactionException("Joined transaction has a different isolation level");
				if (!d.readOnly && c.readOnly)
					throw new TransactionException("Cannot join read-only transaction with writable definition");
				if (d.propagation == Propagation.NESTED)
				{
					if (!c.connection.getMetaData().supportsSavepoints())
						throw new TransactionException("Driver does not support NESTED savepoints");
					savepoint = c.connection.setSavepoint();
					callbackCount = c.afterCommit.size();
				}
			}
			scope = new Scope(c, oldScope, savepoint != null);
			SCOPE.set(scope);
			try
			{
				result = work.run();
			}
			catch (Throwable e)
			{
				failure = e;
			}
			if (c != null)
			{
				try
				{
					c.remaining();
				}
				catch (Throwable e)
				{
					failure = combine(failure, e);
				}
				boolean rollback = scope.rollbackOnly || c.rollbackOnly || (failure != null && d.rollsBack(failure));
				if (owner)
				{
					if (rollback)
					{
						rollback(c);
						if (failure == null)
							failure = new UnexpectedRollbackException("Transaction marked rollback-only");
					}
					else
					{
						try
						{
							c.connection.commit();
							c.state = TransactionContext.State.COMMITTED;
						}
						catch (SQLException e)
						{
							c.state = TransactionContext.State.UNKNOWN;
							throw new TransactionCommitException("Commit failed; database outcome may be unknown", e);
						}
						for (Runnable action : c.afterCommit)
						{
							try
							{
								action.run();
							}
							catch (Throwable e)
							{
								failure = combine(failure, new TransactionException("After-commit action failed; database is committed", e));
							}
						}
					}
				}
				else if (savepoint != null)
				{
					if (rollback)
					{
						c.connection.rollback(savepoint);
						c.afterCommit.subList(callbackCount, c.afterCommit.size()).clear();
						if (failure == null)
							failure = new UnexpectedRollbackException("Nested scope marked rollback-only");
					}
					c.connection.releaseSavepoint(savepoint);
				}
				else if (rollback)
				{
					setRollbackOnly();
				}
			}
		}
		catch (Throwable e)
		{
			failure = combine(failure, e);
			if (c != null && owner && c.state == TransactionContext.State.ACTIVE)
			{
				try
				{
					rollback(c);
				}
				catch (Throwable cleanup)
				{
					failure = combine(failure, cleanup);
				}
			}
			else if (c != null && !owner)
			{
				c.rollbackOnly = true;
			}
		}
		finally
		{
			if (owner && c != null)
				failure = cleanup(c, failure);
			if (previous == null)
				CURRENT.remove();
			else
				CURRENT.set(previous);
			if (oldScope == null)
				SCOPE.remove();
			else
				SCOPE.set(oldScope);
		}
		if (failure != null)
			throw failure;
		return result;
	}

	private static TransactionContext begin(TransactionDefinition d) throws SQLException
	{
		Connection connection = PersistenceManager.openTransactionConnection();
		TransactionContext c = null;
		try
		{
			c = new TransactionContext(connection, d);
			if (!c.originalAutoCommit)
				throw new TransactionException("Connection provider must supply an idle auto-commit connection");
			connection.setReadOnly(d.readOnly);
			if (d.isolation != Isolation.DEFAULT)
				connection.setTransactionIsolation(d.isolation.jdbc);
			connection.setAutoCommit(false);
			c.remaining();
			return c;
		}
		catch (SQLException | RuntimeException | Error e)
		{
			if (c != null)
			{
				try
				{
					if (c.originalAutoCommit && !connection.getAutoCommit())
						rollback(c);
				}
				catch (Throwable x)
				{
					e.addSuppressed(x);
					c.state = TransactionContext.State.UNKNOWN;
				}
				cleanup(c, e);
			}
			else
				try
				{
					connection.close();
				}
				catch (Throwable x)
				{
					e.addSuppressed(x);
				}
			throw e;
		}
	}

	private static void rollback(TransactionContext c) throws SQLException
	{
		if (c.state != TransactionContext.State.ACTIVE)
			throw new TransactionException("Invalid rollback state: " + c.state);
		try
		{
			c.connection.rollback();
			c.state = TransactionContext.State.ROLLED_BACK;
		}
		catch (SQLException e)
		{
			c.state = TransactionContext.State.UNKNOWN;
			throw e;
		}
	}

	private static Throwable cleanup(TransactionContext c, Throwable failure)
	{
		// Do not set autoCommit(true) after uncertain completion: it could accidentally commit.
		boolean discard = c.state == TransactionContext.State.UNKNOWN;
		if (!discard)
		{
			try
			{
				c.connection.setTransactionIsolation(c.originalIsolation);
			}
			catch (Throwable e)
			{
				failure = combine(failure, e);
				discard = true;
			}
			try
			{
				c.connection.setReadOnly(c.originalReadOnly);
			}
			catch (Throwable e)
			{
				failure = combine(failure, e);
				discard = true;
			}
			try
			{
				c.connection.setAutoCommit(c.originalAutoCommit);
			}
			catch (Throwable e)
			{
				failure = combine(failure, e);
				discard = true;
			}
		}
		if (discard)
		{
			// An uncertain or unrestorable pooled connection must be discarded.
			try
			{
				c.connection.abort(Runnable::run);
			}
			catch (Throwable e)
			{
				failure = combine(failure, e);
			}
		}
		try
		{
			c.connection.close();
		}
		catch (Throwable e)
		{
			failure = combine(failure, e);
			try
			{
				c.connection.abort(Runnable::run);
			}
			catch (Throwable abort)
			{
				failure = combine(failure, abort);
			}
		}
		return failure;
	}

	private static Throwable combine(Throwable primary, Throwable extra)
	{
		if (primary == null)
			return extra;
		if (primary != extra)
			primary.addSuppressed(extra);
		return primary;
	}
}
