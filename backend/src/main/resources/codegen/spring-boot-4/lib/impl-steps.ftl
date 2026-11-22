<#-- One processing rule as a private method of a service implementation. -->
<#import "/lib/java.ftl" as j>
<#macro step s>

    /** ${j.doc(s.name)}: ${j.doc(s.summary)} */
    private ${s.outType} ${s.methodName}${s.signature} {
<#switch s.type>
<#case "VALIDATE">
        if (current != null) {
            var $violations = validator.validate(current);
            if (!$violations.isEmpty()) {
                var $first = $violations.iterator().next();
                throw new RuleViolationException(${s.data.status}, $first.getPropertyPath() + " " + $first.getMessage());
            }
        }
<#list s.data.checks as c>
        // ${c.comment}
        if (${c.failsWhen}) {
            throw new RuleViolationException(${s.data.status}, ${c.message});
        }
</#list>
        return current;
<#break>
<#case "TRANSFORM">
        return dtoMapper.${s.data.mapperMethod}(current);
<#break>
<#case "ENRICH">
        ${s.data.resultType} result = ${s.data.clientField}.${s.data.clientMethod}(${s.data.callArgs});
        ${j.indent(s.data.assignment, 8)}
        return current;
<#break>
<#case "CONDITIONAL_ROUTE">
<#list s.data.branches as b>
        <#if !b?is_first>} else </#if>if (${b.condition}) {
            ${b.methodName}(${s.args});
</#list>
        } else {
            // Otherwise: ${j.doc(s.data.otherwise!"continue")}
        }
        return current;
<#break>
<#case "PERSIST">
<#assign p = s.data>
<#if p.operation == "SAVE">
        return ${p.storeField}.save(current);
<#elseif p.operation == "FIND_BY_ID">
        return ${p.storeField}.findById(${p.idExpr});
<#elseif p.operation == "UPDATE" && p.sameType>
        return ${p.storeField}.update(${p.idExpr}, current);
<#elseif p.operation == "UPDATE">
        var $entity = ${p.storeField}.load(${p.idExpr});
        // TODO: copy the changes from the ${s.inType} onto the ${p.entityClass}.
        ${p.storeField}.saveEntity($entity);
        return current;
<#else>
        ${p.storeField}.delete(${p.idExpr});
        return current;
</#if>
<#break>
<#case "PUBLISH_EVENT">
<#assign p = s.data>
<#if p.convertMethod??>
        ${p.eventType} event = dtoMapper.${p.convertMethod}(current);
<#elseif p.eventType == s.inType>
        ${p.eventType} event = current;
<#else>
        ${p.eventType} event = new ${p.eventType}(); // TODO: fill in the event
</#if>
<#if p.protocol == "KAFKA">
        messaging.sendKafka("${p.destination}", ${p.keyExpr}, event);
<#elseif p.protocol == "JMS">
        messaging.sendJms("${p.destination}", event);
<#else>
        messaging.sendAmqp("${p.destination}", event);
</#if>
        return current;
<#break>
<#case "SPLIT_AGGREGATE">
<#assign p = s.data>
        var $items = ${p.listExpr}.stream()<#if p.parallel>.parallel()</#if>.map(this::${p.itemMethod});
<#if p.aggregation == "COLLECT_LIST">
        List<${p.itemType}> $results = $items.toList();
        log.debug("${s.name}: {} items processed", $results.size()); // TODO: use the results
<#elseif p.aggregation == "SUM">
        BigDecimal $total = Expressions.sum($items.map($it -> (Object) ${p.sumExpr}).toList());
        log.debug("${s.name}: total {}", $total); // TODO: use the total
<#elseif p.aggregation == "COUNT">
        long $count = $items.count();
        log.debug("${s.name}: {} items", $count); // TODO: use the count
<#else>
        $items.forEach($it -> { });
</#if>
        return current;
<#break>
</#switch>
    }
<#if s.type == "CONDITIONAL_ROUTE">
<#list s.data.branches as b>

    /** Branch "${b.name}"<#if b.description?has_content>: ${j.doc(b.description)}</#if>. */
    private void ${b.methodName}${s.signature} {
        // TODO: ${j.doc(b.description?has_content?then(b.description, "implement this branch"))}
        log.info("${s.name}: branch ${b.name}");
    }
</#list>
</#if>
<#if s.type == "SPLIT_AGGREGATE">

    /** Processes one item for "${j.doc(s.name)}". */
    private ${s.data.itemType} ${s.data.itemMethod}(${s.data.itemType} item) {
        // TODO: process one item
        return item;
    }
</#if>
</#macro>
