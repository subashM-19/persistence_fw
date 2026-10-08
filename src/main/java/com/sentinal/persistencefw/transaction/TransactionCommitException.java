package com.sentinal.persistencefw.transaction;

public class TransactionCommitException extends TransactionException
{
	private static final long serialVersionUID = 1L;

	public TransactionCommitException(String message)
	{
		super(message);
	}

	public TransactionCommitException(String message, Throwable cause)
	{
		super(message, cause);
	}
}
