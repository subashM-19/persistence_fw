package com.sentinal.persistencefw.transaction;

import com.sentinal.persistencefw.exception.PersistenceException;

public class TransactionException extends PersistenceException
{
	private static final long serialVersionUID = 1L;

	public TransactionException(String message)
	{
		super(message);
	}

	public TransactionException(String message, Throwable cause)
	{
		super(message, cause);
	}
}
