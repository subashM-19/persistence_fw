package com.sentinal.persistencefw.transaction;

public class TransactionTimeoutException extends TransactionException
{
	private static final long serialVersionUID = 1L;

	public TransactionTimeoutException(String message)
	{
		super(message);
	}

	public TransactionTimeoutException(String message, Throwable cause)
	{
		super(message, cause);
	}
}
