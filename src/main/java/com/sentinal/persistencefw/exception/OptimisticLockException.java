package com.sentinal.persistencefw.exception;

public class OptimisticLockException extends PersistenceException
{
	public OptimisticLockException(String message)
	{
		super(message);
	}
}