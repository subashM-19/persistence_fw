package com.sentinal.persistencefw.transaction;

import java.sql.Connection;
import java.util.*;

final class TransactionContext
{
	enum State
	{ACTIVE, COMMITTED, ROLLED_BACK, UNKNOWN}

	final Connection connection;
	final boolean originalAutoCommit, originalReadOnly, readOnly;
	final int originalIsolation;
	final long start = System.nanoTime(), budget;
	boolean rollbackOnly;
	State state = State.ACTIVE;
	final List<Runnable> afterCommit = new ArrayList<>();

	TransactionContext(Connection connection, TransactionDefinition d) throws java.sql.SQLException
	{
		this.connection = connection;
		originalAutoCommit = connection.getAutoCommit();
		originalReadOnly = connection.isReadOnly();
		originalIsolation = connection.getTransactionIsolation();
		readOnly = d.readOnly;
		int timeout = d.timeout == TransactionDefinition.DEFAULT_TIMEOUT ? TransactionManager.getDefaultTimeoutSeconds() : d.timeout;
		budget = timeout < 0 ? -1 : timeout * 1_000_000_000L;
	}

	long remaining()
	{
		if (state != State.ACTIVE)
			throw new TransactionException("Transaction is already completed: " + state);
		long remaining = budget == -1 ? Long.MAX_VALUE : budget - (System.nanoTime() - start);
		if (remaining <= 0)
		{
			rollbackOnly = true;
			throw new TransactionTimeoutException("Transaction deadline exceeded");
		}
		return remaining;
	}
}
