package com.sentinal.persistencefw.transaction;

import java.lang.annotation.*;

@Inherited @Retention(RetentionPolicy.RUNTIME) @Target({ ElementType.TYPE, ElementType.METHOD }) public @interface Transactional
{
	Propagation propagation() default Propagation.REQUIRED;

	Isolation isolation() default Isolation.DEFAULT;

	int timeout() default TransactionDefinition.DEFAULT_TIMEOUT; // inherit the application-wide default

	boolean readOnly() default false;

	Class<? extends Throwable>[] rollbackFor() default { Exception.class };

	Class<? extends Throwable>[] noRollbackFor() default {};
}
