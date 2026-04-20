package net.mehvahdjukaar.flash;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class IdeaUtils {

    public static <T> T read(Supplier<T> function) {
        return ApplicationManager.getApplication()
            .runReadAction((ThrowableComputable<T, RuntimeException>) function::get);

    }

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

    public static List<String> getSuperclasses(Project project, String fqn) {

        PsiClass psiClass = JavaPsiFacade.getInstance(project).findClass(fqn, GlobalSearchScope.allScope(project));
        if (psiClass == null) {
            return new ArrayList<>();
        }
        List<String> superclasses = new ArrayList<>();
        PsiClass current = psiClass.getSuperClass();
        int count = 0;
        while (current != null && count < 5) {
            String qn = current.getQualifiedName();
            if (qn != null) {
                superclasses.add(qn);
            }
            current = current.getSuperClass();
            count++;
        }
        return superclasses;
    }
}
