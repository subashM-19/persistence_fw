package com.sentinal.persistencefw;

public class PersistenceActions
{
	public static final int INSERT = 1;
	public static final int UPDATE = 2;
	public static final int DELETE = 3;
	public static final int ON_DELETE_CASCADE = 4;

	private int operation;
	private Row value;

	public PersistenceActions()
	{
	}

	public PersistenceActions(int operation, Row value)
	{
		this.operation = operation;
		this.value = value;
	}

	public int getOperation()
	{
		return operation;
	}

	public Row getValue()
	{
		return value;
	}
}