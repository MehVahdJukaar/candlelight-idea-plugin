package net.mehvahdjukaar.flash;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.Query;

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

    public static String getContent(Project project, String fqn, String methodName, Integer startLine, Integer endLine) {
        PsiClass psiClass = JavaPsiFacade.getInstance(project).findClass(fqn, GlobalSearchScope.allScope(project));
        if (psiClass == null) {
            return "";
        }
        if (methodName != null) {
            PsiMethod[] methods = psiClass.findMethodsByName(methodName, false);
            if (methods.length > 0) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < methods.length; i++) {
                    if (i > 0) sb.append("\n\n");
                    sb.append(methods[i].getText());
                }
                return sb.toString();
            } else {
                return "";
            }
        } else {
            String text = psiClass.getText();
            if (startLine != null && endLine != null) {
                String[] lines = text.split("\n");
                int start = Math.max(0, startLine - 1);
                int end = Math.min(lines.length, endLine);
                if (start >= end) return "";
                StringBuilder sb = new StringBuilder();
                for (int i = start; i < end; i++) {
                    if (i > start) sb.append("\n");
                    sb.append(lines[i]);
                }
                return sb.toString();
            } else {
                return text;
            }
        }
    }

    public static List<String> getMethodCallers(Project project, String fqn, String methodName) {
        PsiClass psiClass = JavaPsiFacade.getInstance(project).findClass(fqn, GlobalSearchScope.allScope(project));
        if (psiClass == null) {
            return new ArrayList<>();
        }
        PsiMethod[] methods = psiClass.findMethodsByName(methodName, false);
        List<String> callers = new ArrayList<>();
        for (PsiMethod method : methods) {
            Query<PsiReference> query = ReferencesSearch.search(method, GlobalSearchScope.allScope(project));
            for (PsiReference ref : query.findAll()) {
                PsiElement element = ref.getElement();
                PsiClass containingClass = findContainingClass(element);
                if (containingClass != null) {
                    String className = containingClass.getQualifiedName();
                    if (className != null) {
                        Document document = PsiDocumentManager.getInstance(project).getDocument(element.getContainingFile());
                        if (document != null) {
                            int line = document.getLineNumber(element.getTextRange().getStartOffset()) + 1;
                            callers.add(className + ":" + line);
                        }
                    }
                }
            }
        }
        return callers;
    }

    private static PsiClass findContainingClass(PsiElement element) {
        while (element != null) {
            if (element instanceof PsiClass) {
                return (PsiClass) element;
            }
            element = element.getParent();
        }
        return null;
    }
}
