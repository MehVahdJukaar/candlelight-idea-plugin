package net.mehvahdjukaar.flash.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import jakarta.annotation.Nullable;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import net.mehvahdjukaar.flash.FlashPlugin;
import net.mehvahdjukaar.flash.IdeaUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Path("/")
public class FlashServlet {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @GET
    @Produces(MediaType.TEXT_HTML)
    public Response getRoot() {
        String html = "<html><body>" +
            "<h1>Candlelight Plugin HTTP Server</h1>" +
            "<p>Server is running on port 4303</p>" +
            "<h2>Available Endpoints</h2>" +
            "<ul>" +
            "<li><a href=\"/superclasses?class=java.lang.String\">/superclasses?class=java.lang.String</a> - Get superclasses for a class</li>" +
            "<li><a href=\"/method-content?class=java.lang.String&method=toString\">/method-content?class=java.lang.String&method=toString</a> - Get content of a method</li>" +
            "<li><a href=\"/class-content?class=java.lang.String\">/class-content?class=java.lang.String</a> - Get full content of a class</li>" +
            "<li><a href=\"/partial-class-content?class=java.lang.String&startLine=1&endLine=10\">/partial-class-content?class=java.lang.String&startLine=1&endLine=10</a> - Get partial content of a class</li>" +
            "<li><a href=\"/containing-method?class=java.lang.String&line=5\">/containing-method?class=java.lang.String&line=5</a> - Get the method containing a line</li>" +
            "<li><a href=\"/callers?class=java.lang.String&method=toString\">/callers?class=java.lang.String&method=toString</a> - Get callers of a method</li>" +
            "<li><a href=\"/declaration?class=java.lang.String&method=toString\">/declaration?class=java.lang.String&method=toString</a> - Get declaration of a method</li>" +
            "<li><a href=\"/implementations?class=java.lang.String&method=toString\">/implementations?class=java.lang.String&method=toString</a> - Get implementations of a method</li>" +
            "<li><a href=\"/class-info?class=java.lang.String\">/class-info?class=java.lang.String</a> - Get information about a class</li>" +
            "<li><a href=\"/class-usages?class=java.lang.String\">/class-usages?class=java.lang.String</a> - Get usages of a class</li>" +
            "<li><a href=\"/field-usages?class=java.lang.String&field=value\">/field-usages?class=java.lang.String&field=value</a> - Get usages of a field</li>" +
            "<li><a href=\"/class-inheritors?class=java.lang.String\">/class-inheritors?class=java.lang.String</a> - Get inheritors of a class</li>" +
            "<li><a href=\"/class-api?class=java.lang.String\">/class-api?class=java.lang.String</a> - Get method signatures of a class</li>" +
            "<li><a href=\"/class-fields?class=java.lang.String\">/class-fields?class=java.lang.String</a> - Get field signatures of a class</li>" +
            "</ul>" +
            "<h3>Debug Info</h3>" +
            "<p>Open projects: " + IdeaUtils.getOpenProjectNames() + "</p>" +
            "</body></html>";
        FlashPlugin.LOGGER.debug("Root endpoint accessed");
        return Response.ok(html).build();
    }

    private @Nullable Response getProjectsToCheck(List<Project> projects, @Nullable String projectName) {
        if (projectName != null) {
            Project project = IdeaUtils.findProjectByName(projectName);
            if (project == null) {
                FlashPlugin.LOGGER.debug("Project not found: " + projectName);
                return Response.status(Response.Status.BAD_REQUEST).entity("Project not found").build();
            }
            projects.add(project);
        } else {
            ProjectManager pm = ApplicationManager.getApplication().getService(ProjectManager.class);
            if (pm == null) {
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("ProjectManager not available").build();
            }
            Project[] openProjects = IdeaUtils.read(pm::getOpenProjects);
            projects.addAll(List.of(openProjects));
            FlashPlugin.LOGGER.debug("Checking all projects: " + Arrays.toString(openProjects));
        }
        return null;
    }

    @GET
    @Path("method-content")
    @Produces(MediaType.TEXT_PLAIN)
    public Response getMethodContent(@QueryParam("class") String className,
                                     @QueryParam("method") String methodName,
                                     @QueryParam("project") String projectName) {
        FlashPlugin.LOGGER.debug("Method content endpoint called - class: " + className + ", method: " + methodName + ", project: " + projectName);
        if (className == null || methodName == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Missing 'class' or 'method' parameter").build();
        }

        List<Project> projectsToCheck = new ArrayList<>();
        var resp = getProjectsToCheck(projectsToCheck, projectName);
        if (resp != null) return resp;

        List<String> resolvedFQNs = new ArrayList<>();
        resp = resolveClassName(className, projectsToCheck, resolvedFQNs);
        if (resp != null) return resp;

        String fqn = resolvedFQNs.get(0);
        for (Project project : projectsToCheck) {
            String content = IdeaUtils.read(() ->
                IdeaUtils.getContent(project, fqn, methodName, null, null));

            if (!content.isEmpty()) {
                FlashPlugin.LOGGER.debug("Found method content in project " + project.getName());
                return Response.ok(content, MediaType.TEXT_PLAIN).build();
            }
        }
        FlashPlugin.LOGGER.debug("No method content found for class: " + fqn + ", method: " + methodName);
        return Response.ok("", MediaType.TEXT_PLAIN).build();
    }
    @GET
    @Path("class-content")
    @Produces(MediaType.TEXT_PLAIN)
    public Response getClassContent(@QueryParam("class") String className,
                                    @QueryParam("project") String projectName) {
        FlashPlugin.LOGGER.debug("Class content endpoint called - class: " + className + ", project: " + projectName);
        if (className == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Missing 'class' parameter").build();
        }

        List<Project> projectsToCheck = new ArrayList<>();
        var resp = getProjectsToCheck(projectsToCheck, projectName);
        if (resp != null) return resp;

        List<String> resolvedFQNs = new ArrayList<>();
        resp = resolveClassName(className, projectsToCheck, resolvedFQNs);
        if (resp != null) return resp;

        String fqn = resolvedFQNs.get(0);
        for (Project project : projectsToCheck) {
            String content = IdeaUtils.read(() ->
                IdeaUtils.getContent(project, fqn, null, null, null));

            if (!content.isEmpty()) {
                FlashPlugin.LOGGER.debug("Found class content in project " + project.getName());
                return Response.ok(content, MediaType.TEXT_PLAIN).build();
            }
        }
        FlashPlugin.LOGGER.debug("No class content found for class: " + fqn);
        return Response.ok("", MediaType.TEXT_PLAIN).build();
    }

    @GET
    @Path("partial-class-content")
    @Produces(MediaType.TEXT_PLAIN)
    public Response getPartialClassContent(@QueryParam("class") String className,
                                           @QueryParam("startLine") int startLine,
                                           @QueryParam("endLine") int endLine,
                                           @QueryParam("project") String projectName) {
        FlashPlugin.LOGGER.debug("Partial class content endpoint called - class: " + className + ", startLine: " + startLine + ", endLine: " + endLine + ", project: " + projectName);
        if (className == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Missing 'class' parameter").build();
        }
        if (startLine <= 0 || endLine <= 0 || startLine > endLine) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Invalid startLine or endLine").build();
        }

        List<Project> projectsToCheck = new ArrayList<>();
        var resp = getProjectsToCheck(projectsToCheck, projectName);
        if (resp != null) return resp;

        List<String> resolvedFQNs = new ArrayList<>();
        resp = resolveClassName(className, projectsToCheck, resolvedFQNs);
        if (resp != null) return resp;

        String fqn = resolvedFQNs.get(0);
        for (Project project : projectsToCheck) {
            String content = IdeaUtils.read(() ->
                IdeaUtils.getContent(project, fqn, null, startLine, endLine));

            if (!content.isEmpty()) {
                FlashPlugin.LOGGER.debug("Found partial class content in project " + project.getName());
                return Response.ok(content, MediaType.TEXT_PLAIN).build();
            }
        }
        FlashPlugin.LOGGER.debug("No partial class content found for class: " + fqn);
        return Response.ok("", MediaType.TEXT_PLAIN).build();
    }


    @GET
    @Path("containing-method")
    @Produces(MediaType.TEXT_PLAIN)
    public Response getContainingMethod(@QueryParam("class") String className,
                                        @QueryParam("line") int lineNumber,
                                        @QueryParam("project") String projectName) {
        FlashPlugin.LOGGER.debug("Containing method endpoint called - class: " + className + ", line: " + lineNumber + ", project: " + projectName);
        if (className == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Missing 'class' parameter").build();
        }
        if (lineNumber <= 0) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Invalid line number").build();
        }

        List<Project> projectsToCheck = new ArrayList<>();
        var resp = getProjectsToCheck(projectsToCheck, projectName);
        if (resp != null) return resp;

        List<String> resolvedFQNs = new ArrayList<>();
        resp = resolveClassName(className, projectsToCheck, resolvedFQNs);
        if (resp != null) return resp;

        String fqn = resolvedFQNs.get(0);
        for (Project project : projectsToCheck) {
            String content = IdeaUtils.read(() ->
                IdeaUtils.getContainingMethodContent(project, fqn, lineNumber));

            if (!content.isEmpty()) {
                FlashPlugin.LOGGER.debug("Found containing method content in project " + project.getName());
                return Response.ok(content, MediaType.TEXT_PLAIN).build();
            }
        }
        FlashPlugin.LOGGER.debug("No containing method found for class: " + fqn + ", line: " + lineNumber);
        return Response.status(Response.Status.BAD_REQUEST).entity("Line not inside a method").build();
    }

    @GET
    @Path("callers")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getCallers(@QueryParam("class") String className,
                               @QueryParam("method") String methodName,
                               @QueryParam("project") String projectName,
                               @QueryParam("includeDependencies") Boolean includeDependencies) {
        FlashPlugin.LOGGER.debug("Callers endpoint called - class: " + className + ", method: " + methodName + ", project: " + projectName + ", includeDependencies: " + includeDependencies);
        if (className == null || methodName == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Missing 'class' or 'method' parameter").build();
        }

        List<Project> projectsToCheck = new ArrayList<>();
        var resp = getProjectsToCheck(projectsToCheck, projectName);
        if (resp != null) return resp;

        List<String> resolvedFQNs = new ArrayList<>();
        resp = resolveClassName(className, projectsToCheck, resolvedFQNs);
        if (resp != null) return resp;

        String fqn = resolvedFQNs.get(0);
        List<Map<String, Object>> allCallers = new ArrayList<>();
        boolean onlyProject = includeDependencies == null || !includeDependencies;
        for (Project project : projectsToCheck) {
            List<Map<String, Object>> callers = IdeaUtils.read(() ->
                IdeaUtils.getMethodCallers(project, fqn, methodName, onlyProject));
            allCallers.addAll(callers);
        }
        try {
            String json = MAPPER.writeValueAsString(allCallers);
            return Response.ok(json, MediaType.APPLICATION_JSON).build();
        } catch (JsonProcessingException e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Error serializing response").build();
        }
    }

    @GET
    @Path("superclasses")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getSuperclasses(@QueryParam("class") String className,
                                    @QueryParam("project") String projectName) {
        FlashPlugin.LOGGER.debug("Superclasses endpoint called - class: " + className + ", project: " + projectName);
        if (className == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Missing 'class' parameter").build();
        }

        List<Project> projectsToCheck = new ArrayList<>();
        var resp = getProjectsToCheck(projectsToCheck, projectName);
        if (resp != null) return resp;

        List<String> resolvedFQNs = new ArrayList<>();
        resp = resolveClassName(className, projectsToCheck, resolvedFQNs);
        if (resp != null) return resp;

        String fqn = resolvedFQNs.get(0);
        List<String> allSuperclasses = new ArrayList<>();
        for (Project project : projectsToCheck) {
            List<String> superclasses = IdeaUtils.read(() ->
                IdeaUtils.getSuperclasses(project, fqn));
            allSuperclasses.addAll(superclasses);
        }
        try {
            String json = MAPPER.writeValueAsString(allSuperclasses);
            return Response.ok(json, MediaType.APPLICATION_JSON).build();
        } catch (JsonProcessingException e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Error serializing response").build();
        }
    }

    @GET
    @Path("class-api")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getClassApi(@QueryParam("class") String className,
                                @QueryParam("project") String projectName,
                                @QueryParam("visibility") String visibility,
                                @QueryParam("inside") Boolean inside,
                                @QueryParam("deep") Boolean deep) {
        FlashPlugin.LOGGER.debug("Class API endpoint called - class: " + className + ", project: " + projectName + ", visibility: " + visibility + ", inside: " + inside + ", deep: " + deep);
        if (className == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Missing 'class' parameter").build();
        }

        // Default values
        final String vis = visibility == null ? "public" : visibility;
        final boolean isInside = inside != null && inside;
        final boolean isDeep = deep != null && deep;

        List<Project> projectsToCheck = new ArrayList<>();
        var resp = getProjectsToCheck(projectsToCheck, projectName);
        if (resp != null) return resp;

        List<String> resolvedFQNs = new ArrayList<>();
        resp = resolveClassName(className, projectsToCheck, resolvedFQNs);
        if (resp != null) return resp;

        String fqn = resolvedFQNs.get(0);
        List<String> allMethodSignatures = new ArrayList<>();
        for (Project project : projectsToCheck) {
            List<String> signatures = IdeaUtils.read(() ->
                IdeaUtils.getClassMethods(project, fqn, vis, isInside, isDeep));
            allMethodSignatures.addAll(signatures);
        }
        try {
            String json = MAPPER.writeValueAsString(allMethodSignatures);
            return Response.ok(json, MediaType.APPLICATION_JSON).build();
        } catch (JsonProcessingException e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Error serializing response").build();
        }
    }

    @GET
    @Path("class-fields")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getClassFields(@QueryParam("class") String className,
                                   @QueryParam("project") String projectName,
                                   @QueryParam("visibility") String visibility,
                                   @QueryParam("inside") Boolean inside,
                                   @QueryParam("deep") Boolean deep) {
        FlashPlugin.LOGGER.debug("Class fields endpoint called - class: " + className + ", project: " + projectName + ", visibility: " + visibility + ", inside: " + inside + ", deep: " + deep);
        if (className == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Missing 'class' parameter").build();
        }

        final String vis = visibility == null ? "public" : visibility;
        final boolean isInside = inside != null && inside;
        final boolean isDeep = deep != null && deep;

        List<Project> projectsToCheck = new ArrayList<>();
        var resp = getProjectsToCheck(projectsToCheck, projectName);
        if (resp != null) return resp;

        List<String> resolvedFQNs = new ArrayList<>();
        resp = resolveClassName(className, projectsToCheck, resolvedFQNs);
        if (resp != null) return resp;

        String fqn = resolvedFQNs.get(0);
        List<String> allFieldSignatures = new ArrayList<>();
        for (Project project : projectsToCheck) {
            List<String> signatures = IdeaUtils.read(() ->
                IdeaUtils.getClassFields(project, fqn, vis, isInside, isDeep));
            allFieldSignatures.addAll(signatures);
        }
        try {
            String json = MAPPER.writeValueAsString(allFieldSignatures);
            return Response.ok(json, MediaType.APPLICATION_JSON).build();
        } catch (JsonProcessingException e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Error serializing response").build();
        }
    }

    private @Nullable Response resolveClassName(String className, List<Project> projectsToCheck, List<String> resolvedFQNs) {
        resolvedFQNs.clear();
        List<String> allFQNs = new ArrayList<>();
        for (Project project : projectsToCheck) {
            List<String> fqns = IdeaUtils.read(() ->
                IdeaUtils.findClassFQNs(project, className));
            allFQNs.addAll(fqns);
        }
        if (allFQNs.isEmpty()) {
            FlashPlugin.LOGGER.debug("No classes found for: " + className);
            return Response.status(Response.Status.NOT_FOUND).entity("Class not found").build();
        } else if (allFQNs.size() == 1) {
            resolvedFQNs.add(allFQNs.get(0));
            return null;
        } else {
            // Multiple, return JSON list
            try {
                String json = MAPPER.writeValueAsString(allFQNs);
                FlashPlugin.LOGGER.debug("Multiple classes found for: " + className + ", returning list");
                return Response.ok(json, MediaType.APPLICATION_JSON).build();
            } catch (JsonProcessingException e) {
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Error serializing response").build();
            }
        }
    }
}
