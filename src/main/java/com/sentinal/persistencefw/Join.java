package com.sentinal.persistencefw;

public class Join
{
	public static final int INNER = 1;
	public static final int LEFT = 2;
	public static final int RIGHT = 3;

	private final int joinType;
	private final String tableName;
	private final String leftTable;
	private final String leftColumn;
	private final String rightTable;
	private final String rightColumn;

	public Join(
		int joinType,
		String tableName,
		String leftTable,
		String leftColumn,
		String rightTable,
		String rightColumn)
	{
		this.joinType = joinType;
		this.tableName = tableName;
		this.leftTable = leftTable;
		this.leftColumn = leftColumn;
		this.rightTable = rightTable;
		this.rightColumn = rightColumn;
	}

	public int getJoinType()
	{
		return joinType;
	}

	public String getTableName()
	{
		return tableName;
	}

	public String getLeftTable()
	{
		return leftTable;
	}

	public String getLeftColumn()
	{
		return leftColumn;
	}

	public String getRightTable()
	{
		return rightTable;
	}

	public String getRightColumn()
	{
		return rightColumn;
	}
}