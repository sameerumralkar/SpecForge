<#-- One processing rule as Camel DSL, and the sub-routes it needs. -->
<#import "/lib/java.ftl" as j>
<#function body type>
  <#return type + " current = exchange.getMessage().getBody(" + type + ".class);">
</#function>
<#-- A step with its comment, optional condition and optional guard. -->
<#macro step s>
            // ${j.doc(s.name)}: ${j.doc(s.summary)}
<#if s.condition??>
            .filter(${j.indent(s.condition, 12)})
</#if>
<#if s.guard??>
<#assign g = s.guard>
            // ${j.doc(g.summary)}
<#if g.catches()>
            .setProperty("${g.inputProperty}", body())
            .doTry()
                .to("${g.uri}")
            .doCatch(${g.classes})
<#if g.deadLetterUri??>
                .setBody(exchangeProperty("${g.inputProperty}"))
                .process(exchange -> exchange.getMessage().setBody(json.writeValueAsString(exchange.getMessage().getBody())))
                .to("${g.deadLetterUri}")
</#if>
<#if (g.fallback!"") == "ERROR_RESPONSE">
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(${g.status}))
                .setBody(constant("${j.doc(g.message)?j_string}"))
                .stop()
<#elseif (g.fallback!"") == "DEFAULT_RESPONSE">
                .setBody(exchangeProperty("${g.inputProperty}")) // carry on without this step
<#else>
                .stop() // the message went to the dead-letter destination
</#if>
            .end()
<#else>
            .to("${g.uri}")
</#if>
<#else>
<@dsl s/>
</#if>
<#if s.condition??>
            .end()
</#if>
</#macro>

<#-- The DSL of one step. -->
<#macro dsl s>
<#switch s.type>
<#case "VALIDATE">
<#if s.inType != "Object">
            .to("${s.data.beanValidatorUri}")
</#if>
            .process(exchange -> {
                ${body(s.inType)}
<#list s.data.checks as c>
                if (${c.failsWhen}) { // ${c.comment}
                    throw new RuleViolationException(${s.data.status}, ${c.message});
                }
</#list>
            })
<#break>
<#case "TRANSFORM">
            .bean(dtoMapper, "${s.data.mapperMethod}")
<#break>
<#case "RESPOND">
<#if s.data.mapperMethod??>
            .bean(dtoMapper, "${s.data.mapperMethod}")
<#else>
            .process(exchange -> exchange.getMessage().setBody(new ${s.outType}())) // TODO: build the response
</#if>
<#break>
<#case "ENRICH">
            .enrich("${s.data.subRouteUri}", (original, resource) -> {
                Exchange exchange = original;
                ${body(s.inType)}
                ${s.data.resultType} result = resource.getMessage().getBody(${s.data.resultType}.class);
                ${j.indent(s.data.assignment, 16)}
                return original;
            })
<#break>
<#case "CONDITIONAL_ROUTE">
            .choice()
<#list s.data.branches as b>
                .when(${j.indent(b.condition, 16)})
                    .to("${b.subRouteUri}")
</#list>
                .otherwise()
                    .to("${s.data.otherwiseUri}")
            .end()
<#break>
<#case "PERSIST">
<#assign p = s.data>
<#if p.operation == "SAVE">
            .bean(${p.storeField}, "save")
<#elseif p.operation == "FIND_BY_ID" && p.assignment??>
            .process(exchange -> {
                ${body(s.inType)}
                ${p.sourceType} loaded = ${p.storeField}.findById(${p.idExpr});
                ${j.indent(p.assignment, 16)}
            })
<#elseif p.operation == "FIND_BY_ID">
            .process(exchange -> {
                ${body(s.inType)}
                exchange.getMessage().setBody(${p.storeField}.findById(${p.idExpr}));
            })
<#elseif p.operation == "UPDATE" && p.sameType>
            .process(exchange -> {
                ${body(s.inType)}
                exchange.getMessage().setBody(${p.storeField}.update(${p.idExpr}, current));
            })
<#elseif p.operation == "UPDATE">
            .process(exchange -> {
                ${body(s.inType)}
                var $entity = ${p.storeField}.load(${p.idExpr});
                // TODO: copy the changes from the ${s.inType} onto the ${p.entityClass}.
                ${p.storeField}.saveEntity($entity);
            })
<#else>
            .process(exchange -> {
                ${body(s.inType)}
                ${p.storeField}.delete(${p.idExpr});
            })
</#if>
<#break>
<#case "PUBLISH_EVENT">
<#assign p = s.data>
            .setProperty("${s.methodName}Input", body())
<#if p.convertMethod??>
            .bean(dtoMapper, "${p.convertMethod}")
</#if>
<#if p.protocol == "KAFKA" && p.keyExpr != "null">
            .process(exchange -> {
                ${p.eventType} event = exchange.getMessage().getBody(${p.eventType}.class);
                exchange.getMessage().setHeader("kafka.KEY", ${p.keyExpr});
            })
</#if>
            .process(exchange -> exchange.getMessage().setBody(json.writeValueAsString(exchange.getMessage().getBody())))
            .to("${p.toUri}")
            .setBody(exchangeProperty("${s.methodName}Input"))
<#break>
<#case "SPLIT_AGGREGATE">
<#assign p = s.data>
            .setProperty("${s.methodName}Input", body())
            .process(exchange -> {
                ${body(s.inType)}
                exchange.getMessage().setBody(${p.listExpr});
            })
<#if p.aggregation == "COLLECT_LIST">
            .split(body(), AggregationStrategies.flexible().accumulateInCollection(ArrayList.class))<#if p.parallel>.parallelProcessing()</#if>
<#elseif p.aggregation == "SUM">
            .split(body(), (previous, next) -> {
                ${p.itemType} $it = next.getMessage().getBody(${p.itemType}.class);
                Object total = previous == null ? BigDecimal.ZERO : previous.getMessage().getBody();
                next.getMessage().setBody(Expressions.add(total, ${p.sumExpr}));
                return next;
            })<#if p.parallel>.parallelProcessing()</#if>
<#elseif p.aggregation == "COUNT">
            .split(body(), (previous, next) -> {
                long count = previous == null ? 0L : previous.getMessage().getBody(Long.class);
                next.getMessage().setBody(count + 1);
                return next;
            })<#if p.parallel>.parallelProcessing()</#if>
<#else>
            .split(body())<#if p.parallel>.parallelProcessing()</#if>
</#if>
                .to("${p.itemUri}")
            .end()
            .setProperty("${p.resultProperty}", body()) // TODO: use the result
            .setBody(exchangeProperty("${s.methodName}Input"))
<#break>
</#switch>
</#macro>

<#macro subRoutes r>
<#list r.steps as s>
<#if s.guard??>
<#assign g = s.guard>

        from("${g.uri}")
            .routeId("${g.routeId}")
            .errorHandler(defaultErrorHandler()<#if g.retry>
                .maximumRedeliveries(${g.maxRedeliveries})
                .redeliveryDelay(${g.delayMs?c}L)
                .backOffMultiplier(${g.multiplier?c})
                .useExponentialBackOff()</#if>)
<@dsl s/>
            ;
</#if>
<#if s.type == "ENRICH">

        from("${s.data.subRouteUri}")
            .routeId("${r.routeId}-${s.methodName}")
            .process(exchange -> {
                ${body(s.inType)}
                exchange.getMessage().setBody(${s.data.clientField}.${s.data.clientMethod}(${s.data.callArgs}));
            });
<#elseif s.type == "CONDITIONAL_ROUTE">
<#list s.data.branches as b>

        from("${b.subRouteUri}")
            .routeId("${r.routeId}-${b.methodName}")
            .log("${s.name}: branch ${b.name}"); // TODO: ${j.doc(b.description?has_content?then(b.description, "implement this branch"))}
</#list>

        from("${s.data.otherwiseUri}")
            .routeId("${r.routeId}-${s.methodName}-otherwise")
            .log("${s.name}: otherwise"); // ${j.doc(s.data.otherwise?has_content?then(s.data.otherwise, "continue"))}
<#elseif s.type == "SPLIT_AGGREGATE">

        from("${s.data.itemUri}")
            .routeId("${r.routeId}-${s.methodName}-item")
            .log("${s.name}: one item"); // TODO: process one item
</#if>
</#list>
</#macro>
