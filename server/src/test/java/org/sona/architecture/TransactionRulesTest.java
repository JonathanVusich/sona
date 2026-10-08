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
 * Transaction boundaries sit on public methods of service beans, one per unit of work. DAOs only join a transaction
 * (propagation {@code MANDATORY}), so these rules keep every DAO call behind a service and every boundary where
 * Spring's proxy can apply it.
 */
@AnalyzeClasses(packages = "org.sona", importOptions = ImportOption.DoNotIncludeTests.class)
class TransactionRulesTest {

    @ArchTest
    static final ArchRule daosOnlyUsedByServices = classes()
            .that().resideInAPackage("..db..")
            .should().onlyBeAccessed().byAnyPackage("..db..", "..store..");

    // Allowed to match nothing, since a codebase may have no service methods yet
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
