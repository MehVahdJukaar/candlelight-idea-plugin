package net.mehvahdjukaar.flash;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;

public class CommonUtils {


    public static Project findProjectByName(String name) {
        ProjectManager pm = ApplicationManager.getApplication().getService(ProjectManager.class);
        if (pm == null) return null;
        Project[] projects = pm.getOpenProjects();
        for (Project p : projects) {
            if (name.equals(p.getName())) {
                return p;
            }
        }
        return null;
    }

    public static String getOpenProjectNames() {
        ProjectManager pm = ApplicationManager.getApplication().getService(ProjectManager.class);
        if (pm == null) return "ProjectManager not available";
        Project[] projects = pm.getOpenProjects();
        if (projects.length == 0) return "No projects open";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < projects.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(projects[i].getName());
        }
        return sb.toString();
    }
}
