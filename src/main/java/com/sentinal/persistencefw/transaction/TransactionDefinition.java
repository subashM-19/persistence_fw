package com.sentinal.persistencefw.transaction;

import java.util.*;

/**
 * Immutable options; by default all Exceptions and Errors cause rollback.
 */
public final class TransactionDefinition
{
	/**
	 * Inherit the application-wide timeout, resolved when a new transaction begins.
	 */
	public static final int DEFAULT_TIMEOUT = -1;
	/**
	 * Explicitly disable the deadline for this new transaction.
	 */
	public static final int NO_TIMEOUT = -2;
	public static final TransactionDefinition DEFAULT = new TransactionDefinition(Propagation.REQUIRED, Isolation.DEFAULT, -1, false);
	final Propagation propagation;
	final Isolation isolation;
	final int timeout;
	final boolean readOnly;
	private final List<Class<? extends Throwable>> rollbackFor, noRollbackFor;

	public TransactionDefinition(Propagation propagation, Isolation isolation, int timeout, boolean readOnly)
	{
		this(propagation, isolation, timeout, readOnly, List.of(Exception.class), List.of());
	}

	public TransactionDefinition(Propagation propagation, Isolation isolation, int timeout, boolean readOnly, List<Class<? extends Throwable>> rollbackFor, List<Class<? extends Throwable>> noRollbackFor)
	{
		this.propagation = Objects.requireNonNull(propagation);
		this.isolation = Objects.requireNonNull(isolation);
		if (timeout != DEFAULT_TIMEOUT && timeout != NO_TIMEOUT && timeout <= 0)
			throw new IllegalArgumentException("Timeout must be positive seconds, DEFAULT_TIMEOUT (-1), or NO_TIMEOUT (-2)");
		this.timeout = timeout;
		this.readOnly = readOnly;
		this.rollbackFor = List.copyOf(rollbackFor);
		this.noRollbackFor = List.copyOf(noRollbackFor);
	}

	static TransactionDefinition from(Transactional a)
	{
		return new TransactionDefinition(a.propagation(), a.isolation(), a.timeout(), a.readOnly(), Arrays.asList(a.rollbackFor()), Arrays.asList(a.noRollbackFor()));
	}

	boolean rollsBack(Throwable failure)
	{
		if (failure instanceof Error || failure instanceof TransactionException)
			return true;
		int yes = distance(failure.getClass(), rollbackFor), no = distance(failure.getClass(), noRollbackFor);
		return yes != Integer.MAX_VALUE && yes < no; // closest type wins; noRollback wins ties
	}

	private static int distance(Class<?> type, List<Class<? extends Throwable>> rules)
	{
		int best = Integer.MAX_VALUE;
		for (Class<?> rule : rules)
		{
			int depth = 0;
			for (Class<?> c = type; c != null; c = c.getSuperclass(), depth++)
				if (c == rule)
					best = Math.min(best, depth);
		}
		return best;
	}
}
