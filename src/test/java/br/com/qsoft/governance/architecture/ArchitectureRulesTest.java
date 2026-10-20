package br.com.qsoft.governance.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.qsoft.governance.iam.domain.rbac.GrantOrigin;
import br.com.qsoft.governance.iam.domain.rbac.Role;
import br.com.qsoft.governance.iam.domain.rbac.RoleAssignment;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

class ArchitectureRulesTest {

    private static final String ROOT = "br.com.qsoft.governance";

    private static final Set<String> BUSINESS_MODULES = Set.of(
        "iam", "department", "project", "aigateway", "chat",
        "agentpolicies", "observability", "engagement", "governancescore"
    );

    private static final Map<String, Set<String>> ALLOWED_DEPENDENCIES = Map.of(
        "iam", Set.of(),
        "department", Set.of("iam"),
        "project", Set.of("iam", "department"),
        "aigateway", Set.of("iam", "department"),
        "chat", Set.of("iam", "aigateway"),
        "agentpolicies", Set.of("iam", "department", "aigateway"),
        "observability", Set.of("iam", "department"),
        "engagement", Set.of("iam", "observability", "aigateway"),
        "governancescore", Set.of("iam", "engagement", "observability")
    );

    @Test
    void domainMustRemainFrameworkIndependent() {
        JavaClasses classes = classes();

        ArchRule rule = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "org.springframework..",
                "jakarta.persistence..",
                "..application..",
                "..infrastructure.."
            );

        rule.check(classes);
    }

    @Test
    void sharedKernelMustNotDependOnBusinessModules() {
        JavaClasses classes = classes();

        String[] forbidden = BUSINESS_MODULES.stream()
            .map(module -> ROOT + "." + module + "..")
            .toArray(String[]::new);

        noClasses()
            .that().resideInAPackage(ROOT + ".sharedkernel..")
            .should().dependOnClassesThat().resideInAnyPackage(forbidden)
            .check(classes);
    }

    @Test
    void moduleDependenciesMustFollowAllowlistAndPublicApi() {
        JavaClasses classes = classes();
        List<String> violations = new ArrayList<>();

        for (JavaClass origin : classes) {
            String originModule = moduleOf(origin.getPackageName());
            if (originModule == null || !BUSINESS_MODULES.contains(originModule)) {
                continue;
            }

            for (Dependency dependency : origin.getDirectDependenciesFromSelf()) {
                JavaClass target = dependency.getTargetClass();
                String targetModule = moduleOf(target.getPackageName());

                if (targetModule == null
                    || targetModule.equals(originModule)
                    || targetModule.equals("sharedkernel")) {
                    continue;
                }

                if (!BUSINESS_MODULES.contains(targetModule)) {
                    continue;
                }

                if (!ALLOWED_DEPENDENCIES
                    .getOrDefault(originModule, Set.of())
                    .contains(targetModule)) {
                    violations.add(originModule + " -> " + targetModule
                        + " não está na allowlist: " + dependency.getDescription());
                    continue;
                }

                String publicApiPrefix = ROOT + "." + targetModule + ".api";
                if (!target.getPackageName().startsWith(publicApiPrefix)) {
                    violations.add(originModule + " acessa parte interna de " + targetModule
                        + ": " + dependency.getDescription());
                }
            }
        }

        assertThat(violations)
            .as("Dependências cross-module devem usar somente module.api.* e a allowlist")
            .isEmpty();
    }

    @Test
    void firstLevelPackagesMustBeFreeOfCycles() {
        slices()
            .matching(ROOT + ".(*)..")
            .should().beFreeOfCycles()
            .check(classes());
    }

    /**
     * Regra 1 (SPEC-01 §10): autorização não lê o rótulo hierárquico. Nenhuma
     * classe de `iam.application.rbac`, `iam.domain.rbac`, do
     * `PermissionInterceptor` ou do `observability` depende de `HierarchyLevel`.
     */
    @Test
    void authorizationMustNotDependOnHierarchyLevel() {
        JavaClasses classes = classes();
        String hierarchyLevel = ROOT + ".iam.domain.user.HierarchyLevel";

        noClasses().that().resideInAnyPackage(
                ROOT + ".iam.application.rbac..",
                ROOT + ".iam.domain.rbac..",
                ROOT + ".observability..")
            .should().dependOnClassesThat().haveFullyQualifiedName(hierarchyLevel)
            .check(classes);

        noClasses().that().haveFullyQualifiedName(
                ROOT + ".iam.infrastructure.web.PermissionInterceptor")
            .should().dependOnClassesThat().haveFullyQualifiedName(hierarchyLevel)
            .check(classes);
    }

    /**
     * Regra 2 (SPEC-01 §10): o construtor e `grant` de `RoleAssignment` só são
     * chamados pela aplicação, pelo próprio domínio rbac e pelo mapper JPA.
     */
    @Test
    void roleAssignmentConstructionIsRestricted() {
        JavaClasses classes = classes();

        noClasses().that().resideOutsideOfPackages(
                ROOT + ".iam.application..",
                ROOT + ".iam.domain.rbac..",
                ROOT + ".iam.infrastructure.persistence..")
            .should().callConstructor(RoleAssignment.class)
            .orShould().callMethod(RoleAssignment.class, "grant",
                UserId.class, Role.class, UUID.class, GrantOrigin.class, UserId.class, Instant.class)
            .check(classes);
    }

    private JavaClasses classes() {
        return new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(ROOT);
    }

    private String moduleOf(String packageName) {
        String prefix = ROOT + ".";
        if (!packageName.startsWith(prefix)) {
            return null;
        }
        String remainder = packageName.substring(prefix.length());
        int dot = remainder.indexOf('.');
        return dot >= 0 ? remainder.substring(0, dot) : remainder;
    }
}
