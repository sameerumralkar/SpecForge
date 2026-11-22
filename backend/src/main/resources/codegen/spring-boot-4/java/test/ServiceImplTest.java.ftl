package ${project.basePackage}.service;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/** Test skeleton for ${item.serviceImplClass}: one test per endpoint, to be written. */
class ${item.testClass} {
<#list item.implEndpoints as e>

    @Test
    @Disabled("TODO: test ${e.name}")
    void ${e.name}() {
    }
</#list>
}
