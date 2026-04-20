package net.mehvahdjukaar.flash;

import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import java.util.ArrayList;
import java.util.List;

public class SuperclassUtil {
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
