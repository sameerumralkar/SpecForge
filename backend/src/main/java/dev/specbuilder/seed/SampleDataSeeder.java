package dev.specbuilder.seed;

import dev.specbuilder.repository.ProjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** On first start (no projects yet) adds the Order Service sample. Turn off with specbuilder.seed-sample=false. */
@Component
@ConditionalOnProperty(name = "specbuilder.seed-sample", havingValue = "true", matchIfMissing = true)
public class SampleDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SampleDataSeeder.class);

    private final ProjectRepository projects;
    private final SampleProjects samples;

    public SampleDataSeeder(ProjectRepository projects, SampleProjects samples) {
        this.projects = projects;
        this.samples = samples;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (projects.count() == 0) {
            samples.createOrderService();
            log.info("Added the '{}' sample project", SampleProjects.ORDER_SERVICE);
        }
    }
}
