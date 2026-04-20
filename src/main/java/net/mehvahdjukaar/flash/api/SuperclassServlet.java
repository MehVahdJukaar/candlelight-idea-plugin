package net.mehvahdjukaar.flash.api;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import net.mehvahdjukaar.flash.CommonUtils;
import net.mehvahdjukaar.flash.SuperclassUtil;

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
                "</ul>" +
                "<h3>Debug Info</h3>" +
                "<p>Open projects: " + CommonUtils. getOpenProjectNames() + "</p>" +
                "</body></html>";
        System.out.println("[DEBUG] Root endpoint accessed");
        return Response.ok(html).build();
    }

    @GET
    @Path("superclasses")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getSuperclasses(@QueryParam("class") String className, @QueryParam("project") String projectName) {
        System.out.println("[DEBUG] Superclasses endpoint called - class: " + className + ", project: " + projectName);
        if (className == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Missing 'class' parameter").build();
        }
        List<Project> projectsToCheck;
        if (projectName != null) {
            Project project =CommonUtils. findProjectByName(projectName);
            if (project == null) {
                System.out.println("[DEBUG] Project not found: " + projectName);
                return Response.status(Response.Status.BAD_REQUEST).entity("Project not found").build();
            }
            projectsToCheck = List.of(project);
        } else {
            ProjectManager pm = ApplicationManager.getApplication().getService(ProjectManager.class);
            if (pm == null) {
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("ProjectManager not available").build();
            }
            projectsToCheck = List.of(pm.getOpenProjects());
            System.out.println("[DEBUG] Checking all projects: " + Arrays.toString(pm.getOpenProjects()));
        }
        for (Project project : projectsToCheck) {
            List<String> superclasses = SuperclassUtil.getSuperclasses(project, className);
            if (!superclasses.isEmpty()) {
                System.out.println("[DEBUG] Found superclasses in project " + project.getName() + ": " + superclasses);
                try {
                    String json = MAPPER.writeValueAsString(superclasses);
                    return Response.ok(json, MediaType.APPLICATION_JSON).build();
                } catch (JsonProcessingException e) {
                    return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Error serializing response").build();
                }
            }
        }
        System.out.println("[DEBUG] No superclasses found for class: " + className);
        // If no superclasses found in any project, return empty list
        return Response.ok("[]", MediaType.APPLICATION_JSON).build();
    }

}
