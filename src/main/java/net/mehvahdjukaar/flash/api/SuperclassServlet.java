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

@Path("/")
public class SuperclassServlet {
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
            "<li><a href=\"/content?class=java.lang.String&method=toString\">/content?class=java.lang.String&method=toString</a> - Get content of a method or class</li>" +
            "<li><a href=\"/callers?class=java.lang.String&method=toString\">/callers?class=java.lang.String&method=toString</a> - Get callers of a method</li>" +
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
            projects.addAll(List.of(pm.getOpenProjects()));
            FlashPlugin.LOGGER.debug("Checking all projects: " + Arrays.toString(pm.getOpenProjects()));
        }
        return null;
    }

    @GET
    @Path("content")
    @Produces(MediaType.TEXT_PLAIN)
    public Response getContent(@QueryParam("class") String className,
                               @QueryParam("method") String methodName,
                               @QueryParam("startLine") Integer startLine,
                               @QueryParam("endLine") Integer endLine,
                               @QueryParam("project") String projectName) {
        FlashPlugin.LOGGER.debug("Content endpoint called - class: " + className + ", method: " + methodName + ", startLine: " + startLine + ", endLine: " + endLine + ", project: " + projectName);
        if (className == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Missing 'class' parameter").build();
        }
        if (methodName != null && (startLine != null || endLine != null)) {
            FlashPlugin.LOGGER.error("Cannot specify startLine or endLine when method is provided");
            return Response.status(Response.Status.BAD_REQUEST).entity("Cannot specify startLine or endLine when method is provided").build();
        }

        List<Project> projectsToCheck = new ArrayList<>();
        var resp = getProjectsToCheck(projectsToCheck, projectName);
        if (resp != null) return resp;

        for (Project project : projectsToCheck) {
            String content = IdeaUtils.read(() ->
                IdeaUtils.getContent(project, className, methodName, startLine, endLine));

            if (!content.isEmpty()) {
                FlashPlugin.LOGGER.debug("Found content in project " + project.getName());
                return Response.ok(content, MediaType.TEXT_PLAIN).build();
            }
        }
        FlashPlugin.LOGGER.debug("No content found for class: " + className + ", method: " + methodName);
        // If no content found in any project, return empty
        return Response.ok("", MediaType.TEXT_PLAIN).build();
    }

    @GET
    @Path("callers")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getCallers(@QueryParam("class") String className,
                               @QueryParam("method") String methodName,
                               @QueryParam("project") String projectName) {
        FlashPlugin.LOGGER.debug("Callers endpoint called - class: " + className + ", method: " + methodName + ", project: " + projectName);
        if (className == null || methodName == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Missing 'class' or 'method' parameter").build();
        }

        List<Project> projectsToCheck = new ArrayList<>();
        var resp = getProjectsToCheck(projectsToCheck, projectName);
        if (resp != null) return resp;

        List<String> allCallers = new ArrayList<>();
        for (Project project : projectsToCheck) {
            List<String> callers = IdeaUtils.read(() ->
                IdeaUtils.getMethodCallers(project, className, methodName));
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

        List<String> allSuperclasses = new ArrayList<>();
        for (Project project : projectsToCheck) {
            List<String> superclasses = IdeaUtils.read(() ->
                IdeaUtils.getSuperclasses(project, className));
            allSuperclasses.addAll(superclasses);
        }
        try {
            String json = MAPPER.writeValueAsString(allSuperclasses);
            return Response.ok(json, MediaType.APPLICATION_JSON).build();
        } catch (JsonProcessingException e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Error serializing response").build();
        }
    }
}
