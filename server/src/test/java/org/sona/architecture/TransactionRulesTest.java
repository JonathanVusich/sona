package org.sona.architecture;

import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.transaction.annotation.Transactional;

import static com.tngtech.archunit.core.domain.properties.CanBeAnnotated.Predicates.annotatedWith;
import static com.tngtech.archunit.lang.conditions.ArchPredicates.are;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

/**
 * Keeps every {@code @Transactional} boundary where Spring's proxy can apply it.
 */
@AnalyzeClasses(packages = "org.sona", importOptions = ImportOption.DoNotIncludeTests.class)
class TransactionRulesTest {

    // Allowed to match nothing, since the DAOs put @Transactional on the class rather than on methods
    @ArchTest
    static final ArchRule transactionalMethodsArePublic = methods()
            .that().areAnnotatedWith(Transactional.class)
            .should().bePublic()
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule transactionalClassesAreNotFinal = classes()
            .that().areAnnotatedWith(Transactional.class)
            .or().containAnyMethodsThat(are(annotatedWith(Transactional.class)))
            .should().notHaveModifier(JavaModifier.FINAL);
}
