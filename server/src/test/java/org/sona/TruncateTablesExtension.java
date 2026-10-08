package org.sona;

import org.jooq.DSLContext;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.sona.model.Sona;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Empties every table in Sona's schema after each test.
 */
public class TruncateTablesExtension implements AfterEachCallback {

    @Override
    public void afterEach(final ExtensionContext context) {
        final var dsl = SpringExtension.getApplicationContext(context).getBean(DSLContext.class);
        // Taken from the generated schema, so new tables are truncated too.
        final var tables = Sona.SONA.getTables();
        dsl.truncate(tables).cascade().execute();
    }
}
