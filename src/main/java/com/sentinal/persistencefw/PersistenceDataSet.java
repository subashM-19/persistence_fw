package com.sentinal.persistencefw;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class PersistenceDataSet implements Iterable<PersistenceDataSetRow>
{
	private final List<PersistenceDataSetRow> rows = new ArrayList<>();

	void addRow(Map<String, Object> values)
	{
		if (values == null)
		{
			throw new IllegalArgumentException(
				"PersistenceDataSet row cannot be null"
			);
		}

		rows.add(new PersistenceDataSetRow(values));
	}

	public int size()
	{
		return rows.size();
	}

	public boolean isEmpty()
	{
		return rows.isEmpty();
	}

	@Override
	public Iterator<PersistenceDataSetRow> iterator()
	{
		return rows.iterator();
	}
}