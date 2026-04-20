package net.mehvahdjukaar.flash;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;

@Service(Service.Level.APP)
public final class FlashPlugin implements Disposable, StartupActivity {

    private static FlashlightHTTPServer currentServer = null;

    public FlashPlugin() {
        if (currentServer == null) {
            try {
                currentServer = new FlashlightHTTPServer();
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
