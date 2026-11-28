package dev.specbuilder.reports;

import dev.specbuilder.reports.Reports.CapabilityMap;
import dev.specbuilder.reports.Reports.EndpointsReport;
import dev.specbuilder.reports.Reports.FieldsReport;
import dev.specbuilder.reports.Reports.ObjectsReport;
import dev.specbuilder.reports.Reports.ProjectsReport;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** The reports, across every project, or one project with {@code ?projectId=}. */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reports;

    public ReportController(ReportService reports) {
        this.reports = reports;
    }

    @GetMapping("/projects")
    public ProjectsReport projects(@RequestParam(required = false) UUID projectId) {
        return reports.projects(projectId);
    }

    @GetMapping("/endpoints")
    public EndpointsReport endpoints(@RequestParam(required = false) UUID projectId) {
        return reports.endpoints(projectId);
    }

    @GetMapping("/objects")
    public ObjectsReport objects(@RequestParam(required = false) UUID projectId) {
        return reports.objects(projectId);
    }

    @GetMapping("/fields")
    public FieldsReport fields(@RequestParam(required = false) UUID projectId) {
        return reports.fields(projectId);
    }

    /** One project's capabilities, endpoints, rules, objects and fields, linked, for the capability map. */
    @GetMapping("/map")
    public CapabilityMap map(@RequestParam UUID projectId) {
        return reports.map(projectId);
    }
}
