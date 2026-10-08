package com.sentinal.persistencefw.transaction;

public class UnexpectedRollbackException extends TransactionException
{
	private static final long serialVersionUID = 1L;

	public UnexpectedRollbackException(String message)
	{
		super(message);
	}

	public UnexpectedRollbackException(String message, Throwable cause)
	{
		super(message, cause);
	}
}
