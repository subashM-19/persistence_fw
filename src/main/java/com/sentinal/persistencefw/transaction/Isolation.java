package com.sentinal.persistencefw.transaction;

import java.sql.Connection;

public enum Isolation
{
	DEFAULT(-1),
    READ_UNCOMMITTED(Connection.TRANSACTION_READ_UNCOMMITTED),
    READ_COMMITTED(Connection.TRANSACTION_READ_COMMITTED),
    REPEATABLE_READ(Connection.TRANSACTION_REPEATABLE_READ),
    SERIALIZABLE(Connection.TRANSACTION_SERIALIZABLE);
	final int jdbc;

	Isolation(int jdbc)
	{
		this.jdbc = jdbc;
	}
}
