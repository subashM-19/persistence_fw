package com.sentinal.persistencefw;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class PersistenceOperations implements Externalizable
{
	private HashMap<String, List<PersistenceActions>> tableVsActionInfo =
		new HashMap<>();

	public PersistenceOperations()
	{
	}

	public PersistenceActions addActionInfo(int operation, Row row)
	{
		if (row == null)
		{
			throw new IllegalArgumentException("Row cannot be null");
		}

		PersistenceActions persistenceActions = new PersistenceActions(operation, row);

		tableVsActionInfo
			.computeIfAbsent(row.getTableName(), key -> new ArrayList<>())
			.add(persistenceActions);

		return persistenceActions;
	}

	public List<PersistenceActions> getActionInfo(String tableName)
	{
		return tableVsActionInfo.getOrDefault(
			tableName,
			new ArrayList<>()
		);
	}

	public HashMap<String, List<PersistenceActions>> getTableVsActionInfo()
	{
		return tableVsActionInfo;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException
	{
		out.writeObject(tableVsActionInfo);
	}

	@Override
	@SuppressWarnings("unchecked")
	public void readExternal(ObjectInput in)
		throws IOException, ClassNotFoundException
	{
		tableVsActionInfo =
			(HashMap<String, List<PersistenceActions>>) in.readObject();
	}
}