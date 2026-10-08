package com.sentinal.persistencefw.transaction;

import java.lang.reflect.*;
import java.util.Objects;

/**
 * Every service method uses a transaction by default, with optional annotation overrides.
 * Inject proxies into collaborating services so cross-class calls are intercepted.
 */
public final class TransactionProxy
{
	private TransactionProxy()
	{
	}

	public static <T> T create(Class<T> serviceInterface, T target)
	{
		Objects.requireNonNull(target);
		if (!serviceInterface.isInterface() || !serviceInterface.isInstance(target))
			throw new IllegalArgumentException("Target must implement the service interface");
		Object proxy = Proxy.newProxyInstance(serviceInterface.getClassLoader(), new Class<?>[] { serviceInterface }, (p, m, args) -> {
			if (m.getDeclaringClass() == Object.class)
			{
				switch (m.getName())
				{
					case "equals":
						return p == args[0];
					case "hashCode":
						return System.identityHashCode(p);
					case "toString":
						return "TransactionProxy(" + target.getClass().getName() + ")";
					default:
						throw new IllegalStateException(m.getName());
				}
			}
			Method implementation = target.getClass().getMethod(m.getName(), m.getParameterTypes());
			Transactional annotation = implementation.getAnnotation(Transactional.class);
			if (annotation == null)
				annotation = m.getAnnotation(Transactional.class);
			if (annotation == null)
				annotation = target.getClass().getAnnotation(Transactional.class);
			if (annotation == null)
				annotation = serviceInterface.getAnnotation(Transactional.class);
			if (java.util.concurrent.Future.class.isAssignableFrom(m.getReturnType()) || java.util.concurrent.CompletionStage.class.isAssignableFrom(m.getReturnType()))
				throw new TransactionException("Transactional methods must complete synchronously; wrap work inside the worker thread");
			TransactionManager.Work<Object> work = () -> {
				try
				{
					if (!implementation.canAccess(target) && !implementation.trySetAccessible())
						throw new TransactionException("Service method is inaccessible: " + implementation);
					return implementation.invoke(target, args);
				}
				catch (InvocationTargetException e)
				{
					throw e.getCause();
				}
			};
			TransactionDefinition definition = annotation == null ? TransactionDefinition.DEFAULT : TransactionDefinition.from(annotation);
			return TransactionManager.execute(definition, work);
		});
		return serviceInterface.cast(proxy);
	}
}
