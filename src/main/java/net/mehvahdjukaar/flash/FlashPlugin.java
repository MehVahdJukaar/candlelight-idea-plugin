package net.mehvahdjukaar.flash;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import org.jetbrains.annotations.NotNull;

@Service(Service.Level.APP)
public final class FlashPlugin implements Disposable, StartupActivity {

    private static MyHttpServer currentServer = null;

    public FlashPlugin() {
        if (currentServer == null) {
            try {
                currentServer = new MyHttpServer();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void dispose() {
        if (currentServer != null) {
            try {
                currentServer.stop();
                currentServer = null;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void runActivity(@NotNull Project project) {
        project.getService(FlashPlugin.class);
    }
}
